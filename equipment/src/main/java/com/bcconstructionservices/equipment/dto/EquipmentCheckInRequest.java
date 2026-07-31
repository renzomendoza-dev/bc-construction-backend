package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request payload to check in previously checked-out equipment")
public class EquipmentCheckInRequest {

    @Size(max = 500)
    @Schema(description = "Condition notes recorded at check-in", example = "Returned in working order, blade needs sharpening", maxLength = 500)
    private String conditionIn;
}