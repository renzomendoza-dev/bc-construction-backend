package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight response payload for list views, showing only the essentials
 * needed to display an item alongside its primary image.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemSummaryResponse {

    @Schema(description = "Unique identifier of the item", example = "1")
    private Long id;

    @Schema(description = "Unique stock keeping unit code for the item", example = "SKU-12345")
    private String sku;

    @Schema(description = "Display name of the item", example = "Wireless Mouse")
    private String name;

    @Schema(description = "Category the item belongs to", example = "Electronics")
    private String category;

    @Schema(description = "URL of the item's primary (first) image, if any", example = "/images/items/1/photo1.jpg")
    private String primaryImageUrl;

    @Schema(description = "Whether the item is currently active", example = "true")
    private boolean active;
}
