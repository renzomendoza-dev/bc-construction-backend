package com.bcconstructionservices.app;

import com.bcconstructionservices.inventory.dto.StockAdjustmentRequest;
import com.bcconstructionservices.inventory.dto.StockTransferRequest;
import com.bcconstructionservices.inventory.entity.MovementType;
import com.bcconstructionservices.inventory.exception.InsufficientStockException;
import com.bcconstructionservices.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent stock changes through the real InventoryService on Postgres.
 * Before rows were locked, 201 concurrent stock-ins of 1 ended at quantities
 * like 57-97 (lost updates), and concurrent first stock-ins created up to 8
 * duplicate no-location rows, after which the item/warehouse was permanently
 * broken ("Query did not return a unique result").
 *
 * <p>Not @Transactional: every call must commit on its own for the races to
 * be real. Rows created here are deleted afterward, since every context in
 * this module shares one database.
 */
@SpringBootTest(properties = {
        "keycloak.issuer-uri=http://localhost:1/realms/test",
        "keycloak.admin.client-id=test",
        "keycloak.admin.client-secret=test"
})
@ActiveProfiles("dev")
class StockConcurrencyIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> items = new ArrayList<>();
    private final List<Long> warehouses = new ArrayList<>();

    @AfterEach
    void deleteCreatedRows() {
        for (Long item : items) {
            jdbc.update("DELETE FROM stock_movement WHERE item_id = ?", item);
            jdbc.update("DELETE FROM inventory_stock WHERE item_id = ?", item);
            jdbc.update("DELETE FROM item WHERE id = ?", item);
        }
        for (Long warehouse : warehouses) {
            jdbc.update("DELETE FROM warehouse WHERE id = ?", warehouse);
        }
    }

    private long newItem() {
        long id = jdbc.queryForObject("""
                INSERT INTO item (sku, name, category, unit_of_measure, selling_price, default_cost_price,
                                  active, created_at, updated_at)
                VALUES (?, 'Concurrency Test Item', 'Test', 'pc', 10, 8, true, now(), now()) RETURNING id
                """, Long.class, "CONC-" + System.nanoTime());
        items.add(id);
        return id;
    }

    private long newWarehouse() {
        long id = jdbc.queryForObject("""
                INSERT INTO warehouse (code, name, active, type, created_at, updated_at)
                VALUES (?, 'Concurrency Test Warehouse', true, 'MAIN', now(), now()) RETURNING id
                """, Long.class, "CONC-" + System.nanoTime());
        warehouses.add(id);
        return id;
    }

    private void adjust(long item, long warehouse, MovementType type, int quantity) {
        inventoryService.adjustStock(StockAdjustmentRequest.builder()
                .itemId(item).warehouseId(warehouse).locationId(null)
                .quantity(quantity).type(type).reason("concurrency test").build());
    }

    private int quantityOf(long item, long warehouse) {
        return jdbc.queryForObject("SELECT coalesce(sum(quantity), 0) FROM inventory_stock "
                + "WHERE item_id = ? AND warehouse_id = ?", Integer.class, item, warehouse);
    }

    /**
     * Runs callsPerThread calls on each of THREADS threads, all released at
     * once. Each call gets (thread index); returns every exception thrown.
     */
    private List<Throwable> runConcurrently(int callsPerThread, IntConsumer call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            call.accept(thread);
                        } catch (Throwable ex) {
                            failures.add(ex);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentFirstStockInsCreateExactlyOneNoLocationRow() throws Exception {
        for (int round = 0; round < 10; round++) {
            long item = newItem();
            long warehouse = newWarehouse();

            List<Throwable> failures = runConcurrently(1, t -> adjust(item, warehouse, MovementType.IN, 1));

            assertThat(failures).as("round %d", round).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_stock "
                            + "WHERE item_id = ? AND warehouse_id = ? AND location_id IS NULL",
                    Integer.class, item, warehouse)).as("round %d rows", round).isEqualTo(1);
            assertThat(quantityOf(item, warehouse)).as("round %d quantity", round).isEqualTo(THREADS);
        }
    }

    @Test
    void concurrentStockInsToOneRowLoseNoUpdates() throws Exception {
        long item = newItem();
        long warehouse = newWarehouse();
        adjust(item, warehouse, MovementType.IN, 1);

        List<Throwable> failures = runConcurrently(25, t -> adjust(item, warehouse, MovementType.IN, 1));

        assertThat(failures).isEmpty();
        assertThat(quantityOf(item, warehouse)).isEqualTo(1 + THREADS * 25);
    }

    @Test
    void concurrentWithdrawalsNeverTakeMoreThanIsAvailable() throws Exception {
        long item = newItem();
        long warehouse = newWarehouse();
        adjust(item, warehouse, MovementType.IN, 50);

        // 80 attempts to take 1 unit from 50: exactly 50 may succeed.
        List<Throwable> failures = runConcurrently(10, t -> adjust(item, warehouse, MovementType.OUT, 1));

        assertThat(failures).hasSize(THREADS * 10 - 50).allMatch(InsufficientStockException.class::isInstance);
        assertThat(quantityOf(item, warehouse)).isZero();
    }

    @Test
    void oppositeDirectionTransfersNeitherDeadlockNorLoseStock() throws Exception {
        long item = newItem();
        long warehouseA = newWarehouse();
        long warehouseB = newWarehouse();
        adjust(item, warehouseA, MovementType.IN, 1000);
        adjust(item, warehouseB, MovementType.IN, 1000);

        // Half the threads move A->B, half B->A, through both transfer paths,
        // so every lock ordering between the two warehouses is exercised.
        List<Throwable> failures = runConcurrently(10, t -> {
            boolean aToB = t % 2 == 0;
            long from = aToB ? warehouseA : warehouseB;
            long to = aToB ? warehouseB : warehouseA;
            if (t % 4 < 2) {
                inventoryService.transferStock(StockTransferRequest.builder()
                        .itemId(item).fromWarehouseId(from).toWarehouseId(to).quantity(3).build());
            } else {
                inventoryService.transferWarehouseStock(item, from, to, 3);
            }
        });

        assertThat(failures).isEmpty();
        assertThat(quantityOf(item, warehouseA) + quantityOf(item, warehouseB)).isEqualTo(2000);
        // A->B and B->A ran the same number of times, so each side nets to zero.
        assertThat(quantityOf(item, warehouseA)).isEqualTo(1000);
    }
}
