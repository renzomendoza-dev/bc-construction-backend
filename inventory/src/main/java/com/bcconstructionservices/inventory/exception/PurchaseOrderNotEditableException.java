package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import lombok.Getter;

/**
 * Thrown when updating or submitting a PurchaseOrder is attempted while its
 * status isn't DRAFT — both share the same precondition (line items are only
 * ever mutable, and only ever transition to SUBMITTED, while still DRAFT).
 * Maps to HTTP 422, matching this module's established convention for a
 * lifecycle-state violation (compare MaterialRequestNotEditableException,
 * TransferBatchNotAwaitingPurchaseException).
 */
@Getter
public class PurchaseOrderNotEditableException extends RuntimeException {

    private final Long purchaseOrderId;
    private final PurchaseOrderStatus status;

    public PurchaseOrderNotEditableException(Long purchaseOrderId, PurchaseOrderStatus status) {
        super("Purchase order " + purchaseOrderId + " can no longer be edited or submitted (status: " + status
                + "); only a DRAFT order can be changed or submitted");
        this.purchaseOrderId = purchaseOrderId;
        this.status = status;
    }
}
