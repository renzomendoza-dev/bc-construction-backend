package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when a PurchaseReceipt fails to process, e.g. a line references an
 * itemId that doesn't exist, or the receipt has no lines.
 * Maps to HTTP 422 Unprocessable Entity.
 */
@Getter
public class ReceiptProcessingException extends RuntimeException {

    private final Long receiptId;

    /**
     * Use for failures that occur before a receipt has been persisted / assigned an id
     * (e.g. "Purchase receipt must contain at least one line").
     */
    public ReceiptProcessingException(String message) {
        super(message);
        this.receiptId = null;
    }

    /**
     * Use for failures tied to a specific, already-identified receipt
     * (e.g. line references an unknown itemId).
     */
    public ReceiptProcessingException(Long receiptId, String message) {
        super("Failed to process purchase receipt " + receiptId + ": " + message);
        this.receiptId = receiptId;
    }
}
