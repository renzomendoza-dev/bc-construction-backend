package com.bcconstructionservices.inventory.entity;

/**
 * Net effect of a StockMovement row on its OWN warehouse's stock level —
 * set explicitly at construction time in InventoryService, never inferred
 * later from which of fromLocation/toLocation happens to be populated (that
 * inference breaks once a TRANSFER's origin side can debit the no-location
 * bucket, e.g. via transferWarehouseStock, since both the OUT and IN rows
 * can then have fromLocation/toLocation both null).
 */
public enum MovementDirection {
    /** Quantity increased at this row's warehouse (IN, ADJUSTMENT, or a TRANSFER's destination-side row). */
    IN,
    /** Quantity decreased at this row's warehouse (OUT, or a TRANSFER's origin-side row). */
    OUT,
    /** Net-zero at the warehouse level — a TRANSFER between two locations in the SAME warehouse, one row. */
    WITHIN
}
