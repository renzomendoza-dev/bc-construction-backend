package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /**
     * Fetches a single order with its supplier eagerly loaded. Lines are
     * intentionally not join-fetched here, same reasoning as
     * TransferBatchRepository.findByIdWithWarehouses.
     */
    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN FETCH po.supplier
            WHERE po.id = :id
            """)
    Optional<PurchaseOrder> findByIdWithSupplier(@Param("id") Long id);

    /**
     * Filters orders by optional supplier and status.
     */
    @Query("""
            SELECT po FROM PurchaseOrder po
            JOIN FETCH po.supplier
            WHERE (:supplierId IS NULL OR po.supplier.id = :supplierId)
              AND (:status IS NULL OR po.status = :status)
            """)
    Page<PurchaseOrder> search(@Param("supplierId") Long supplierId,
                                @Param("status") PurchaseOrderStatus status,
                                Pageable pageable);
}
