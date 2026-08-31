package com.bcconstructionservices.equipment.exception;

public class EquipmentAssignmentBatchNotFoundException extends RuntimeException {
    public EquipmentAssignmentBatchNotFoundException(Long id) {
        super("Equipment assignment batch not found with id: " + id);
    }
}
