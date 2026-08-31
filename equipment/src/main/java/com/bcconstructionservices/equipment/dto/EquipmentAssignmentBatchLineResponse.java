package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One piece of equipment in an assignment batch")
public class EquipmentAssignmentBatchLineResponse {

    @Schema(example = "501")
    private Long id;

    @Schema(example = "42")
    private Long equipmentId;

    @Schema(example = "EQ-2026-0042")
    private String equipmentAssetTag;

    @Schema(description = "Equipment name, for display without a second lookup", example = "DeWalt 20V Cordless Drill")
    private String equipmentName;

    @Schema(example = "Minor scuff on housing, fully functional")
    private String conditionNotes;
}
