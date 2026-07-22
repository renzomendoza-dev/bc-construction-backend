package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload representing a StorageLocation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageLocationResponse {

    @Schema(description = "Unique identifier of the storage location", example = "7")
    private Long id;

    @Schema(description = "Identifier of the warehouse this location belongs to", example = "1")
    private Long warehouseId;

    @Schema(description = "Code identifying the location within its warehouse, e.g. a rack or box label", example = "RACK-A-03")
    private String code;
}
