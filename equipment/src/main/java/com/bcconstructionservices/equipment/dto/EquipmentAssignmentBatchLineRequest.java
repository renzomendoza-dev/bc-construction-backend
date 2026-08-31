package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class EquipmentAssignmentBatchLineRequest {

    @NotNull
    @Positive
    @Schema(description = "Identifier of the equipment on this line", example = "42")
    private Long equipmentId;

    @Size(max = 500)
    @Schema(description = "Condition notes for this piece of equipment — conditionOut for an assign-out line, "
            + "conditionIn for a return line, or both sides of a transfer line (the closed assignment's "
            + "conditionIn and the new one's conditionOut)",
            example = "Minor scuff on housing, fully functional", maxLength = 500)
    private String conditionNotes;
}
