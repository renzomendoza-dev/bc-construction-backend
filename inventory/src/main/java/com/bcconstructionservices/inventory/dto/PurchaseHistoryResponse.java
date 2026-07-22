package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response payload representing the full purchase history of a single Item
 * across all suppliers and receipts.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseHistoryResponse {

    @Schema(description = "Identifier of the item", example = "1")
    private Long itemId;

    @Schema(description = "Name of the item", example = "Wireless Mouse")
    private String itemName;

    @Schema(description = "Chronological list of past purchases of this item")
    private List<PurchaseHistoryEntry> purchases;
}
