package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemSupplier;
import com.bcconstructionservices.inventory.entity.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest slice tests for ItemSupplierRepository.
 *
 * <p>Requires an embedded test database (e.g. H2, test scope) on the
 * classpath, since @DataJpaTest replaces the configured DataSource with an
 * embedded one by default.
 *
 * <p>This turn confirms the same
 * {@code findByItemIdAndSupplierId(Long itemId, Long supplierId)} signature
 * that was assumed (unconfirmed at the time) for ItemSupplierRepository in
 * PurchaseReceiptServiceConfirmTest earlier in this codebase — good
 * alignment, no change needed there.
 */
@DataJpaTest
class ItemSupplierRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSupplierRepository itemSupplierRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

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
        entityManager.persistAndFlush(item);
        return item;
    }

    private Supplier persistSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContactInfo("orders@example.com");
        supplier.setActive(true);
        entityManager.persistAndFlush(supplier);
        return supplier;
    }

    private ItemSupplier buildItemSupplier(Item item, Supplier supplier, String unitCost) {
        ItemSupplier itemSupplier = new ItemSupplier();
        itemSupplier.setItem(item);
        itemSupplier.setSupplier(supplier);
        itemSupplier.setSupplierSku("SUP-SKU-001");
        itemSupplier.setUnitCost(new BigDecimal(unitCost));
        return itemSupplier;
    }

    // ---------------------------------------------------------------
    // (item_id, supplier_id) uniqueness
    // ---------------------------------------------------------------

    @Nested
    class ItemSupplierUniquenessConstraint {

        @Test
        void shouldSaveItemSupplierForItemAndSupplierPairSuccessfully() {
            Item item = persistItem("SKU-960", "Portland Cement 40kg");
            Supplier supplier = persistSupplier("Luzon Steel Trading");

            ItemSupplier saved = itemSupplierRepository.saveAndFlush(buildItemSupplier(item, supplier, "238.25"));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUnitCost()).isEqualByComparingTo(new BigDecimal("238.25"));
        }

        @Test
        void shouldThrowDataIntegrityViolationExceptionForDuplicateItemSupplierPair() {
            Item item = persistItem("SKU-961", "Deformed Rebar 10mm x 6m");
            Supplier supplier = persistSupplier("Bulacan Hardware Supply");

            itemSupplierRepository.saveAndFlush(buildItemSupplier(item, supplier, "158.75"));
            entityManager.clear();

            ItemSupplier duplicate = buildItemSupplier(item, supplier, "162.00");

            assertThatThrownBy(() -> itemSupplierRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAllowSameItemWithDifferentSupplier() {
            Item item = persistItem("SKU-962", "Gravel 3/4 Minus");
            Supplier supplierA = persistSupplier("Luzon Steel Trading");
            Supplier supplierB = persistSupplier("Bulacan Hardware Supply");

            itemSupplierRepository.saveAndFlush(buildItemSupplier(item, supplierA, "99.00"));

            assertThatCode(() -> itemSupplierRepository.saveAndFlush(buildItemSupplier(item, supplierB, "102.50")))
                    .doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------
    // findByItemIdAndSupplierId
    // ---------------------------------------------------------------

    @Nested
    class FindByItemIdAndSupplierIdTests {

        @Test
        void shouldReturnItemSupplierWhenFound() {
            Item item = persistItem("SKU-963", "16mm Plywood Marine 4x8");
            Supplier supplier = persistSupplier("Luzon Steel Trading");
            itemSupplierRepository.saveAndFlush(buildItemSupplier(item, supplier, "875.00"));
            entityManager.clear();

            Optional<ItemSupplier> result =
                    itemSupplierRepository.findByItemIdAndSupplierId(item.getId(), supplier.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getUnitCost()).isEqualByComparingTo(new BigDecimal("875.00"));
        }

        @Test
        void shouldReturnEmptyWhenNoMatchingRowExists() {
            Item item = persistItem("SKU-964", "Hollow Block 4in CHB");
            Supplier supplier = persistSupplier("Bulacan Hardware Supply");
            // Intentionally not saved - no ItemSupplier row exists for this pair.

            Optional<ItemSupplier> result =
                    itemSupplierRepository.findByItemIdAndSupplierId(item.getId(), supplier.getId());

            assertThat(result).isEmpty();
        }
    }
}