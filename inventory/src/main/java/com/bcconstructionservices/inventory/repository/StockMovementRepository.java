package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * Filters movement history by optional item, warehouse, and createdAt range
     * (from inclusive, to exclusive). Any null bound is ignored. Item is
     * join-fetched so callers can read itemName without a lazy-load per row.
     */
    @Query("""
            SELECT sm FROM StockMovement sm
            JOIN FETCH sm.item
            WHERE (:itemId IS NULL OR sm.item.id = :itemId)
              AND (:warehouseId IS NULL OR sm.warehouse.id = :warehouseId)
              AND (:from IS NULL OR sm.createdAt >= :from)
              AND (:to IS NULL OR sm.createdAt < :to)
            """)
    Page<StockMovement> search(@Param("itemId") Long itemId,
                                @Param("warehouseId") Long warehouseId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);
}
