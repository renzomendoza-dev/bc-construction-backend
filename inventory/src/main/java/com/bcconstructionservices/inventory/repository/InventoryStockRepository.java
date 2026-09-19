package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.InventoryStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    /**
     * Looks up the single InventoryStock row for an exact item+warehouse+location
     * combination. locationId may be null (untracked / no specific location),
     * which is matched explicitly since SQL NULL never equals NULL via '='.
     */
    @Query("""
            SELECT s FROM InventoryStock s
            JOIN FETCH s.item
            JOIN FETCH s.warehouse
            LEFT JOIN FETCH s.location
            WHERE s.item.id = :itemId
              AND s.warehouse.id = :warehouseId
              AND ((:locationId IS NULL AND s.location IS NULL) OR s.location.id = :locationId)
            """)
    Optional<InventoryStock> findByItemAndWarehouseAndLocation(
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId,
            @Param("locationId") Long locationId);

    /**
     * Every stock row for an item across every location in a warehouse — the
     * no-location bucket included. Used by
     * InventoryService.transferWarehouseStock to sum/debit a TransferBatch
     * line's warehouse-total balance rather than one specific location (a
     * batch line has no locationId to narrow by in the first place).
     */
    @Query("""
            SELECT s FROM InventoryStock s
            JOIN FETCH s.item
            JOIN FETCH s.warehouse
            LEFT JOIN FETCH s.location
            WHERE s.item.id = :itemId
              AND s.warehouse.id = :warehouseId
            """)
    List<InventoryStock> findAllByItemAndWarehouse(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

    // --- Locking variants: every quantity change reads through these --------
    //
    // SELECT ... FOR UPDATE, so a concurrent change to the same row waits for
    // this transaction to finish instead of both reading the same starting
    // quantity and one overwriting the other. No fetch joins: Postgres can't
    // lock the nullable side of an outer join, and callers only need the row.
    // Must be the FIRST load of the row in the transaction — Hibernate doesn't
    // refresh an entity it already holds, so a stale earlier copy would win.

    /** Locked single-row lookup; locationId null means the no-location bucket. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM InventoryStock s
            WHERE s.item.id = :itemId
              AND s.warehouse.id = :warehouseId
              AND ((CAST(:locationId AS Long) IS NULL AND s.location IS NULL) OR s.location.id = :locationId)
            """)
    Optional<InventoryStock> findForUpdate(@Param("itemId") Long itemId,
                                           @Param("warehouseId") Long warehouseId,
                                           @Param("locationId") Long locationId);

    /**
     * Every stock row for an item in a warehouse, locked, in the order they're
     * drained by InventoryService.transferWarehouseStock (and locked): the
     * no-location bucket first, then each location by id.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM InventoryStock s
            WHERE s.item.id = :itemId
              AND s.warehouse.id = :warehouseId
            ORDER BY s.location.id NULLS FIRST
            """)
    List<InventoryStock> findAllByItemAndWarehouseForUpdate(@Param("itemId") Long itemId,
                                                            @Param("warehouseId") Long warehouseId);

    /**
     * Creates the stock row at quantity 0 unless it already exists,
     * atomically — two concurrent first stock-ins can't both insert: the
     * second waits for the first to commit, then does nothing. Relies on V34's
     * NULLS NOT DISTINCT constraint so the no-location bucket is covered too.
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_stock (item_id, warehouse_id, location_id, quantity, updated_at)
            VALUES (:itemId, :warehouseId, :locationId, 0, now())
            ON CONFLICT ON CONSTRAINT uq_inventory_stock_item_warehouse_location DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("itemId") Long itemId,
                       @Param("warehouseId") Long warehouseId,
                       @Param("locationId") Long locationId);

    /**
     * Filters stock rows by optional item and/or warehouse; either or both may be null.
     */
    @Query("""
            SELECT s FROM InventoryStock s
            JOIN FETCH s.item
            JOIN FETCH s.warehouse
            LEFT JOIN FETCH s.location
            WHERE (:itemId IS NULL OR s.item.id = :itemId)
              AND (:warehouseId IS NULL OR s.warehouse.id = :warehouseId)
            """)
    Page<InventoryStock> search(@Param("itemId") Long itemId,
                                 @Param("warehouseId") Long warehouseId,
                                 Pageable pageable);

    @Query("""
            SELECT s FROM InventoryStock s
            JOIN FETCH s.item
            JOIN FETCH s.warehouse
            WHERE s.reorderThreshold IS NOT NULL AND s.quantity <= s.reorderThreshold
            """)
    List<InventoryStock> findLowStock();
}
