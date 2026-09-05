package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.TransferBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for creating and submitting warehouse/site transfer batches.
 */
@RestController
@RequestMapping(value = "/api/inventory/transfer-batches", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Transfer Batches", description = "Move items between warehouses and sites as a batch")
public class TransferBatchController {

    private final TransferBatchService transferBatchService;

    @PostMapping
    @Operation(
            summary = "Create a draft transfer batch",
            description = "Records a transfer batch and its line items as a draft. This step does NOT move "
                    + "stock — it's a plan/count only. The batch must be submitted via POST /{id}/submit before "
                    + "it changes inventory. originWarehouseId and destinationWarehouseId must reference "
                    + "different, active warehouses (a \"site\" is just a Warehouse with type SITE)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft batch created",
                    content = @Content(schema = @Schema(implementation = TransferBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, origin and "
                    + "destination warehouse were the same, or one of them is inactive",
                    content = {
                            @Content(schema = @Schema(implementation = ValidationErrorResponse.class)),
                            @Content(schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "404", description = "Origin/destination warehouse, or an item referenced "
                    + "by a line, not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('TRANSFER_BATCH_CREATE')")
    public ResponseEntity<TransferBatchResponse> createDraft(
            @Valid @RequestBody TransferBatchCreateRequest request) {
        TransferBatchResponse response = transferBatchService.createDraft(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "Submit a draft transfer batch",
            description = "Applies a draft batch to inventory: this is the step that actually moves stock. "
                    + "For each line item, InventoryService.transferWarehouseStock is called to move that "
                    + "quantity from the batch's origin warehouse to its destination warehouse — checked and "
                    + "debited against the origin warehouse's TOTAL balance for the item (summed across every "
                    + "storage location plus the no-location bucket), not one specific location, since a batch "
                    + "line only ever specifies warehouses. If the total is spread across more than one "
                    + "location, they're drained in a fixed order (no-location bucket first, then each location "
                    + "by id ascending) and each debited location gets its own accurate movement record; the "
                    + "destination side always lands in the destination warehouse's no-location bucket. The "
                    + "whole operation is one transaction — if any line fails (e.g. insufficient stock), nothing "
                    + "is applied and the batch stays in its prior state. If this batch fulfills a MaterialRequest "
                    + "(sourceMaterialRequestId was set on creation), that request's status is updated to "
                    + "FULFILLED or PARTIALLY_FULFILLED depending on whether every requested quantity was covered. "
                    + "If the failure was specifically insufficient stock (409), this batch's status is also set "
                    + "to AWAITING_PURCHASE — create a PurchaseReceipt with fulfillsTransferBatchId set to this "
                    + "batch's id for the shortfall item(s); confirming that receipt flips this batch back to "
                    + "DRAFT so it can be resubmitted (see POST /api/purchase-receipts)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch submitted and applied to inventory",
                    content = @Content(schema = @Schema(implementation = TransferBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "The batch has no line items, or an item/warehouse "
                    + "on one of its lines has since become inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transfer batch not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock at the origin warehouse to "
                    + "cover one of the batch's lines. The batch's status is also set to AWAITING_PURCHASE.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('TRANSFER_BATCH_SUBMIT')")
    public ResponseEntity<TransferBatchResponse> submit(
            @Parameter(description = "Identifier of the transfer batch to submit", example = "42")
            @PathVariable Long id) {
        return ResponseEntity.ok(transferBatchService.submit(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a draft transfer batch",
            description = "Only a DRAFT batch can be deleted (422 otherwise) — SUBMITTED and COMPLETED batches "
                    + "have already moved stock, and AWAITING_PURCHASE batches are actively referenced by a "
                    + "fulfilling PurchaseReceipt. If this batch has sourceMaterialRequestId set, deleting it "
                    + "does NOT touch that MaterialRequest — the request is simply left with no draft transfer "
                    + "against it, same as if this batch had never been created."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Batch deleted"),
            @ApiResponse(responseCode = "404", description = "Transfer batch not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The batch is not DRAFT and cannot be deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('TRANSFER_BATCH_DELETE')")
    public void delete(
            @Parameter(description = "Identifier of the transfer batch to delete", example = "42")
            @PathVariable Long id) {
        transferBatchService.delete(id);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a transfer batch by id",
            description = "Retrieves a single transfer batch, including its line items and status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer batch found",
                    content = @Content(schema = @Schema(implementation = TransferBatchResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transfer batch not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransferBatchResponse> getById(
            @Parameter(description = "Identifier of the transfer batch to retrieve", example = "42")
            @PathVariable Long id) {
        return ResponseEntity.ok(transferBatchService.getById(id));
    }

    @GetMapping
    @Operation(
            summary = "List transfer batches",
            description = "Returns a paged list of transfer batches, optionally filtered by origin warehouse, "
                    + "destination warehouse, and/or status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of transfer batches",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<TransferBatchResponse>> search(
            @Parameter(description = "Filter by origin warehouse id", example = "1")
            @RequestParam(required = false) Long originWarehouseId,
            @Parameter(description = "Filter by destination warehouse id", example = "2")
            @RequestParam(required = false) Long destinationWarehouseId,
            @Parameter(description = "Filter by status", example = "COMPLETED")
            @RequestParam(required = false) TransferBatchStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(
                transferBatchService.search(originWarehouseId, destinationWarehouseId, status, pageable));
    }
}
