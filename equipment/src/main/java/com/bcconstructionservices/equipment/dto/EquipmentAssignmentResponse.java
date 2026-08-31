package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single checkout/check-in assignment record for a piece of equipment")
public class EquipmentAssignmentResponse {

    @Schema(example = "301")
    private Long id;

    @Schema(example = "42")
    private Long equipmentId;

    @Schema(example = "EQ-2026-0042")
    private String equipmentAssetTag;

    @Schema(description = "App-local user ID this equipment was assigned to", example = "17")
    private Long assignedToId;

    @Schema(description = "Resolved display name of the assignee", example = "Maria Santos")
    private String assignedToName;

    @Schema(description = "Identifier of the warehouse this equipment was checked out to", example = "2")
    private Long warehouseId;

    @Schema(description = "Resolved display name of that warehouse", example = "Site B - Riverside")
    private String warehouseName;

    @Schema(description = "Identifier of the warehouse this equipment was checked back in to, if it has been",
            example = "1")
    private Long returnWarehouseId;

    @Schema(description = "Resolved display name of the return warehouse", example = "Main Warehouse")
    private String returnWarehouseName;

    private Instant checkedOutAt;
    private Instant checkedInAt;

    @Schema(example = "Minor scuff on housing, fully functional")
    private String conditionOut;

    @Schema(example = "Returned in working order, blade needs sharpening")
    private String conditionIn;

    private Long createdBy;
    private Instant createdAt;
}