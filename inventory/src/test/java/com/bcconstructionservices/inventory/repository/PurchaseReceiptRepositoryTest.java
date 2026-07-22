package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.InventoryTestConfig;
import com.bcconstructionservices.inventory.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for PurchaseReceiptRepository, plus the
 * cascade/orphanRemoval behavior of PurchaseReceipt.lines
 * (PurchaseReceiptLineRepository) since that relationship is owned by
 * PurchaseReceipt and can't meaningfully be tested in isolation.
 *
 * <p>Uses @SpringBootTest (not @DataJpaTest) against the real configured
 * Postgres DataSource. @DataJpaTest's embedded-database auto-configuration
 * (TestDatabaseAutoConfiguration) was forcing an H2 connection in this
 * environment even with @AutoConfigureTestDatabase(replace = NONE),
 * @TestPropertySource overrides, and excludeAutoConfiguration all attempted
 * — none changed the outcome, so @SpringBootTest is used instead to
 * sidestep that mechanism entirely and connect via the real Postgres
 * schema built by Flyway.
 *
 * <p>@Transactional rolls back each test's changes automatically, giving
 * the same test-isolation behavior @DataJpaTest would have provided.
 *
 * <p>PurchaseReceipt.warehouse is @NotNull, so buildReceipt(...) always
 * attaches a persisted Warehouse — otherwise every save fails with a
 * ConstraintViolationException on that field regardless of what's actually
 * under test.
 *
 * <p>ASSUMPTION: the "supplier is required" constraint violation (scenario
 * 4) could surface as either DataIntegrityViolationException (DB-level NOT
 * NULL, translated by Spring) or jakarta.validation.ConstraintViolationException
 * (Bean Validation, if @NotNull + hibernate-validator intercept first) —
 * both are accepted since the CONTEXT didn't specify which layer enforces
 * it.
 */
//@SpringBootTest(classes = InventoryTestConfig.class)
//@Transactional
    @DataJpaTest
class PurchaseReceiptRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PurchaseReceiptRepository purchaseReceiptRepository;

    @Autowired
    private PurchaseReceiptLineRepository purchaseReceiptLineRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private Supplier persistSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContactInfo("orders@example.com");
        supplier.setActive(true);
        entityManager.persist(supplier);
        entityManager.flush();
        return supplier;
    }

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        entityManager.persist(warehouse);
        entityManager.flush();
        return warehouse;
    }

    private Item persistItem(String sku, String name) {
        Item item = new Item();
        item.setSku(sku);
        item.setName(name);
        item.setCategory("Cement");
        item.setUnitOfMeasure("bag");
        item.setSellingPrice(new BigDecimal("289.50"));
        item.setDefaultCostPrice(new BigDecimal("245.00"));
        item.setActive(true);
        item.setImages(new ArrayList<>());
        entityManager.persist(item);
        entityManager.flush();
        return item;
    }

    private PurchaseReceipt buildReceipt(Supplier supplier, Warehouse warehouse, String receiptNumber,
                                         LocalDate purchaseDate) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setPurchaseDate(purchaseDate);
        receipt.setTotalAmount(BigDecimal.ZERO);
        receipt.setLines(new ArrayList<>());
        return receipt;
    }

    private PurchaseReceiptLine buildLine(PurchaseReceipt receipt, Item item, int quantity, String unitCost) {
        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setPurchaseReceipt(receipt);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal(unitCost));
        line.setLineTotal(new BigDecimal(unitCost).multiply(BigDecimal.valueOf(quantity)));
        return line;
    }

    // ---------------------------------------------------------------
    // Cascade save / delete / orphanRemoval
    // ---------------------------------------------------------------

    @Nested
    class LineCascadeBehavior {

        @Test
        void shouldCascadeSaveMultipleLinesWithCorrectReceiptForeignKey() {
            Supplier supplier = persistSupplier("Luzon Steel Trading");
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Item cement = persistItem("SKU-900", "Portland Cement 40kg");
            Item rebar = persistItem("SKU-901", "Deformed Rebar 10mm x 6m");

            PurchaseReceipt receipt = buildReceipt(supplier, warehouse, "OR-2026-004512", LocalDate.of(2026, 7, 10));
            PurchaseReceiptLine lineA = buildLine(receipt, cement, 50, "245.00");
            PurchaseReceiptLine lineB = buildLine(receipt, rebar, 8, "158.75");
            receipt.getLines().add(lineA);
            receipt.getLines().add(lineB);

            PurchaseReceipt saved = purchaseReceiptRepository.saveAndFlush(receipt);
            Long receiptId = saved.getId();
            entityManager.clear();

            // Verify via the parent association...
            PurchaseReceipt reloaded = purchaseReceiptRepository.findById(receiptId).orElseThrow();
            assertThat(reloaded.getLines()).hasSize(2);

            // ...and independently via PurchaseReceiptLineRepository, to
            // confirm the purchase_receipt_id foreign key was actually
            // persisted on the child rows.
            List<PurchaseReceiptLine> linesForThisReceipt = purchaseReceiptLineRepository.findAll().stream()
                    .filter(line -> line.getPurchaseReceipt() != null
                            && receiptId.equals(line.getPurchaseReceipt().getId()))
                    .toList();
            assertThat(linesForThisReceipt).hasSize(2);
            assertThat(linesForThisReceipt)
                    .extracting(PurchaseReceiptLine::getQuantity)
                    .containsExactlyInAnyOrder(50, 8);
        }

        @Test
        void shouldDeleteAssociatedLinesWhenReceiptIsDeleted() {
            Supplier supplier = persistSupplier("Bulacan Hardware Supply");
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Item item = persistItem("SKU-902", "Gravel 3/4 Minus");

            PurchaseReceipt receipt = buildReceipt(supplier, warehouse, "OR-2026-004600", LocalDate.of(2026, 7, 11));
            PurchaseReceiptLine line = buildLine(receipt, item, 15, "99.00");
            receipt.getLines().add(line);

            PurchaseReceipt saved = purchaseReceiptRepository.saveAndFlush(receipt);
            Long receiptId = saved.getId();
            Long lineId = saved.getLines().get(0).getId();
            entityManager.clear();

            assertThat(purchaseReceiptLineRepository.findById(lineId)).isPresent();

            purchaseReceiptRepository.deleteById(receiptId);
            entityManager.flush();
            entityManager.clear();

            assertThat(purchaseReceiptRepository.findById(receiptId)).isEmpty();
            // No orphaned PurchaseReceiptLine row left behind.
            assertThat(purchaseReceiptLineRepository.findById(lineId)).isEmpty();
        }

        @Test
        void shouldDeleteOnlyTheRemovedLineWhenOneLineIsRemovedFromTheList() {
            Supplier supplier = persistSupplier("Luzon Steel Trading");
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Item cement = persistItem("SKU-903", "Portland Cement 40kg");
            Item rebar = persistItem("SKU-904", "Deformed Rebar 10mm x 6m");

            PurchaseReceipt receipt = buildReceipt(supplier, warehouse, "OR-2026-004700", LocalDate.of(2026, 7, 12));
            PurchaseReceiptLine keepLine = buildLine(receipt, cement, 50, "245.00");
            PurchaseReceiptLine removeLine = buildLine(receipt, rebar, 8, "158.75");
            receipt.getLines().add(keepLine);
            receipt.getLines().add(removeLine);

            PurchaseReceipt saved = purchaseReceiptRepository.saveAndFlush(receipt);
            Long receiptId = saved.getId();
            Long keepLineId = saved.getLines().get(0).getId();
            Long removeLineId = saved.getLines().get(1).getId();
            entityManager.clear();

            PurchaseReceipt reloaded = purchaseReceiptRepository.findById(receiptId).orElseThrow();
            // Mutate the managed collection in place so Hibernate's
            // orphanRemoval diffing (against its load-time snapshot) picks
            // up the removal correctly.
            reloaded.getLines().removeIf(line -> line.getId().equals(removeLineId));
            entityManager.flush();
            entityManager.clear();

            assertThat(purchaseReceiptLineRepository.findById(removeLineId)).isEmpty();
            assertThat(purchaseReceiptLineRepository.findById(keepLineId)).isPresent();

            PurchaseReceipt refetched = purchaseReceiptRepository.findById(receiptId).orElseThrow();
            assertThat(refetched.getLines()).hasSize(1);
            assertThat(refetched.getLines().get(0).getId()).isEqualTo(keepLineId);
        }
    }

    // ---------------------------------------------------------------
    // Required supplier
    // ---------------------------------------------------------------

    @Nested
    class RequiredSupplierConstraint {

        @Test
        void shouldThrowConstraintViolationWhenSupplierIsMissing() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");

            PurchaseReceipt receipt = new PurchaseReceipt();
            receipt.setWarehouse(warehouse);
            receipt.setReceiptNumber("OR-2026-004999");
            receipt.setPurchaseDate(LocalDate.of(2026, 7, 15));
            receipt.setTotalAmount(BigDecimal.ZERO);
            receipt.setLines(new ArrayList<>());
            // supplier intentionally left unset - warehouse IS set, so this
            // isolates the assertion to the supplier constraint specifically.

            assertThatThrownBy(() -> purchaseReceiptRepository.saveAndFlush(receipt))
                    .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
        }
    }

    // ---------------------------------------------------------------
    // Required warehouse
    // ---------------------------------------------------------------

    @Nested
    class RequiredWarehouseConstraint {

        @Test
        void shouldThrowConstraintViolationWhenWarehouseIsMissing() {
            Supplier supplier = persistSupplier("Luzon Steel Trading");

            PurchaseReceipt receipt = new PurchaseReceipt();
            receipt.setSupplier(supplier);
            receipt.setReceiptNumber("OR-2026-005000");
            receipt.setPurchaseDate(LocalDate.of(2026, 7, 16));
            receipt.setTotalAmount(BigDecimal.ZERO);
            receipt.setLines(new ArrayList<>());
            // warehouse intentionally left unset - supplier IS set, so this
            // isolates the assertion to the warehouse constraint specifically.

            assertThatThrownBy(() -> purchaseReceiptRepository.saveAndFlush(receipt))
                    .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
        }
    }
}