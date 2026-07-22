package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for recording a new PurchaseReceipt along with its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseReceiptCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the supplier this receipt was purchased from", example = "5")
    private Long supplierId;

    @NotNull
    @Schema(description = "Identifier of the warehouse this receipt's stock will be received into once confirmed", example = "1")
    private Long warehouseId;

    @Schema(description = "Supplier-provided receipt or invoice number", example = "INV-2026-00842")
    private String receiptNumber;

    @NotNull
    @PastOrPresent(message = "Purchase date must not be in the future")
    @Schema(description = "Date the purchase was made, must not be in the future", example = "2026-07-15")
    private LocalDate purchaseDate;

    @Schema(description = "Relative path or URL to the scanned receipt file, if available", example = "/receipts/2026/07/inv-00842.jpg")
    private String imageUrl;

    @Schema(description = "Optional free-text notes about the purchase", example = "Partial shipment, remainder expected next week")
    private String notes;

    @NotEmpty(message = "A receipt must have at least one line")
    @Valid
    @Schema(description = "Line items purchased on this receipt; at least one is required")
    private List<PurchaseReceiptLineRequest> lines;
}
