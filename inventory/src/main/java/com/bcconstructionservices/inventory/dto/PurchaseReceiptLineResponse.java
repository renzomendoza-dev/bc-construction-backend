package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Response payload representing a single line item on a PurchaseReceipt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseReceiptLineResponse {

    @Schema(description = "Unique identifier of the purchase receipt line", example = "88")
    private Long id;

    @Schema(description = "Identifier of the purchased item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the purchased item", example = "Wireless Mouse")
    private String itemName;

    @Schema(description = "Quantity of the item purchased", example = "100")
    private Integer quantity;

    @Schema(description = "Cost per unit paid on this line", example = "9.75")
    private BigDecimal unitCost;

    @Schema(description = "Total cost for this line (quantity multiplied by unit cost)", example = "975.00")
    private BigDecimal lineTotal;
}
