package com.bcconstructionservices.inventory.exception;

/**
 * Thrown for logically invalid stock requests that are not about stock
 * levels specifically (e.g. quantity <= 0 passed to an adjustment, or a
 * transfer where fromWarehouseId equals toWarehouseId).
 * Maps to HTTP 400 Bad Request.
 *
 * Callers are expected to supply a descriptive message that includes the
 * offending value(s), e.g.:
 *   new InvalidStockOperationException("Adjustment quantity must be greater than zero, received: " + quantity)
 *   new InvalidStockOperationException("Transfer source and destination warehouse cannot be the same (warehouseId: " + warehouseId + ")")
 */
public class InvalidStockOperationException extends RuntimeException {

    public InvalidStockOperationException(String message) {
        super(message);
    }
}
