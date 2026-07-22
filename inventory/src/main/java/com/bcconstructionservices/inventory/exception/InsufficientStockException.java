package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when an OUT or TRANSFER stock operation would drop
 * InventoryStock.quantity below zero. Maps to HTTP 409 Conflict.
 */
@Getter
public class InsufficientStockException extends RuntimeException {

    private final Long itemId;
    private final Long warehouseId;
    private final Integer requestedQuantity;
    private final Integer availableQuantity;

    public InsufficientStockException(Long itemId, Long warehouseId, Integer requestedQuantity, Integer availableQuantity) {
        super(String.format(
                "Insufficient stock for item %d at warehouse %d: requested %d, available %d",
                itemId, warehouseId, requestedQuantity, availableQuantity));
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}
