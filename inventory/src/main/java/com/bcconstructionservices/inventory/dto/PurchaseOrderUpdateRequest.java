package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for replacing an existing DRAFT purchase order's editable
 * fields and line items (422 if the order isn't DRAFT). supplierId is
 * deliberately excluded — immutable after creation, same as MaterialRequest's
 * siteWarehouseId. Full replacement, not a partial patch: notes is copied as
 * given (including null, clearing it), and lines always fully replaces the
 * existing line items.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to replace a DRAFT purchase order's notes and line items")
public class PurchaseOrderUpdateRequest {

    @Schema(description = "Optional free-text notes about this order; omit or send null to clear it",
            example = "Requested delivery before end of month")
    private String notes;

    @NotEmpty(message = "A purchase order must have at least one line")
    @Valid
    @Schema(description = "Line items being ordered — replaces the existing lines entirely; at least one is required")
    private List<PurchaseOrderLineRequest> lines;
}
