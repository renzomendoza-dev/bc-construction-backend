package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One line item on a purchase order")
public class PurchaseOrderLineRequest {

    @NotNull
    @Positive
    @Schema(description = "Identifier of the item being ordered", example = "42")
    private Long itemId;

    @NotNull
    @Positive
    @Schema(description = "Quantity being ordered", example = "100")
    private Integer quantity;

    @Size(max = 500)
    @Schema(description = "Optional free-text notes about this line", example = "Confirm lead time with supplier")
    private String notes;
}
