package com.bcconstructionservices.projects.exception;

import lombok.Getter;

/**
 * Thrown when a resource (Project, ProjectExpense) cannot be found by its
 * identifier. Maps to HTTP 404 Not Found.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
        this.resourceName = resourceName;
        this.resourceId = id;
    }
}
