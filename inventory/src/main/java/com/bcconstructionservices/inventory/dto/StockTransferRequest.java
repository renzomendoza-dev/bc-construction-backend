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
 * Request payload for moving stock of an Item from one warehouse/location
 * to another. Always recorded as a movement of type TRANSFER.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {

    @NotNull
    @Schema(description = "Identifier of the item being transferred", example = "1")
    private Long itemId;

    @NotNull
    @Schema(description = "Identifier of the warehouse the stock is being moved from", example = "1")
    private Long fromWarehouseId;

    @Schema(description = "Identifier of the storage location being moved from, if tracked", example = "7")
    private Long fromLocationId;

    @NotNull
    @Schema(description = "Identifier of the warehouse the stock is being moved to", example = "2")
    private Long toWarehouseId;

    @Schema(description = "Identifier of the storage location being moved to, if tracked", example = "12")
    private Long toLocationId;

    @NotNull
    @Positive(message = "Quantity must be greater than zero")
    @Schema(description = "Quantity of stock to transfer", example = "50")
    private Integer quantity;
}
