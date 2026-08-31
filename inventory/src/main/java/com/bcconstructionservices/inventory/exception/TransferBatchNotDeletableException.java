package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import lombok.Getter;

/**
 * Thrown when deleting a TransferBatch is attempted while its status isn't
 * DRAFT. Maps to HTTP 422, matching this module's own established
 * convention for a lifecycle-state violation (compare
 * ReceiptProcessingException's "already confirmed", MaterialRequestNotEditableException,
 * TransferBatchNotAwaitingPurchaseException) rather than 409 (reserved here
 * for a mid-operation resource-quantity conflict, e.g. insufficient stock).
 */
@Getter
public class TransferBatchNotDeletableException extends RuntimeException {

    private final Long transferBatchId;
    private final TransferBatchStatus status;

    public TransferBatchNotDeletableException(Long transferBatchId, TransferBatchStatus status) {
        super("Transfer batch " + transferBatchId + " cannot be deleted (status: " + status
                + "); only a DRAFT batch can be deleted, since anything else has either already moved stock "
                + "or is actively referenced by a fulfilling PurchaseReceipt");
        this.transferBatchId = transferBatchId;
        this.status = status;
    }
}
