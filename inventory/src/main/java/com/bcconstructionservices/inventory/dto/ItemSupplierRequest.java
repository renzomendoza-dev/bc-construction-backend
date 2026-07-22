package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request payload for linking an Item to a Supplier, including
 * the supplier's own SKU and most recent unit cost.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemSupplierRequest {

    @NotNull
    @Schema(description = "Identifier of the item being linked to the supplier", example = "1")
    private Long itemId;

    @NotNull
    @Schema(description = "Identifier of the supplier being linked to the item", example = "5")
    private Long supplierId;

    @Schema(description = "Supplier's own SKU or product code for this item, if different from the internal SKU", example = "AC-9981-BLK")
    private String supplierSku;

    @NotNull
    @DecimalMin(value = "0.0", message = "Unit cost must not be negative")
    @Schema(description = "Most recent purchase cost from this supplier for this item, must be non-negative", example = "12.50")
    private BigDecimal unitCost;
}
