package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A purchase order placed with a supplier, along with its line items")
public class PurchaseOrderResponse {

    @Schema(example = "12")
    private Long id;

    @Schema(description = "Identifier of the supplier this order is placed with", example = "5")
    private Long supplierId;

    @Schema(description = "Name of the supplier this order is placed with", example = "Acme Distribution Co.")
    private String supplierName;

    @Schema(example = "SUBMITTED",
            allowableValues = {"DRAFT", "SUBMITTED", "PARTIALLY_RECEIVED", "RECEIVED", "CLOSED"})
    private PurchaseOrderStatus status;

    @Schema(example = "Requested delivery before end of month")
    private String notes;

    @Schema(description = "ID of the user who created this purchase order", example = "3")
    private Long initiatedBy;

    @Schema(description = "Full name of the user who created this purchase order", example = "Juan Dela Cruz")
    private String initiatedByName;

    @Schema(description = "Line items on this order")
    private List<PurchaseOrderLineResponse> lines;

    private Instant createdAt;
    private Instant updatedAt;
}
