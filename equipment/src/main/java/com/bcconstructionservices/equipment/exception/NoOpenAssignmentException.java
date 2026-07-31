package com.bcconstructionservices.equipment.exception;

public class NoOpenAssignmentException extends RuntimeException {
    public NoOpenAssignmentException(Long equipmentId) {
        super("No open assignment found for equipment id: " + equipmentId);
    }
}