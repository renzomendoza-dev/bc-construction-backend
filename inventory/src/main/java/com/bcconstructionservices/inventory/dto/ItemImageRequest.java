package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for adding or updating an image on an Item.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemImageRequest {

    @NotBlank
    @Schema(description = "Relative path or URL to the image file; no binary data is stored", example = "/images/items/1/photo1.jpg")
    private String imageUrl;

    @Schema(description = "Display order of the image, lower values are shown first", example = "0")
    private Integer sortOrder;
}
