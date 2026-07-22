package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload representing the current stock level of an Item
 * at a specific Warehouse and, optionally, StorageLocation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelResponse {

    @Schema(description = "Identifier of the item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the item", example = "Wireless Mouse")
    private String itemName;

    @Schema(description = "Stock keeping unit code of the item", example = "SKU-12345")
    private String sku;

    @Schema(description = "Identifier of the warehouse holding the stock", example = "1")
    private Long warehouseId;

    @Schema(description = "Name of the warehouse holding the stock", example = "Main Distribution Center")
    private String warehouseName;

    @Schema(description = "Identifier of the storage location within the warehouse, if tracked", example = "7")
    private Long locationId;

    @Schema(description = "Code of the storage location within the warehouse, if tracked", example = "RACK-A-03")
    private String locationCode;

    @Schema(description = "Current quantity on hand", example = "150")
    private Integer quantity;

    @Schema(description = "Quantity threshold below which the item is considered low stock, if configured", example = "20")
    private Integer reorderThreshold;
}
