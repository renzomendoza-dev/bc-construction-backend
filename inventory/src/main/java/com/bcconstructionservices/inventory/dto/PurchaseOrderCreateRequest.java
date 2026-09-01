package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for creating a draft purchase order. lines is typically
 * pre-filled from GET /api/purchase-orders/suggestions?supplierId=, but
 * that's purely a frontend convenience — this endpoint doesn't know or care
 * where the caller's line items came from, and accepts any well-formed list.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to create a draft purchase order")
public class PurchaseOrderCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the supplier this order is placed with", example = "5")
    private Long supplierId;

    @Schema(description = "Optional free-text notes about this order", example = "Requested delivery before end of month")
    private String notes;

    @NotEmpty(message = "A purchase order must have at least one line")
    @Valid
    @Schema(description = "Line items being ordered; at least one is required")
    private List<PurchaseOrderLineRequest> lines;
}
