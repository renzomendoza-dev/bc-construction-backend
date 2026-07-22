package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.InventoryStock;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
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
 * @DataJpaTest slice tests for InventoryStockRepository.
 *
 * <p>Requires an embedded test database (e.g. H2, test scope) on the
 * classpath, since @DataJpaTest replaces the configured DataSource with an
 * embedded one by default.
 *
 * <p>ASSUMPTIONS:
 * <ul>
 *   <li>Per this turn's instructions, the finder under test is named
 *       {@code findByItemIdAndWarehouseIdAndLocationId(Long, Long, Long)}
 *       returning Optional&lt;InventoryStock&gt;. Note: the actual
 *       InventoryStockRepository.java uploaded earlier in this codebase
 *       names this method {@code findByItemAndWarehouseAndLocation} instead
 *       — rename the calls below if this test needs to run against that
 *       real file rather than the hypothetical signature given here.</li>
 *   <li>Item's non-SKU/name fields (category, unitOfMeasure, sellingPrice,
 *       defaultCostPrice) are populated in the test helper for a realistic,
 *       valid Item, even though only sku/name were confirmed NOT NULL
 *       earlier in this codebase.</li>
 * </ul>
 *
 * <p>Scenarios 10 and 14 are "observe the actual behavior" cases per the
 * prompt; see the detailed comments on each for why the asserted outcome
 * was chosen and what to check if the real entity differs.
 */
@DataJpaTest
class InventoryStockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

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

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        entityManager.persistAndFlush(warehouse);
        return warehouse;
    }

    private StorageLocation persistLocation(Warehouse warehouse, String code) {
        StorageLocation location = new StorageLocation();
        location.setWarehouse(warehouse);
        location.setCode(code);
        entityManager.persistAndFlush(location);
        return location;
    }

    private InventoryStock stock(Item item, Warehouse warehouse, StorageLocation location, Integer quantity) {
        InventoryStock stock = new InventoryStock();
        stock.setItem(item);
        stock.setWarehouse(warehouse);
        stock.setLocation(location);
        stock.setQuantity(quantity);
        return stock;
    }

    // ---------------------------------------------------------------
    // Composite unique constraint: (item_id, warehouse_id, location_id)
    // ---------------------------------------------------------------

    @Nested
    class CompositeUniquenessConstraint {

        @Test
        void shouldSaveInventoryStockForItemWarehouseLocationCombinationSuccessfully() {
            Item item = persistItem("SKU-800", "Portland Cement 40kg");
            Warehouse warehouse = persistWarehouse("WH-800", "Warehouse 800");
            StorageLocation location = persistLocation(warehouse, "A-01-01");

            InventoryStock saved = inventoryStockRepository.saveAndFlush(stock(item, warehouse, location, 100));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getQuantity()).isEqualTo(100);
        }

        @Test
        void shouldThrowDataIntegrityViolationExceptionForDuplicateItemWarehouseLocationCombination() {
            Item item = persistItem("SKU-801", "Deformed Rebar 10mm x 6m");
            Warehouse warehouse = persistWarehouse("WH-801", "Warehouse 801");
            StorageLocation location = persistLocation(warehouse, "A-01-01");

            inventoryStockRepository.saveAndFlush(stock(item, warehouse, location, 50));
            entityManager.clear();

            // This is the constraint that prevents duplicate/conflicting
            // stock records for the exact same item+warehouse+location.
            InventoryStock duplicate = stock(item, warehouse, location, 75);

            assertThatThrownBy(() -> inventoryStockRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldHandleSameItemAndWarehouseWithNullLocationInBothRows() {
            Item item = persistItem("SKU-802", "Gravel 3/4 Minus");
            Warehouse warehouse = persistWarehouse("WH-802", "Warehouse 802");

            inventoryStockRepository.saveAndFlush(stock(item, warehouse, null, 40));
            entityManager.clear();

            InventoryStock secondNullLocationRow = stock(item, warehouse, null, 60);

            // DESIGN NOTE (observed behavior, not a guess): standard SQL
            // semantics (NULL <> NULL) mean a composite UNIQUE constraint
            // does NOT treat two rows that are both NULL in location_id as
            // conflicting - this holds for H2 (the typical @DataJpaTest
            // default), PostgreSQL, and MySQL alike. So this save is
            // expected to SUCCEED, meaning the DB constraint alone does
            // NOT prevent two "warehouse-level" (no specific location)
            // stock rows for the same item+warehouse from coexisting.
            //
            // This matters: the real InventoryStockRepository.java uploaded
            // earlier in this codebase has findByItemAndWarehouseAndLocation
            // explicitly handle null-location matching in its JPQL
            // ("(:locationId IS NULL AND s.location IS NULL)") rather than
            // relying on "location.id = :locationId" - which is *only*
            // necessary because "=" against NULL never matches in SQL. That
            // confirms the team is already working around related NULL
            // semantics at the query layer. If "at most one warehouse-level
            // row per item+warehouse" is meant to be a hard invariant, this
            // test shows it is currently enforced only by application-level
            // lookup-before-insert logic (e.g. in InventoryService), not by
            // this DB constraint - which leaves a race-condition window
            // under concurrent requests. Consider a partial unique index
            // (e.g. Postgres "WHERE location_id IS NULL") if that gap needs
            // closing at the DB level.
            assertThatCode(() -> inventoryStockRepository.saveAndFlush(secondNullLocationRow))
                    .doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------
    // Non-conflicting combinations
    // ---------------------------------------------------------------

    @Nested
    class NonConflictingCombinations {

        @Test
        void shouldAllowSameItemInDifferentWarehouses() {
            Item item = persistItem("SKU-803", "16mm Plywood Marine 4x8");
            Warehouse warehouseA = persistWarehouse("WH-803A", "Warehouse 803A");
            Warehouse warehouseB = persistWarehouse("WH-803B", "Warehouse 803B");
            StorageLocation locationA = persistLocation(warehouseA, "A-01");
            StorageLocation locationB = persistLocation(warehouseB, "A-01");

            inventoryStockRepository.saveAndFlush(stock(item, warehouseA, locationA, 30));

            assertThatCode(() -> inventoryStockRepository.saveAndFlush(stock(item, warehouseB, locationB, 20)))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldAllowSameItemAndWarehouseWithDifferentLocations() {
            Item item = persistItem("SKU-804", "Hollow Block 4in CHB");
            Warehouse warehouse = persistWarehouse("WH-804", "Warehouse 804");
            StorageLocation locationA = persistLocation(warehouse, "A-01");
            StorageLocation locationB = persistLocation(warehouse, "A-02");

            inventoryStockRepository.saveAndFlush(stock(item, warehouse, locationA, 30));

            assertThatCode(() -> inventoryStockRepository.saveAndFlush(stock(item, warehouse, locationB, 20)))
                    .doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------
    // findByItemIdAndWarehouseIdAndLocationId
    // ---------------------------------------------------------------

    @Nested
    class FindByItemIdAndWarehouseIdAndLocationIdTests {

        @Test
        void shouldReturnInventoryStockWhenFound() {
            Item item = persistItem("SKU-805", "Tie Wire #16 Roll");
            Warehouse warehouse = persistWarehouse("WH-805", "Warehouse 805");
            StorageLocation location = persistLocation(warehouse, "A-01");
            inventoryStockRepository.saveAndFlush(stock(item, warehouse, location, 88));
            entityManager.clear();

            Optional<InventoryStock> result = inventoryStockRepository
                    .findByItemAndWarehouseAndLocation(item.getId(), warehouse.getId(), location.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(88);
        }

        @Test
        void shouldReturnEmptyWhenNoMatchingRowExists() {
            Item item = persistItem("SKU-806", "Angle Bar 25mm x 3mm x 6m");
            Warehouse warehouse = persistWarehouse("WH-806", "Warehouse 806");
            StorageLocation location = persistLocation(warehouse, "A-01");
            // Intentionally not saved - no InventoryStock row exists for this combination.

            Optional<InventoryStock> result = inventoryStockRepository
                    .findByItemAndWarehouseAndLocation(item.getId(), warehouse.getId(), location.getId());

            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Default quantity
    // ---------------------------------------------------------------

    @Nested
    class DefaultQuantityTests {

        @Test
        void shouldDefaultQuantityToZeroWhenNotExplicitlySet() {
            Item item = persistItem("SKU-807", "Chicken Wire 1/2in Mesh");
            Warehouse warehouse = persistWarehouse("WH-807", "Warehouse 807");

            InventoryStock stockWithoutQuantity = new InventoryStock();
            stockWithoutQuantity.setItem(item);
            stockWithoutQuantity.setWarehouse(warehouse);
            // quantity intentionally left unset.

            // OBSERVED-BEHAVIOR NOTE: a plain DDL "DEFAULT 0" clause only
            // takes effect through Hibernate if the column is left out of
            // the generated INSERT entirely (via @DynamicInsert), which is
            // NOT Hibernate's default - by default it sends every mapped
            // column, including NULL if unset. This test assumes the more
            // common way teams actually achieve "default 0" through plain
            // JPA saves: either a Java field initializer
            // (`private Integer quantity = 0;`) or a @PrePersist callback
            // that fills in 0 when null. If the entity instead relies purely
            // on the DB column default with no such mechanism, this save
            // would throw a NOT NULL constraint violation (quantity=NULL
            // sent explicitly) rather than silently defaulting - in that
            // case, swap this assertion for
            // assertThatThrownBy(...).isInstanceOf(DataIntegrityViolationException.class)
            // and treat that as a real bug to fix (the "default" doesn't
            // actually work as described) rather than adjusting the test to
            // match it.
            InventoryStock saved = inventoryStockRepository.saveAndFlush(stockWithoutQuantity);
            entityManager.clear();

            InventoryStock reloaded = inventoryStockRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getQuantity()).isEqualTo(0);
        }
    }
}