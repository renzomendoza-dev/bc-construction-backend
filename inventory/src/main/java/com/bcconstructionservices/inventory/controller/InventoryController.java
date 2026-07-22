package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.LowStockItemResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.StockAdjustmentRequest;
import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.dto.StockTransferRequest;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.InventoryService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for tracking stock levels, recording adjustments and
 * transfers, and querying movement history.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Track stock levels, adjustments, transfers, and movement history")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/stock")
    @Operation(
            summary = "Get the current stock level for an exact item+warehouse+location",
            description = "Looks up a single InventoryStock row for the given item and warehouse, optionally "
                    + "narrowed to a specific storage location. Returns 404 if no stock has ever been recorded "
                    + "for that exact combination — this does not mean quantity is zero, it means the row itself "
                    + "doesn't exist yet."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock level found",
                    content = @Content(schema = @Schema(implementation = StockLevelResponse.class))),
            @ApiResponse(responseCode = "404", description = "No stock record exists for this item+warehouse"
                    + "+location combination",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StockLevelResponse> getStockLevel(
            @Parameter(description = "Identifier of the item", example = "1", required = true)
            @RequestParam Long itemId,
            @Parameter(description = "Identifier of the warehouse", example = "1", required = true)
            @RequestParam Long warehouseId,
            @Parameter(description = "Identifier of the storage location within the warehouse, if tracked",
                    example = "7")
            @RequestParam(required = false) Long locationId) {
        return ResponseEntity.ok(inventoryService.getStockLevel(itemId, warehouseId, locationId));
    }

    @GetMapping
    @Operation(
            summary = "List stock levels",
            description = "Returns a paged list of InventoryStock rows, optionally filtered by item and/or "
                    + "warehouse. Unlike getStockLevel, this does not require an exact location match."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of stock levels",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<StockLevelResponse>> listStock(
            @Parameter(description = "Filter by item id", example = "1")
            @RequestParam(required = false) Long itemId,
            @Parameter(description = "Filter by warehouse id", example = "1")
            @RequestParam(required = false) Long warehouseId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(inventoryService.listStock(itemId, warehouseId, pageable));
    }

    @GetMapping("/low-stock")
    @Operation(
            summary = "List items at or below their reorder threshold",
            description = "Returns every InventoryStock row where quantity is less than or equal to "
                    + "reorderThreshold, for rows that have a reorderThreshold configured. Rows with no "
                    + "reorderThreshold set are never included, regardless of quantity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Low-stock items",
                    content = @Content(schema = @Schema(implementation = LowStockItemResponse.class)))
    })
    public ResponseEntity<List<LowStockItemResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @PostMapping("/adjust")
    @Operation(
            summary = "Record a single-location stock adjustment",
            description = "Applies one of four movement types to a single item+warehouse+location: IN "
                    + "(increases quantity; if no stock row exists yet, one is created starting at 0 before the "
                    + "increase is applied), OUT (decreases quantity; rejected with 409 if it would drop the "
                    + "balance below zero), ADJUSTMENT (a manual increase, e.g. a cycle-count correction — this "
                    + "endpoint only supports upward adjustments, not downward ones), or TRANSFER (rarely used "
                    + "directly here — prefer POST /api/inventory/transfer, which also updates the destination "
                    + "in the same call). quantity must be greater than zero; requests with quantity <= 0 are "
                    + "rejected with 400."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adjustment applied",
                    content = @Content(schema = @Schema(implementation = StockMovementResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, quantity was <= 0, "
                    + "or the referenced item or warehouse is inactive",
                    content = {
                            @Content(schema = @Schema(implementation = ValidationErrorResponse.class)),
                            @Content(schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "404", description = "Item, warehouse, or storage location not found, or "
                    + "(for OUT/ADJUSTMENT) no existing stock row to adjust",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock: an OUT adjustment would drop "
                    + "the balance below zero",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StockMovementResponse> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(request));
    }

    @PostMapping("/transfer")
    @Operation(
            summary = "Transfer stock between two item+warehouse+location combinations",
            description = "Decrements the source location and increments the destination location for the same "
                    + "item, writing one or two TRANSFER-type movement records depending on whether source and "
                    + "destination share a warehouse. The source must already have a sufficient balance — this "
                    + "is rejected with 409 if it would drop below zero, the same rule as an OUT adjustment. "
                    + "Source and destination must differ (by warehouse and/or location); an identical source "
                    + "and destination is rejected with 400. quantity must be greater than zero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer applied",
                    content = @Content(schema = @Schema(implementation = StockMovementResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, quantity was <= 0, "
                    + "source and destination were identical, or the referenced item or warehouse is inactive",
                    content = {
                            @Content(schema = @Schema(implementation = ValidationErrorResponse.class)),
                            @Content(schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "404", description = "Item, warehouse, or storage location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock at the source location to "
                    + "cover the transfer",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<StockMovementResponse>> transferStock(@Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.ok(inventoryService.transferStock(request));
    }

    @GetMapping("/movements")
    @Operation(
            summary = "Get stock movement history",
            description = "Returns a paged, chronologically-filterable audit trail of every stock movement "
                    + "(IN, OUT, TRANSFER, ADJUSTMENT), optionally filtered by item, warehouse, and/or a "
                    + "createdAt date range. fromDate and toDate are both inclusive of the full day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of stock movements",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<StockMovementResponse>> getMovementHistory(
            @Parameter(description = "Filter by item id", example = "1")
            @RequestParam(required = false) Long itemId,
            @Parameter(description = "Filter by warehouse id", example = "1")
            @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "Only include movements on or after this date (inclusive)", example = "2026-07-01")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "Only include movements on or before this date (inclusive)", example = "2026-07-18")
            @RequestParam(required = false) LocalDate toDate,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getMovementHistory(itemId, warehouseId, fromDate, toDate, pageable));
    }
}