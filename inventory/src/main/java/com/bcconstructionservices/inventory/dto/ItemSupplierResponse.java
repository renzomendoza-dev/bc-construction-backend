package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Response payload representing the link between an Item and a Supplier.
 * Related entity names are flattened in rather than nesting full Item/Supplier objects.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemSupplierResponse {

    @Schema(description = "Unique identifier of the item-supplier link", example = "10")
    private Long id;

    @Schema(description = "Identifier of the linked item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the linked item", example = "Wireless Mouse")
    private String itemName;

    @Schema(description = "Identifier of the linked supplier", example = "5")
    private Long supplierId;

    @Schema(description = "Name of the linked supplier", example = "Acme Distribution Co.")
    private String supplierName;

    @Schema(description = "Supplier's own SKU or product code for this item, if different from the internal SKU", example = "AC-9981-BLK")
    private String supplierSku;

    @Schema(description = "Most recent purchase cost from this supplier for this item", example = "12.50")
    private BigDecimal unitCost;
}
