package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response payload representing a MaterialRequest along with its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestResponse {

    @Schema(description = "Unique identifier of the material request", example = "14")
    private Long id;

    @Schema(description = "Identifier of the site (a Warehouse of type SITE) this request is for", example = "2")
    private Long siteWarehouseId;

    @Schema(description = "Name of the site this request is for", example = "Site Warehouse - Sta. Maria Project")
    private String siteWarehouseName;

    @Schema(description = "ID of the user who made this request", example = "7")
    private Long requestedBy;

    @Schema(description = "Full name of the user who made this request", example = "Maria Santos")
    private String requestedByName;

    @Schema(description = "Date the materials are needed by, if known", example = "2026-08-01")
    private LocalDate dateNeeded;

    @Schema(description = "Current status of the request", example = "SUBMITTED",
            allowableValues = {"DRAFT", "SUBMITTED", "PARTIALLY_FULFILLED", "FULFILLED"})
    private MaterialRequestStatus status;

    @Schema(description = "Optional free-text notes about the request", example = "For the east wing pour")
    private String notes;

    @Schema(description = "Line items requested")
    private List<MaterialRequestLineItemResponse> lines;

    @Schema(description = "Timestamp when the request was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the request was last updated", example = "2026-07-18T09:20:00Z")
    private Instant updatedAt;
}
