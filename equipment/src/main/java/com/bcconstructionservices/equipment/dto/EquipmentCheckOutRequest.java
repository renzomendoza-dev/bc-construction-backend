package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload to check out equipment to a user")
public class EquipmentCheckOutRequest {

    @NotNull
    @Positive
    @Schema(description = "App-local user ID receiving the equipment", example = "17")
    private Long userId;

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Job site or location the equipment is going to", example = "Site B - Riverside", maxLength = 150)
    private String site;

    @Size(max = 500)
    @Schema(description = "Condition notes recorded at checkout", example = "Minor scuff on housing, fully functional", maxLength = 500)
    private String conditionOut;
}