package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long> {

    /**
     * Fetches a single receipt with its supplier and warehouse eagerly loaded.
     * Lines are intentionally not join-fetched here (a to-many fetch alongside
     * two to-one fetches risks a cartesian product); accessing getLines() after
     * this still costs one extra query, which is fine for a single receipt.
     */
    @Query("""
            SELECT pr FROM PurchaseReceipt pr
            JOIN FETCH pr.supplier
            JOIN FETCH pr.warehouse
            WHERE pr.id = :id
            """)
    Optional<PurchaseReceipt> findByIdWithSupplierAndWarehouse(@Param("id") Long id);

    /**
     * Filters receipts by optional supplier, purchaseDate range (both bounds
     * inclusive), and fulfillsTransferBatchId — the last one is how a client
     * finds "the receipt resolving batch #Y" from the TransferBatch side,
     * given TransferBatchResponse doesn't embed the reverse reference itself
     * (GET /api/purchase-receipts?fulfillsTransferBatchId=Y).
     */
    @Query("""
            SELECT pr FROM PurchaseReceipt pr
            JOIN FETCH pr.supplier
            JOIN FETCH pr.warehouse
            WHERE (:supplierId IS NULL OR pr.supplier.id = :supplierId)
              AND (:fromDate IS NULL OR pr.purchaseDate >= :fromDate)
              AND (:toDate IS NULL OR pr.purchaseDate <= :toDate)
              AND (:fulfillsTransferBatchId IS NULL OR pr.fulfillsTransferBatchId = :fulfillsTransferBatchId)
            """)
    Page<PurchaseReceipt> search(@Param("supplierId") Long supplierId,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("fulfillsTransferBatchId") Long fulfillsTransferBatchId,
                                  Pageable pageable);
}
