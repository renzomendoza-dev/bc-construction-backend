package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when a resource (Item, Supplier, Warehouse, StorageLocation,
 * InventoryStock, StockMovement, PurchaseReceipt, etc.) cannot be found by its identifier.
 * Maps to HTTP 404 Not Found.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
        this.resourceName = resourceName;
        this.resourceId = id;
    }
}
