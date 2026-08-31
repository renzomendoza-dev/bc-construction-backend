package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new Warehouse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseCreateRequest {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "Unique short code identifying the warehouse", example = "WH-MAIN")
    private String code;

    @NotBlank
    @Schema(description = "Display name of the warehouse", example = "Main Distribution Center")
    private String name;

    @Schema(description = "Type of warehouse; defaults to MAIN when omitted", example = "MAIN",
            allowableValues = {"MAIN", "SITE"})
    private WarehouseType type;
}
