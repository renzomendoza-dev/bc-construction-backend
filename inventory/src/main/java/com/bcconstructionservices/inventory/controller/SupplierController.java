package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.ItemSupplierRequest;
import com.bcconstructionservices.inventory.dto.ItemSupplierResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.SupplierCreateRequest;
import com.bcconstructionservices.inventory.dto.SupplierResponse;
import com.bcconstructionservices.inventory.dto.SupplierUpdateRequest;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for managing suppliers and item-supplier relationships.
 */
@RestController
@RequestMapping(value = "/api/suppliers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Manage suppliers and item-supplier relationships")
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @Operation(
            summary = "Create a new supplier",
            description = "Creates a new supplier record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Supplier created",
                    content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class)))
    })
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierCreateRequest request) {
        SupplierResponse response = supplierService.createSupplier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{supplierId}")
    @Operation(
            summary = "Update an existing supplier",
            description = "Updates one or more fields of an existing supplier, including its active status. Only "
                    + "non-null fields present in the request body are applied."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier updated",
                    content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SupplierResponse> updateSupplier(
            @Parameter(description = "Identifier of the supplier to update", example = "5")
            @PathVariable Long supplierId,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ResponseEntity.ok(supplierService.updateSupplier(supplierId, request));
    }

    @GetMapping("/{supplierId}")
    @Operation(
            summary = "Get a supplier by id",
            description = "Retrieves a single supplier by its identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Supplier found",
                    content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SupplierResponse> getSupplierById(
            @Parameter(description = "Identifier of the supplier to retrieve", example = "5")
            @PathVariable Long supplierId) {
        return ResponseEntity.ok(supplierService.getSupplierById(supplierId));
    }

    @GetMapping
    @Operation(
            summary = "List suppliers",
            description = "Returns a paged list of suppliers, optionally filtered by active status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of suppliers",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<SupplierResponse>> listSuppliers(
            @Parameter(description = "Filter by active status", example = "true")
            @RequestParam(required = false) Boolean active,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(supplierService.listSuppliers(active, pageable));
    }

    @PatchMapping("/{supplierId}/deactivate")
    @Operation(
            summary = "Deactivate a supplier",
            description = "Soft-disables a supplier by setting active to false. The supplier and its history are "
                    + "preserved, not deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Supplier deactivated"),
            @ApiResponse(responseCode = "404", description = "Supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deactivateSupplier(
            @Parameter(description = "Identifier of the supplier to deactivate", example = "5")
            @PathVariable Long supplierId) {
        supplierService.deactivateSupplier(supplierId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/link-item")
    @Operation(
            summary = "Link an item to a supplier",
            description = "Creates the item-supplier relationship if it doesn't already exist for this item+"
                    + "supplier pair, or updates the existing link's supplierSku/unitCost in place otherwise. "
                    + "Both the item and supplier must be active."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Link created or updated",
                    content = @Content(schema = @Schema(implementation = ItemSupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, or the referenced "
                    + "item or supplier is inactive",
                    content = {
                            @Content(schema = @Schema(implementation = ValidationErrorResponse.class)),
                            @Content(schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "404", description = "Item or supplier not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ItemSupplierResponse> linkItemToSupplier(@Valid @RequestBody ItemSupplierRequest request) {
        return ResponseEntity.ok(supplierService.linkItemToSupplier(request));
    }

    @GetMapping("/for-item/{itemId}")
    @Operation(
            summary = "List suppliers for an item",
            description = "Returns every supplier this item is linked to, including the supplier-specific SKU "
                    + "and most recent unit cost for each."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item-supplier links found",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemSupplierResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Item not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ItemSupplierResponse>> getSuppliersForItem(
            @Parameter(description = "Identifier of the item to look up suppliers for", example = "1")
            @PathVariable Long itemId) {
        return ResponseEntity.ok(supplierService.getSuppliersForItem(itemId));
    }
}