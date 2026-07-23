package com.bcconstructionservices.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to set or update the reorder threshold on an existing InventoryStock
 * row for a specific item + warehouse + (optional) location combination.
 *
 * <p>locationId is nullable: a null location targets the warehouse-level stock
 * row (no specific bin/location), consistent with how InventoryStock and its
 * repository finder treat a null location elsewhere in this domain.
 */
public class ReorderThresholdRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private Long warehouseId;

    // Nullable - null means the warehouse-level (no specific location) stock row.
    private Long locationId;

    @NotNull
    @PositiveOrZero
    private Integer reorderThreshold;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Integer getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(Integer reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }
}