package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.PurchaseHistoryResponse;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.FileStorageService;
import com.bcconstructionservices.inventory.service.PurchaseReceiptService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * REST endpoints for recording purchase receipts and applying them to inventory.
 */
@RestController
@RequestMapping(value = "/api/purchase-receipts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Purchase Receipts", description = "Record scanned purchase receipts and apply them to inventory")
public class PurchaseReceiptController {

    private final PurchaseReceiptService purchaseReceiptService;
    private final FileStorageService fileStorageService;


    @PostMapping
    @Operation(
            summary = "Create a draft purchase receipt",
            description = "Records a purchase receipt and its line items as a draft. lineTotal and totalAmount "
                    + "are always computed from quantity/unitCost. This step does NOT affect stock — the receipt "
                    + "must be confirmed via POST /{receiptId}/confirm before it changes inventory. If "
                    + "fulfillsTransferBatchId is set, the referenced batch must currently be status "
                    + "AWAITING_PURCHASE (422 if not) — confirming this receipt later flips that batch back to "
                    + "DRAFT (see POST /api/inventory/transfer-batches/{id}/submit)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft receipt created",
                    content = @Content(schema = @Schema(implementation = PurchaseReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier, warehouse, or fulfillsTransferBatchId "
                    + "not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The receipt has no lines, a line references an "
                    + "item id that doesn't exist, or fulfillsTransferBatchId references a batch that isn't "
                    + "status AWAITING_PURCHASE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_RECEIPT_CREATE')")
    public ResponseEntity<PurchaseReceiptResponse> createPurchaseReceipt(
            @Valid @RequestBody PurchaseReceiptCreateRequest request) {
        PurchaseReceiptResponse response = purchaseReceiptService.createPurchaseReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{receiptId}/confirm")
    @Operation(
            summary = "Confirm a draft purchase receipt",
            description = "Applies a draft receipt to inventory: this is the step that actually changes stock "
                    + "quantities. For each line, an IN-type stock movement is recorded against the receipt's "
                    + "warehouse, and that item+supplier's most recent unit cost is updated to the line's "
                    + "unitCost. The whole operation is one transaction — if any line fails, nothing is applied. "
                    + "A receipt can only be confirmed once; confirming an already-confirmed receipt is rejected "
                    + "with 422. If fulfillsTransferBatchId was set on this receipt and that batch is still "
                    + "AWAITING_PURCHASE, confirming also flips that batch back to DRAFT — resubmitting it "
                    + "(POST /api/inventory/transfer-batches/{id}/submit) remains a separate, manual step."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt confirmed and applied to inventory",
                    content = @Content(schema = @Schema(implementation = PurchaseReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "The item or warehouse on one of the receipt's "
                    + "lines has since become inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase receipt not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The receipt has already been confirmed, or has no "
                    + "lines to confirm",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PURCHASE_RECEIPT_CONFIRM')")
    public ResponseEntity<PurchaseReceiptResponse> confirmPurchaseReceipt(
            @Parameter(description = "Identifier of the purchase receipt to confirm", example = "20")
            @PathVariable Long receiptId) {
        return ResponseEntity.ok(purchaseReceiptService.confirmPurchaseReceipt(receiptId));
    }

    @GetMapping("/{receiptId}")
    @Operation(
            summary = "Get a purchase receipt by id",
            description = "Retrieves a single purchase receipt, including its line items and confirmation status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase receipt found",
                    content = @Content(schema = @Schema(implementation = PurchaseReceiptResponse.class))),
            @ApiResponse(responseCode = "404", description = "Purchase receipt not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PurchaseReceiptResponse> getPurchaseReceiptById(
            @Parameter(description = "Identifier of the purchase receipt to retrieve", example = "20")
            @PathVariable Long receiptId) {
        return ResponseEntity.ok(purchaseReceiptService.getPurchaseReceiptById(receiptId));
    }

    @GetMapping
    @Operation(
            summary = "List purchase receipts",
            description = "Returns a paged list of purchase receipts (both draft and confirmed), optionally "
                    + "filtered by supplier, a purchaseDate range, and/or fulfillsTransferBatchId. fromDate and "
                    + "toDate are both inclusive. fulfillsTransferBatchId is how to find \"the receipt resolving "
                    + "batch #Y\" from a blocked TransferBatch — TransferBatchResponse doesn't embed the reverse "
                    + "reference itself."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of purchase receipts",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<PurchaseReceiptResponse>> listPurchaseReceipts(
            @Parameter(description = "Filter by supplier id", example = "5")
            @RequestParam(required = false) Long supplierId,
            @Parameter(description = "Only include receipts purchased on or after this date (inclusive)",
                    example = "2026-07-01")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "Only include receipts purchased on or before this date (inclusive)",
                    example = "2026-07-18")
            @RequestParam(required = false) LocalDate toDate,
            @Parameter(description = "Filter to the receipt(s) that fulfill this transfer batch id", example = "42")
            @RequestParam(required = false) Long fulfillsTransferBatchId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(purchaseReceiptService.listPurchaseReceipts(
                supplierId, fromDate, toDate, fulfillsTransferBatchId, pageable));
    }

    @GetMapping("/item/{itemId}/history")
    @Operation(
            summary = "Get purchase history for an item",
            description = "Returns every purchase receipt line recorded for this item across all suppliers and "
                    + "receipts, ordered by purchase date descending."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase history found",
                    content = @Content(schema = @Schema(implementation = PurchaseHistoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PurchaseHistoryResponse> getPurchaseHistoryForItem(
            @Parameter(description = "Identifier of the item to retrieve purchase history for", example = "1")
            @PathVariable Long itemId) {
        return ResponseEntity.ok(purchaseReceiptService.getPurchaseHistoryForItem(itemId));
    }

    @Operation(summary = "Upload or replace the scanned image for a purchase receipt")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image uploaded and linked to the receipt"),
            @ApiResponse(responseCode = "400", description = "Invalid file (empty, wrong type, or too large)"),
            @ApiResponse(responseCode = "404", description = "Receipt not found")
    })
    @PostMapping(
            value = "/{receiptId}/image/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PURCHASE_RECEIPT_CREATE')")
    public ResponseEntity<PurchaseReceiptResponse> uploadReceiptImage(
            @PathVariable Long receiptId,
            @RequestParam("image") MultipartFile image) {

        // 1. Validate + persist the file to disk, getting back the stored URL.
        //    FileStorageService throws InvalidFileException (-> 400) on an
        //    empty/oversized/wrong-type file before anything else happens.
        String imageUrl = fileStorageService.storeFile(image, "receipts");

        // 2. Update the receipt's imageUrl. The service throws
        //    ResourceNotFoundException (-> 404) if receiptId doesn't exist, and
        //    (see PurchaseReceiptService change) deletes any PREVIOUS image file
        //    after the update commits, so replacing an image doesn't leave the
        //    old file orphaned on disk.
        //
        //    ORDERING NOTE: the new file is written before we know the receipt
        //    exists, so a 404 here leaves an orphaned new file on disk. If that
        //    matters, either verify existence before storing (extra service
        //    call) or catch + fileStorageService.deleteFile(imageUrl) here
        //    before rethrowing. Left as the simple path since a 404 on upload
        //    is an unusual client error; flagging the tradeoff explicitly.
        PurchaseReceiptResponse response =
                purchaseReceiptService.updateReceiptImage(receiptId, imageUrl);

        return ResponseEntity.ok(response);
    }
}