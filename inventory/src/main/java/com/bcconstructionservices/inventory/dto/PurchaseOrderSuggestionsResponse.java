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
@Schema(description = "Suggested line items for a new purchase order against a given supplier")
public class PurchaseOrderSuggestionsResponse {

    @Schema(example = "5")
    private Long supplierId;

    @Schema(description = "Suggested items, sorted by suggested quantity descending")
    private List<PurchaseOrderSuggestionItem> suggestions;
}
