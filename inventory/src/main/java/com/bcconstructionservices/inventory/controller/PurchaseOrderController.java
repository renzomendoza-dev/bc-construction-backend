package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionsResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.PurchaseOrderService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for purchase orders — placed with a supplier before
 * anything has physically arrived, an earlier stage than PurchaseReceipt
 * (see POST /api/purchase-receipts, which stays the way stock actually
 * enters inventory).
 */
@RestController
@RequestMapping(value = "/api/purchase-orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Purchase Orders", description = "Orders placed with suppliers, ahead of goods arriving")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    @Operation(
            summary = "Create a draft purchase order",
            description = "Records a purchase order and its line items as a draft — freely editable while "
                    + "DRAFT via PUT /{id}. lines is typically pre-filled from "
                    + "GET /api/purchase-orders/suggestions?supplierId=, but that's a frontend convenience only; "
                    + "this endpoint accepts any well-formed list regardless of where it came from."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft order created",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation (e.g. empty lines, "
                    + "non-positive quantity)",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found, or a line's itemId not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_ORDER_CREATE')")
    public ResponseEntity<PurchaseOrderResponse> createDraft(
            @Valid @RequestBody PurchaseOrderCreateRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a draft purchase order's notes and line items",
            description = "Full-replacement update, only while DRAFT (422 otherwise — once SUBMITTED, a "
                    + "supplier already has this order, so changing quantities without telling them is "
                    + "misleading). supplierId cannot be changed and is not part of this request body. notes is "
                    + "copied as given, including null (clearing it); lines entirely replaces the existing line "
                    + "items."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase order updated",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase order not found, or a line's itemId not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The order is not DRAFT and can no longer be edited",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_ORDER_EDIT')")
    public ResponseEntity<PurchaseOrderResponse> update(
            @Parameter(description = "Identifier of the purchase order to update", example = "12")
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        return ResponseEntity.ok(purchaseOrderService.update(id, request));
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "Submit a draft purchase order to the supplier",
            description = "DRAFT -> SUBMITTED, only while DRAFT (422 otherwise). Locks line items from this "
                    + "point — see PUT /{id}'s description for why."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase order submitted",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The order is not DRAFT and cannot be submitted again",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_ORDER_SUBMIT')")
    public ResponseEntity<PurchaseOrderResponse> submit(
            @Parameter(description = "Identifier of the purchase order to submit", example = "12")
            @PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.submit(id));
    }

    @PostMapping("/{id}/close")
    @Operation(
            summary = "Manually close a purchase order",
            description = "Terminates the order regardless of how much of it has been received — for when the "
                    + "remaining shortfall isn't coming (supplier discontinued an item, order was over-cautious, "
                    + "etc.). Allowed from any status except RECEIVED/CLOSED (422 — both are already terminal). "
                    + "Deliberately a separate terminal status from RECEIVED, not reused for it, so \"fully "
                    + "delivered\" and \"abandoned short\" stay distinguishable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase order closed",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The order is already RECEIVED or CLOSED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_ORDER_CLOSE')")
    public ResponseEntity<PurchaseOrderResponse> close(
            @Parameter(description = "Identifier of the purchase order to close", example = "12")
            @PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.close(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a draft purchase order",
            description = "Only a DRAFT order can be deleted (422 otherwise) — anything past that has already "
                    + "been submitted to the supplier. Independently rejected with 409 if one or more "
                    + "PurchaseReceipts already reference this order via purchaseOrderId — createPurchaseReceipt "
                    + "allows linking a receipt to a DRAFT order (not just an already-submitted one), so a "
                    + "still-DRAFT order can legitimately already have receipt history against it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Purchase order deleted"),
            @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "One or more PurchaseReceipts already reference "
                    + "this order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The order is not DRAFT and cannot be deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PURCHASE_ORDER_DELETE')")
    public void delete(
            @Parameter(description = "Identifier of the purchase order to delete", example = "12")
            @PathVariable Long id) {
        purchaseOrderService.delete(id);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a purchase order by id",
            description = "Retrieves a single purchase order, including its line items and, per line, "
                    + "receivedQuantity — the sum of that item's quantity across every CONFIRMED PurchaseReceipt "
                    + "created against this order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase order found",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PurchaseOrderResponse> getById(
            @Parameter(description = "Identifier of the purchase order to retrieve", example = "12")
            @PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getById(id));
    }

    @GetMapping
    @Operation(
            summary = "List purchase orders",
            description = "Returns a paged list of purchase orders, optionally filtered by supplier and/or status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of purchase orders",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<PurchaseOrderResponse>> search(
            @Parameter(description = "Filter by supplier id", example = "5")
            @RequestParam(required = false) Long supplierId,
            @Parameter(description = "Filter by status", example = "SUBMITTED")
            @RequestParam(required = false) PurchaseOrderStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(purchaseOrderService.search(supplierId, status, pageable));
    }

    @GetMapping("/suggestions")
    @Operation(
            summary = "Suggest line items for a new purchase order against a supplier",
            description = "Combines three sources: (1) shortfall items on TransferBatch lines currently "
                    + "AWAITING_PURCHASE, re-checked against current stock (not the stale moment the batch "
                    + "failed); (2) items at/below their reorder threshold (same data as "
                    + "GET /api/inventory/low-stock); (3) items on open (SUBMITTED/PARTIALLY_FULFILLED) "
                    + "MaterialRequest lines not yet fully dispatched. Quantities from multiple sources for the "
                    + "same item are summed, not deduplicated. Suggestions are NOT filtered to items linked to "
                    + "the supplier via ItemSupplier — every candidate is returned regardless, with "
                    + "linkedToSupplier telling you whether that link exists, so nothing is silently hidden. "
                    + "This is a starting point to edit before POST /api/purchase-orders, not a constraint — "
                    + "it's also the intended way to find a PARTIALLY_RECEIVED order's remaining shortfall, "
                    + "since that isn't auto-chained into a new order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggested line items",
                    content = @Content(schema = @Schema(implementation = PurchaseOrderSuggestionsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PurchaseOrderSuggestionsResponse> getSuggestions(
            @Parameter(description = "Identifier of the supplier to suggest items for", example = "5")
            @RequestParam Long supplierId) {
        return ResponseEntity.ok(purchaseOrderService.getSuggestions(supplierId));
    }
}
