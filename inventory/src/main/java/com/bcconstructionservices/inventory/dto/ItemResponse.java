package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full response payload representing an Item, including its images.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemResponse {

    @Schema(description = "Unique identifier of the item", example = "1")
    private Long id;

    @Schema(description = "Unique stock keeping unit code for the item", example = "SKU-12345")
    private String sku;

    @Schema(description = "Display name of the item", example = "Wireless Mouse")
    private String name;

    @Schema(description = "Category the item belongs to", example = "Electronics")
    private String category;

    @Schema(description = "Unit of measure used for stock counts", example = "pcs")
    private String unitOfMeasure;

    @Schema(description = "Price at which the item is sold", example = "19.99")
    private BigDecimal sellingPrice;

    @Schema(description = "Optional reference-only default cost price", example = "12.50")
    private BigDecimal defaultCostPrice;

    @Schema(description = "Whether the item is currently active", example = "true")
    private boolean active;

    @Schema(description = "List of images associated with the item, ordered by sortOrder")
    private List<ItemImageResponse> images;

    @Schema(description = "Timestamp when the item was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the item was last updated", example = "2026-07-18T09:15:30Z")
    private Instant updatedAt;
}
