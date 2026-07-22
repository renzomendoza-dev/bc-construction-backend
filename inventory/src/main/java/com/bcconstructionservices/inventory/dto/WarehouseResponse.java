package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response payload representing a Warehouse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseResponse {

    @Schema(description = "Unique identifier of the warehouse", example = "1")
    private Long id;

    @Schema(description = "Unique short code identifying the warehouse", example = "WH-MAIN")
    private String code;

    @Schema(description = "Display name of the warehouse", example = "Main Distribution Center")
    private String name;

    @Schema(description = "Whether the warehouse is currently active", example = "true")
    private boolean active;

    @Schema(description = "Timestamp when the warehouse was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the warehouse was last updated", example = "2026-07-18T09:15:30Z")
    private Instant updatedAt;
}
