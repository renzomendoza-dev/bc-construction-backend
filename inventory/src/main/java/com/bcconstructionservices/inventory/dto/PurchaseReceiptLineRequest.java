package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request payload for a single line item within a PurchaseReceiptCreateRequest.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseReceiptLineRequest {

    @NotNull
    @Schema(description = "Identifier of the item being purchased", example = "1")
    private Long itemId;

    @NotNull
    @Positive(message = "Quantity must be greater than zero")
    @Schema(description = "Quantity of the item purchased", example = "100")
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", message = "Unit cost must not be negative")
    @Schema(description = "Cost per unit paid on this line, must be non-negative", example = "9.75")
    private BigDecimal unitCost;
}
