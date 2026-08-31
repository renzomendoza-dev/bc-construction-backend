package com.bcconstructionservices.equipment.exception;

public class WarehouseNotFoundException extends RuntimeException {
    public WarehouseNotFoundException(Long warehouseId) {
        super("Warehouse not found with id: " + warehouseId);
    }
}
