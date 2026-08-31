package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for creating a draft equipment assignment batch. Direction
 * is derived from destinationWarehouseId's resolved Warehouse.type, not a
 * separate field — but a SITE destination alone doesn't fully resolve it:
 * it means holderId is required, covering both an assign-out (equipment
 * currently AVAILABLE) and a direct site-to-site transfer (equipment
 * already CHECKED_OUT/IN_USE elsewhere) — which one a given line is depends
 * on that equipment's status, resolved at submit time. MAIN unambiguously
 * means a return batch (holderId must be omitted).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to create a draft batch of equipment assignments (checkouts), "
        + "site-to-site transfers, or returns (check-ins)")
public class EquipmentAssignmentBatchCreateRequest {

    @NotNull
    @Positive
    @Schema(description = "Identifier of the destination warehouse — SITE-type for an assign-out or transfer "
            + "batch (holderId required either way), MAIN-type for a return batch (holderId must be omitted)",
            example = "2")
    private Long destinationWarehouseId;

    @Positive
    @Schema(description = "App-local user id taking (or reconfirmed as) custody — required whenever "
            + "destinationWarehouseId is a SITE warehouse (assign-out or transfer), must be omitted/null for a "
            + "return batch (destination is MAIN)", example = "17")
    private Long holderId;

    @Schema(description = "Optional free-text notes about this batch", example = "Weekly dispatch for Sta. Maria site")
    private String notes;

    @NotEmpty(message = "A batch must have at least one line")
    @Valid
    @Schema(description = "Equipment included in this batch; at least one is required")
    private List<EquipmentAssignmentBatchLineRequest> lines;
}
