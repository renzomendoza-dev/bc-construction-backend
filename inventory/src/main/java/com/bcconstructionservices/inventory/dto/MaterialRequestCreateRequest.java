package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating a new MaterialRequest along with its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the site (a Warehouse of type SITE) this request is for", example = "2")
    private Long siteWarehouseId;

    @FutureOrPresent(message = "Date needed must not be in the past")
    @Schema(description = "Date the materials are needed by, if known", example = "2026-08-01")
    private LocalDate dateNeeded;

    @Schema(description = "Optional free-text notes about the request", example = "For the east wing pour")
    private String notes;

    @NotEmpty(message = "A material request must have at least one line")
    @Valid
    @Schema(description = "Line items being requested; at least one is required")
    private List<MaterialRequestLineItemRequest> lines;
}
