package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for recording a single-location stock change: a receipt (IN),
 * a deduction (OUT), or a manual correction (ADJUSTMENT). For moving stock between
 * two warehouses or locations, use StockTransferRequest instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {

    @NotNull
    @Schema(description = "Identifier of the item whose stock is being adjusted", example = "1")
    private Long itemId;

    @NotNull
    @Schema(description = "Identifier of the warehouse where the adjustment takes place", example = "1")
    private Long warehouseId;

    @Schema(description = "Identifier of the storage location within the warehouse, if tracked", example = "7")
    private Long locationId;

    @NotNull
    @Positive(message = "Quantity must be greater than zero")
    @Schema(description = "Quantity being adjusted; the direction is determined by type, not the sign", example = "25")
    private Integer quantity;

    @NotNull
    @Schema(description = "Type of stock movement being recorded", example = "ADJUSTMENT",
            allowableValues = {"IN", "OUT", "TRANSFER", "ADJUSTMENT"})
    private MovementType type;

    @Size(max = 255)
    @Schema(description = "Optional explanation for the adjustment", example = "Cycle count correction")
    private String reason;
}
