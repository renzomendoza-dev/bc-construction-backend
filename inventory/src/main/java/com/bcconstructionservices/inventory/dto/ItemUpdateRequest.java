package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request payload for updating an existing Item.
 * All fields are optional; only non-null fields should be applied by the service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemUpdateRequest {

    @Schema(description = "Unique stock keeping unit code for the item", example = "SKU-12345")
    private String sku;

    @Size(max = 255)
    @Schema(description = "Display name of the item", example = "Wireless Mouse")
    private String name;

    @Size(max = 255)
    @Schema(description = "Category the item belongs to", example = "Electronics")
    private String category;

    @Schema(description = "Unit of measure used for stock counts", example = "pcs")
    private String unitOfMeasure;

    @DecimalMin(value = "0.0", message = "Selling price must not be negative")
    @Schema(description = "Price at which the item is sold, must be non-negative", example = "21.99")
    private BigDecimal sellingPrice;

    @DecimalMin(value = "0.0", message = "Default cost price must not be negative")
    @Schema(description = "Optional reference-only default cost price, must be non-negative", example = "13.00")
    private BigDecimal defaultCostPrice;
}
