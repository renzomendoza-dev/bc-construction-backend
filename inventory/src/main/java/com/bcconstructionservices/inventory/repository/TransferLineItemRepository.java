package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.TransferLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransferLineItemRepository extends JpaRepository<TransferLineItem, Long> {

    List<TransferLineItem> findByTransferBatchId(Long transferBatchId);

    /**
     * Every line from a COMPLETED batch fulfilling this material request —
     * used to decide the request's status from everything transferred against
     * it so far. A batch that isn't COMPLETED hasn't moved any stock yet.
     */
    @Query("""
            SELECT tli FROM TransferLineItem tli
            JOIN tli.transferBatch tb
            WHERE tb.sourceMaterialRequestId = :materialRequestId
              AND tb.status = com.bcconstructionservices.inventory.entity.TransferBatchStatus.COMPLETED
            """)
    List<TransferLineItem> findCompletedByMaterialRequestId(@Param("materialRequestId") Long materialRequestId);
}
