package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import lombok.Getter;

/**
 * Thrown when a PurchaseReceipt is created with fulfillsTransferBatchId set
 * to a batch that isn't currently AWAITING_PURCHASE — a receipt can only be
 * linked to a batch that has actually failed on insufficient stock. Maps to
 * HTTP 422, matching this codebase's precedent for a lifecycle-state
 * violation (compare ReceiptProcessingException, MaterialRequestNotEditableException)
 * rather than 409 (used for a mid-operation stock conflict) or 400 (request
 * shape validation).
 */
@Getter
public class TransferBatchNotAwaitingPurchaseException extends RuntimeException {

    private final Long transferBatchId;
    private final TransferBatchStatus status;

    public TransferBatchNotAwaitingPurchaseException(Long transferBatchId, TransferBatchStatus status) {
        super("Transfer batch " + transferBatchId + " is not awaiting purchase (status: " + status
                + "); a purchase receipt can only be linked to a batch that failed on insufficient stock");
        this.transferBatchId = transferBatchId;
        this.status = status;
    }
}
