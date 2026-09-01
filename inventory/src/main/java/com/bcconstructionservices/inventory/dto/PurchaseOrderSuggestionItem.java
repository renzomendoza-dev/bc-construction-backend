package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One suggested line item for a new purchase order — a starting point to edit, not a "
        + "hard constraint")
public class PurchaseOrderSuggestionItem {

    @Schema(example = "42")
    private Long itemId;

    @Schema(example = "Portland Cement 40kg")
    private String itemName;

    @Schema(example = "CEM-40KG")
    private String itemSku;

    @Schema(description = "Suggested quantity — the sum of whatever each contributing source independently "
            + "calls for", example = "150")
    private Integer suggestedQuantity;

    @Schema(description = "Whether this item has an existing ItemSupplier link to the queried supplier. Not "
            + "used to filter suggestions (see GET /api/purchase-orders/suggestions's description) — purely a "
            + "signal for the frontend to display", example = "true")
    private boolean linkedToSupplier;

    @Schema(description = "Which source(s) suggested this item")
    private List<PurchaseOrderSuggestionSource> sources;
}
