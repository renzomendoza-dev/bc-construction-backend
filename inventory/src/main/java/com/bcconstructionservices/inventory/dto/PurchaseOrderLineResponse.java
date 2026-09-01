package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "One line item on a purchase order, including how much of it has been received so far")
public class PurchaseOrderLineResponse {

    @Schema(example = "301")
    private Long id;

    @Schema(example = "42")
    private Long itemId;

    @Schema(example = "Portland Cement 40kg")
    private String itemName;

    @Schema(example = "CEM-40KG")
    private String itemSku;

    @Schema(description = "Quantity ordered on this line", example = "100")
    private Integer quantity;

    @Schema(description = "Sum of this item's quantity across every CONFIRMED PurchaseReceipt created against "
            + "this purchase order — 0 until at least one receipt has been confirmed", example = "60")
    private Integer receivedQuantity;

    @Schema(example = "Confirm lead time with supplier")
    private String notes;
}
