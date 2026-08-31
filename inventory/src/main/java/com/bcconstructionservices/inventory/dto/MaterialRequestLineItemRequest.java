package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for a single line item within a MaterialRequestCreateRequest.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestLineItemRequest {

    @NotNull
    @Schema(description = "Identifier of the item being requested", example = "1")
    private Long itemId;

    @NotNull
    @Positive(message = "Quantity requested must be greater than zero")
    @Schema(description = "Quantity of the item being requested", example = "50")
    private Integer quantityRequested;

    @Schema(description = "Optional free-text notes about this line", example = "Needed before Friday's pour")
    private String notes;
}
