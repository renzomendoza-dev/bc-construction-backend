package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response payload representing a PurchaseReceipt along with its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseReceiptResponse {

    @Schema(description = "Unique identifier of the purchase receipt", example = "20")
    private Long id;

    @Schema(description = "Identifier of the supplier this receipt was purchased from", example = "5")
    private Long supplierId;

    @Schema(description = "Name of the supplier this receipt was purchased from", example = "Acme Distribution Co.")
    private String supplierName;

    @Schema(description = "Identifier of the warehouse this receipt's stock is received into", example = "1")
    private Long warehouseId;

    @Schema(description = "Name of the warehouse this receipt's stock is received into", example = "Main Distribution Center")
    private String warehouseName;

    @Schema(description = "Supplier-provided receipt or invoice number", example = "INV-2026-00842")
    private String receiptNumber;

    @Schema(description = "Date the purchase was made", example = "2026-07-15")
    private LocalDate purchaseDate;

    @Schema(description = "Total amount of the receipt across all lines, if provided", example = "975.00")
    private BigDecimal totalAmount;

    @Schema(description = "Relative path or URL to the scanned receipt file, if available", example = "/receipts/2026/07/inv-00842.jpg")
    private String imageUrl;

    @Schema(description = "Optional free-text notes about the purchase", example = "Partial shipment, remainder expected next week")
    private String notes;

    @Schema(description = "Identifier of the TransferBatch this receipt is purchasing the shortfall for, if any",
            example = "42")
    private Long fulfillsTransferBatchId;

    @Schema(description = "Identifier of the PurchaseOrder this receipt is (at least partially) fulfilling, if any",
            example = "12")
    private Long purchaseOrderId;

    @Schema(description = "Line items purchased on this receipt")
    private List<PurchaseReceiptLineResponse> lines;

    @Schema(description = "Whether this receipt has been confirmed and applied to inventory", example = "false")
    private boolean confirmed;

    @Schema(description = "ID of the user who confirmed this purchase receipt, applying stock and updating supplier cost", example = "5")
    private Long confirmedBy;

    @Schema(description = "Full name of the user who confirmed this purchase receipt", example = "Maria Santos")
    private String confirmedByName;

    @Schema(description = "ID of the user who created this purchase receipt", example = "3")
    private Long createdBy;

    @Schema(description = "Full name of the user who created this purchase receipt", example = "Juan Dela Cruz")
    private String createdByName;

    @Schema(description = "Timestamp when the receipt was confirmed and applied to inventory, if it has been", example = "2026-07-16T08:05:00Z")
    private Instant confirmedAt;

    @Schema(description = "Timestamp when the receipt was recorded", example = "2026-07-15T14:22:10Z")
    private Instant createdAt;
}
