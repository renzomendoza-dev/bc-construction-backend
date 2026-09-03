package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import lombok.Getter;

/**
 * Thrown when deleting a PurchaseOrder is attempted while its status isn't
 * DRAFT. Kept separate from {@link PurchaseOrderNotEditableException} even
 * though both share the same "still DRAFT" predicate — that exception's
 * message is specifically about editing/submitting, which doesn't fit
 * deletion (matches this codebase's existing per-action exception
 * convention: compare MaterialRequestNotEditableException vs.
 * MaterialRequestNotDeletableException). Maps to HTTP 422, this module's
 * established convention for a lifecycle-state violation.
 */
@Getter
public class PurchaseOrderNotDeletableException extends RuntimeException {

    private final Long purchaseOrderId;
    private final PurchaseOrderStatus status;

    public PurchaseOrderNotDeletableException(Long purchaseOrderId, PurchaseOrderStatus status) {
        super("Purchase order " + purchaseOrderId + " cannot be deleted (status: " + status
                + "); only a DRAFT order can be deleted");
        this.purchaseOrderId = purchaseOrderId;
        this.status = status;
    }
}
