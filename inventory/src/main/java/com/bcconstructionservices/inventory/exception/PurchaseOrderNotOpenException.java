package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import lombok.Getter;

/**
 * Thrown when an action requires a PurchaseOrder to still be open (not yet
 * RECEIVED or CLOSED) but it already is — closing an already-closed/received
 * order, or creating a PurchaseReceipt against one. Maps to HTTP 422, same
 * convention as PurchaseOrderNotEditableException; kept as a separate class
 * since "open" (not RECEIVED/CLOSED) and "editable" (DRAFT only) are
 * different predicates — a SUBMITTED order is open but not editable.
 */
@Getter
public class PurchaseOrderNotOpenException extends RuntimeException {

    private final Long purchaseOrderId;
    private final PurchaseOrderStatus status;

    public PurchaseOrderNotOpenException(Long purchaseOrderId, PurchaseOrderStatus status) {
        super("Purchase order " + purchaseOrderId + " is already " + status
                + "; it can no longer be closed or receive new PurchaseReceipts against it");
        this.purchaseOrderId = purchaseOrderId;
        this.status = status;
    }
}
