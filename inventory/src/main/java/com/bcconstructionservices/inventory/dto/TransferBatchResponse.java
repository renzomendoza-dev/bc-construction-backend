package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Response payload representing a TransferBatch along with its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferBatchResponse {

    @Schema(description = "Unique identifier of the transfer batch", example = "42")
    private Long id;

    @Schema(description = "Identifier of the warehouse (or site) stock was moved from", example = "1")
    private Long originWarehouseId;

    @Schema(description = "Name of the warehouse (or site) stock was moved from", example = "Main Warehouse")
    private String originWarehouseName;

    @Schema(description = "Identifier of the warehouse (or site) stock was moved to", example = "2")
    private Long destinationWarehouseId;

    @Schema(description = "Name of the warehouse (or site) stock was moved to", example = "Site Warehouse - Sta. Maria Project")
    private String destinationWarehouseName;

    @Schema(description = "Current status of the batch. AWAITING_PURCHASE means the last submit attempt failed "
            + "on insufficient stock (409) — see POST /{id}/submit — and this batch is blocked until a "
            + "PurchaseReceipt with fulfillsTransferBatchId set to this batch's id is confirmed, which flips it "
            + "back to DRAFT so it can be resubmitted.",
            example = "DRAFT", allowableValues = {"DRAFT", "SUBMITTED", "COMPLETED", "AWAITING_PURCHASE"})
    private TransferBatchStatus status;

    @Schema(description = "ID of the user who initiated this batch", example = "3")
    private Long initiatedBy;

    @Schema(description = "Full name of the user who initiated this batch", example = "Juan Dela Cruz")
    private String initiatedByName;

    @Schema(description = "Identifier of the MaterialRequest this batch fulfills, if any", example = "14")
    private Long sourceMaterialRequestId;

    @Schema(description = "Optional free-text notes about the transfer", example = "Weekly resupply for Sta. Maria site")
    private String notes;

    @Schema(description = "Line items included in this batch")
    private List<TransferLineItemResponse> lines;

    @Schema(description = "Timestamp when the batch was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the batch was last updated", example = "2026-07-18T09:20:00Z")
    private Instant updatedAt;
}
