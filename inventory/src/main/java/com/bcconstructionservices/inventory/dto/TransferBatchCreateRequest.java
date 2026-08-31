package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for creating a new TransferBatch (as a draft) along with
 * its line items.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferBatchCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the warehouse (or site) stock is being moved from", example = "1")
    private Long originWarehouseId;

    @NotNull
    @Schema(description = "Identifier of the warehouse (or site) stock is being moved to", example = "2")
    private Long destinationWarehouseId;

    @Schema(description = "Identifier of the MaterialRequest this batch fulfills, if any; omit for a "
            + "straight pull-out with no originating request", example = "14")
    private Long sourceMaterialRequestId;

    @Schema(description = "Optional free-text notes about the transfer", example = "Weekly resupply for Sta. Maria site")
    private String notes;

    @NotEmpty(message = "A transfer batch must have at least one line")
    @Valid
    @Schema(description = "Line items to transfer; at least one is required")
    private List<TransferLineItemRequest> lines;
}
