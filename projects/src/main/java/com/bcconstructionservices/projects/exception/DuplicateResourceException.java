package com.bcconstructionservices.projects.exception;

import lombok.Getter;

/**
 * Thrown when a service-level check detects a unique-constraint violation
 * before it would otherwise fail at the database (e.g. duplicate Project
 * code). Maps to HTTP 409 Conflict.
 */
@Getter
public class DuplicateResourceException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(resourceName + " already exists with " + fieldName + ": " + fieldValue);
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}
