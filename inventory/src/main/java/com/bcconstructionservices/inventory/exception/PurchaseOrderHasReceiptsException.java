package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when deleting a PurchaseOrder is attempted while one or more
 * PurchaseReceipts already reference it via purchaseOrderId.
 * createPurchaseReceipt() deliberately allows linking a receipt to a DRAFT
 * order (it only rejects RECEIVED/CLOSED — see its own inline comment), so a
 * still-DRAFT order can legitimately already have real, DB-FK-referencing
 * receipts against it; deleting it out from under those would either violate
 * that FK or silently orphan history. Unlike
 * {@link PurchaseOrderNotDeletableException} (a lifecycle-state check on the
 * order itself), this is a conflict discovered at operation time against a
 * different resource — matches this module's 409 convention (compare
 * InsufficientStockException) rather than the 422 "wrong status" convention.
 */
@Getter
public class PurchaseOrderHasReceiptsException extends RuntimeException {

    private final Long purchaseOrderId;

    public PurchaseOrderHasReceiptsException(Long purchaseOrderId) {
        super("Purchase order " + purchaseOrderId + " cannot be deleted; one or more PurchaseReceipts "
                + "already reference it");
        this.purchaseOrderId = purchaseOrderId;
    }
}
