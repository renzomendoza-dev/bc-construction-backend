package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload representing a single line item on a MaterialRequest.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestLineItemResponse {

    @Schema(description = "Unique identifier of the material request line item", example = "88")
    private Long id;

    @Schema(description = "Identifier of the requested item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the requested item", example = "Portland Cement 40kg")
    private String itemName;

    @Schema(description = "Quantity of the item requested", example = "50")
    private Integer quantityRequested;

    @Schema(description = "Optional free-text notes about this line", example = "Needed before Friday's pour")
    private String notes;
}
