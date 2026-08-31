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
@Schema(description = "Request payload to check in previously checked-out equipment")
public class EquipmentCheckInRequest {

    @NotNull
    @Positive
    @Schema(description = "Identifier of the MAIN-type warehouse the equipment is being returned to (400 if it "
            + "isn't a MAIN warehouse)", example = "1")
    private Long destinationWarehouseId;

    @Size(max = 500)
    @Schema(description = "Condition notes recorded at check-in", example = "Returned in working order, blade needs sharpening", maxLength = 500)
    private String conditionIn;
}