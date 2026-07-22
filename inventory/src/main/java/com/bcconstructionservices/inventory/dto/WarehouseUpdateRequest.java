package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for updating an existing Warehouse.
 * All fields are optional; only non-null fields should be applied by the service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseUpdateRequest {

    @Schema(description = "Display name of the warehouse", example = "Main Distribution Center")
    private String name;

    @Schema(description = "Whether the warehouse is currently active", example = "true")
    private Boolean active;
}
