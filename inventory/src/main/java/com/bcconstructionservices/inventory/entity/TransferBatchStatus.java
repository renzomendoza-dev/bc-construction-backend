package com.bcconstructionservices.inventory.entity;

public enum TransferBatchStatus {
    DRAFT,
    SUBMITTED,
    COMPLETED,
    /**
     * submit() failed because InventoryService.transferStock reported
     * insufficient stock on at least one line. Set by
     * TransferBatchStatusUpdater (a separate REQUIRES_NEW transaction — see
     * its javadoc for why) instead of the doomed submit() transaction itself.
     * A PurchaseReceipt can reference a batch in this state via
     * fulfillsTransferBatchId; confirming that receipt flips the batch back
     * to DRAFT so it can be resubmitted.
     */
    AWAITING_PURCHASE
}
