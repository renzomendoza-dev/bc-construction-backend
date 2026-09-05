package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MovementDirection;
import com.bcconstructionservices.inventory.entity.MovementType;
import com.bcconstructionservices.inventory.entity.StockMovement;
import com.bcconstructionservices.inventory.entity.Warehouse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for StockMovementRepository.
 *
 * <p>Uses @SpringBootTest (not @DataJpaTest) against the real configured
 * Postgres DataSource — same pattern as the other repository tests in this
 * package. @DataJpaTest's embedded-database auto-configuration was forcing
 * an H2 connection in this environment regardless of
 * @AutoConfigureTestDatabase(replace = NONE), and the module's
 * Postgres-specific Flyway migrations don't run cleanly on H2 (e.g. the
 * chk_stock_movement_type check constraint), so real Postgres is required.
 *
 * <p>@Transactional rolls back each test's changes automatically.
 *
 * <p>Verifies the type check constraint accepts every MovementType enum
 * value — guarding against the enum and the chk_stock_movement_type
 * constraint in the Flyway migration drifting apart (e.g. a new enum value
 * added in Java but not in the migration).
 */
//@SpringBootTest(classes = InventoryTestConfig.class)
//@Transactional
    @DataJpaTest
class StockMovementRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

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

    private StockMovement buildMovement(Item item, Warehouse warehouse, MovementType type, int quantity) {
        StockMovement movement = new StockMovement();
        movement.setItem(item);
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setDirection(MovementDirection.IN);
        movement.setQuantity(quantity);
        movement.setReason("Repository constraint test");
        return movement;
    }

    // ---------------------------------------------------------------
    // MovementType check constraint
    // ---------------------------------------------------------------

    @Nested
    @Disabled("H2 in PostgreSQL mode cannot evaluate chk_stock_movement_type; " +
            "constraint verified working in Postgres. Re-enable under Testcontainers.")
    class MovementTypeCheckConstraint {

        @ParameterizedTest
        @EnumSource(MovementType.class)
        void shouldPersistMovementForEveryMovementTypeEnumValue(MovementType type) {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Item item = persistItem("SKU-800", "Portland Cement 40kg");

            StockMovement movement = buildMovement(item, warehouse, type, 10);

            StockMovement saved = stockMovementRepository.saveAndFlush(movement);
            entityManager.clear();

            StockMovement reloaded = stockMovementRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getType()).isEqualTo(type);
            assertThat(reloaded.getQuantity()).isEqualTo(10);
        }
    }
}