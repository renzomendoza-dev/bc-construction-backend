package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single historical purchase entry for an item, as shown in PurchaseHistoryResponse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseHistoryEntry {

    @Schema(description = "Identifier of the purchase receipt this entry came from", example = "20")
    private Long receiptId;

    @Schema(description = "Date the purchase was made", example = "2026-07-15")
    private LocalDate purchaseDate;

    @Schema(description = "Name of the supplier the item was purchased from", example = "Acme Distribution Co.")
    private String supplierName;

    @Schema(description = "Quantity of the item purchased on this receipt", example = "100")
    private Integer quantity;

    @Schema(description = "Cost per unit paid on this purchase", example = "9.75")
    private BigDecimal unitCost;
}
