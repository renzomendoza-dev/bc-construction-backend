package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for a single line item within a TransferBatchCreateRequest.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLineItemRequest {

    @NotNull
    @Schema(description = "Identifier of the item being transferred", example = "1")
    private Long itemId;

    @PositiveOrZero
    @Schema(description = "Counted/audited quantity expected at the origin, if this line represents a "
            + "counted pull-out; omitted for a straight dispatch line with nothing to count against",
            example = "50")
    private Integer expectedQuantity;

    @NotNull
    @Positive(message = "Quantity must be greater than zero")
    @Schema(description = "Quantity of the item actually being transferred", example = "50")
    private Integer quantity;

    @Schema(description = "Optional free-text notes, e.g. condition remarks from counting", example = "2 bags damaged, excluded from count")
    private String notes;
}
