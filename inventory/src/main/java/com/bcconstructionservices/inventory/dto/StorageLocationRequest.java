package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a StorageLocation within a Warehouse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageLocationRequest {

    @NotNull
    @Schema(description = "Identifier of the warehouse this location belongs to", example = "1")
    private Long warehouseId;

    @NotBlank
    @Size(max = 32)
    @Schema(description = "Code identifying the location within its warehouse, e.g. a rack or box label", example = "RACK-A-03")
    private String code;
}
