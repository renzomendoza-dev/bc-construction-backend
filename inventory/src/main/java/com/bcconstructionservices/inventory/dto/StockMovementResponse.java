package com.bcconstructionservices.inventory.dto;

import com.bcconstructionservices.inventory.entity.MovementDirection;
import com.bcconstructionservices.inventory.entity.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response payload representing a recorded StockMovement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    @Schema(description = "Unique identifier of the stock movement", example = "301")
    private Long id;

    @Schema(description = "Identifier of the item involved in the movement", example = "1")
    private Long itemId;

    @Schema(description = "Name of the item involved in the movement", example = "Wireless Mouse")
    private String itemName;

    @Schema(description = "Identifier of the warehouse where the movement occurred", example = "1")
    private Long warehouseId;

    @Schema(description = "Identifier of the storage location moved from, if applicable", example = "7")
    private Long fromLocationId;

    @Schema(description = "Identifier of the storage location moved to, if applicable", example = "12")
    private Long toLocationId;

    @Schema(description = "Type of stock movement", example = "TRANSFER",
            allowableValues = {"IN", "OUT", "TRANSFER", "ADJUSTMENT"})
    private MovementType type;

    @Schema(description = "Net effect of this row on its OWN warehouseId's stock level: IN (increased), "
            + "OUT (decreased), or WITHIN (net-zero — an internal move between two locations in the same "
            + "warehouse; only occurs for a same-warehouse TRANSFER, the single row that sets both "
            + "fromLocationId and toLocationId). For a cross-warehouse TRANSFER, this is the reliable way to "
            + "tell the origin-side row from the destination-side row — fromLocationId/toLocationId "
            + "nullability alone is not, since the origin side can legitimately be null too (e.g. when the "
            + "debited quantity came from the no-location bucket).",
            example = "OUT", allowableValues = {"IN", "OUT", "WITHIN"})
    private MovementDirection direction;

    @Schema(description = "Quantity of stock involved in the movement", example = "50")
    private Integer quantity;

    @Schema(description = "Optional explanation for the movement", example = "Restocking from supplier delivery")
    private String reason;

    @Schema(description = "ID of the user who created this stock movement", example = "3")
    private Long createdBy;

    @Schema(description = "Full name of the user who created this stock movement", example = "Juan Dela Cruz")
    private String createdByName;

    @Schema(description = "Timestamp when the movement was recorded", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;
}
