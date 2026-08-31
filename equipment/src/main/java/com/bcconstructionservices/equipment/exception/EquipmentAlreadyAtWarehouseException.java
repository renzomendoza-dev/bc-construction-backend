package com.bcconstructionservices.equipment.exception;

/**
 * Thrown when a site-to-site transfer's destination resolves to the same
 * warehouse the equipment is already at — not a meaningful transfer. Maps to
 * HTTP 400, matching TransferBatch's own "origin and destination cannot be
 * the same" check (InvalidStockOperationException, also 400) in the
 * inventory module, and this module's own InvalidWarehouseTypeException:
 * both are a structural mismatch between the request and the resource it
 * references, not a status conflict (409, reserved here for equipment's
 * current state) or a lifecycle-progression issue (422, not used in this
 * module at all).
 */
public class EquipmentAlreadyAtWarehouseException extends RuntimeException {
    public EquipmentAlreadyAtWarehouseException(Long equipmentId, Long warehouseId) {
        super("Equipment " + equipmentId + " is already at warehouse " + warehouseId
                + " — a transfer must target a different warehouse");
    }
}
