package com.bcconstructionservices.inventory.exception;

import lombok.Getter;

/**
 * Thrown when a service-level check detects a unique-constraint violation
 * before it would otherwise fail at the database (e.g. duplicate Item SKU,
 * duplicate Warehouse code, duplicate StorageLocation code within a warehouse).
 * Maps to HTTP 409 Conflict.
 */
@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public DuplicateResourceException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName = null;
        this.fieldValue = null;
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(resourceName + " already exists with " + fieldName + ": " + fieldValue);
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}
