package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload representing a single image associated with an Item.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemImageResponse {

    @Schema(description = "Unique identifier of the image", example = "42")
    private Long id;

    @Schema(description = "Relative path or URL to the image file", example = "/images/items/1/photo1.jpg")
    private String imageUrl;

    @Schema(description = "Display order of the image, lower values are shown first", example = "0")
    private Integer sortOrder;
}
