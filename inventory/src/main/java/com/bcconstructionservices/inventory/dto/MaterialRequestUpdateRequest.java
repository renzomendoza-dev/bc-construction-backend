package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for replacing an existing MaterialRequest's editable
 * fields and line items. siteWarehouseId is deliberately excluded — the
 * site a request belongs to is immutable after creation, same as
 * Warehouse.code is immutable after creation.
 * <p>
 * Unlike WarehouseUpdateRequest (a partial patch — null means "don't touch
 * this field"), this is a full replacement: dateNeeded and notes are copied
 * as-is including null, so sending null explicitly clears them. lines is
 * always a full replacement of the existing line items, never a merge.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestUpdateRequest {

    @FutureOrPresent(message = "Date needed must not be in the past")
    @Schema(description = "Date the materials are needed by; omit or send null to clear it", example = "2026-09-01")
    private LocalDate dateNeeded;

    @Schema(description = "Optional free-text notes about the request; omit or send null to clear it",
            example = "For the east wing pour")
    private String notes;

    @NotEmpty(message = "A material request must have at least one line")
    @Valid
    @Schema(description = "Line items being requested — replaces the existing lines entirely; at least one is required")
    private List<MaterialRequestLineItemRequest> lines;
}
