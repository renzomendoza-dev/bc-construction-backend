package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when attempting to transact against an inactive Item, Supplier, or
 * Warehouse (e.g. adjusting stock for a deactivated item, or linking an
 * inactive supplier). Maps to HTTP 400 Bad Request.
 */
@Getter
public class InactiveResourceException extends RuntimeException {

    private final String resourceName;
    private final Object resourceId;

    public InactiveResourceException(String resourceName, Object id) {
        super(resourceName + " with id " + id + " is inactive and cannot be used in this operation");
        this.resourceName = resourceName;
        this.resourceId = id;
    }
}
