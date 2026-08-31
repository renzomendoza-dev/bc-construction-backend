package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload representing a single line item on a TransferBatch.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLineItemResponse {

    @Schema(description = "Unique identifier of the transfer line item", example = "301")
    private Long id;

    @Schema(description = "Identifier of the transferred item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the transferred item", example = "Portland Cement 40kg")
    private String itemName;

    @Schema(description = "Counted/audited quantity expected at the origin, if this line represents a counted pull-out", example = "50")
    private Integer expectedQuantity;

    @Schema(description = "Quantity of the item actually transferred", example = "50")
    private Integer quantity;

    @Schema(description = "Optional free-text notes, e.g. condition remarks from counting", example = "2 bags damaged, excluded from count")
    private String notes;
}
