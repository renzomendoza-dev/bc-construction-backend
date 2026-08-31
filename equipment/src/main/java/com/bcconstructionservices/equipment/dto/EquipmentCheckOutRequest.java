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
@Schema(description = "Request payload to check out equipment to a user, or to transfer it directly between "
        + "two SITE warehouses if it's already checked out elsewhere")
public class EquipmentCheckOutRequest {

    @NotNull
    @Positive
    @Schema(description = "App-local user ID receiving the equipment. Required even for a site-to-site "
            + "transfer of already-checked-out equipment — either reconfirming the existing holder or "
            + "reassigning to someone new.", example = "17")
    private Long userId;

    @NotNull
    @Positive
    @Schema(description = "Identifier of the SITE-type warehouse the equipment is going to (400 if it isn't a "
            + "SITE warehouse, or if it's the warehouse the equipment is already at)", example = "2")
    private Long siteWarehouseId;

    @Size(max = 500)
    @Schema(description = "Condition notes recorded at checkout (or at both sides of a transfer — the closed "
            + "assignment's conditionIn and the new one's conditionOut)",
            example = "Minor scuff on housing, fully functional", maxLength = 500)
    private String conditionOut;
}