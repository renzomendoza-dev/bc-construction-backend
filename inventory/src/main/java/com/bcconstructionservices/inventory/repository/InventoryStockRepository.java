package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.InventoryStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
