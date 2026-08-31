package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransferBatchRepository extends JpaRepository<TransferBatch, Long> {

    /**
     * Fetches a single batch with its origin/destination warehouses eagerly
     * loaded. Line items are intentionally not join-fetched here (a to-many
     * fetch alongside two to-one fetches risks a cartesian product); accessing
     * getLineItems() after this still costs one extra query, which is fine
     * for a single batch.
     */
    @Query("""
            SELECT tb FROM TransferBatch tb
            JOIN FETCH tb.originWarehouse
            JOIN FETCH tb.destinationWarehouse
            WHERE tb.id = :id
            """)
    Optional<TransferBatch> findByIdWithWarehouses(@Param("id") Long id);

    /**
     * Filters batches by optional origin warehouse, destination warehouse, and status.
     */
    @Query("""
            SELECT tb FROM TransferBatch tb
            JOIN FETCH tb.originWarehouse
            JOIN FETCH tb.destinationWarehouse
            WHERE (:originWarehouseId IS NULL OR tb.originWarehouse.id = :originWarehouseId)
              AND (:destinationWarehouseId IS NULL OR tb.destinationWarehouse.id = :destinationWarehouseId)
              AND (:status IS NULL OR tb.status = :status)
            """)
    Page<TransferBatch> search(@Param("originWarehouseId") Long originWarehouseId,
                                @Param("destinationWarehouseId") Long destinationWarehouseId,
                                @Param("status") TransferBatchStatus status,
                                Pageable pageable);
}
