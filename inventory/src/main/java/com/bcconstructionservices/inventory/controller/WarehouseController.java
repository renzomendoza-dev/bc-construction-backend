package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.StorageLocationRequest;
import com.bcconstructionservices.inventory.dto.StorageLocationResponse;
import com.bcconstructionservices.inventory.dto.WarehouseCreateRequest;
import com.bcconstructionservices.inventory.dto.WarehouseResponse;
import com.bcconstructionservices.inventory.dto.WarehouseUpdateRequest;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.WarehouseService;
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
 * REST endpoints for managing warehouses and the storage locations within them.
 */
@RestController
@RequestMapping(value = "/api/warehouses", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Manage warehouses and storage locations")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @Operation(
            summary = "Create a new warehouse",
            description = "Creates a new warehouse. Fails with 409 if the warehouse code is already in use."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Warehouse created",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A warehouse with this code already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseCreateRequest request) {
        WarehouseResponse response = warehouseService.createWarehouse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{warehouseId}")
    @Operation(
            summary = "Update an existing warehouse",
            description = "Updates a warehouse's name and/or active status. Only non-null fields present in the "
                    + "request body are applied. The warehouse code is immutable and cannot be changed here."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Warehouse updated",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @Parameter(description = "Identifier of the warehouse to update", example = "1")
            @PathVariable Long warehouseId,
            @Valid @RequestBody WarehouseUpdateRequest request) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(warehouseId, request));
    }

    @GetMapping
    @Operation(
            summary = "List warehouses",
            description = "Returns a paged list of warehouses, optionally filtered by active status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of warehouses",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<WarehouseResponse>> listWarehouses(
            @Parameter(description = "Filter by active status", example = "true")
            @RequestParam(required = false) Boolean active,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(warehouseService.listWarehouses(active, pageable));
    }

    @PatchMapping("/{warehouseId}/deactivate")
    @Operation(
            summary = "Deactivate a warehouse",
            description = "Soft-disables a warehouse by setting active to false. The warehouse, its storage "
                    + "locations, and its history are preserved, not deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Warehouse deactivated"),
            @ApiResponse(responseCode = "404", description = "Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deactivateWarehouse(
            @Parameter(description = "Identifier of the warehouse to deactivate", example = "1")
            @PathVariable Long warehouseId) {
        warehouseService.deactivateWarehouse(warehouseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/locations")
    @Operation(
            summary = "Add a storage location to a warehouse",
            description = "Creates a new storage location (e.g. a rack or box) within a warehouse. Fails with 409 "
                    + "if the code is already in use within that same warehouse; the same code may be reused "
                    + "across different warehouses."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Storage location created",
                    content = @Content(schema = @Schema(implementation = StorageLocationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A storage location with this code already exists "
                    + "in the given warehouse",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StorageLocationResponse> addStorageLocation(
            @Valid @RequestBody StorageLocationRequest request) {
        StorageLocationResponse response = warehouseService.addStorageLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{warehouseId}/locations")
    @Operation(
            summary = "List storage locations for a warehouse",
            description = "Returns storage locations defined within the given warehouse. Omitting active defaults "
                    + "to true (active only) rather than returning everything — pass active=false to see inactive "
                    + "locations instead. There is currently no single call that returns both together."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Storage locations found",
                    content = @Content(schema = @Schema(implementation = StorageLocationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<StorageLocationResponse>> listStorageLocations(
            @Parameter(description = "Identifier of the warehouse to list storage locations for", example = "1")
            @PathVariable Long warehouseId,
            @Parameter(description = "Filter by active status; defaults to true (active only) when omitted", example = "true")
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(warehouseService.listStorageLocations(warehouseId, active));
    }

    @PatchMapping("/locations/{locationId}/deactivate")
    @Operation(
            summary = "Deactivate a storage location (soft delete) — does not affect existing stock or movement history referencing it",
            description = "Soft-disables a storage location by setting active to false. Historical InventoryStock "
                    + "and StockMovement rows referencing this location are left untouched; deactivation only "
                    + "affects whether the location should still be offered going forward, e.g. in \"pick a "
                    + "location\" UI pickers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Storage location deactivated"),
            @ApiResponse(responseCode = "404", description = "Storage location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deactivateStorageLocation(
            @Parameter(description = "Identifier of the storage location to deactivate", example = "7")
            @PathVariable Long locationId) {
        warehouseService.deactivateStorageLocation(locationId);
        return ResponseEntity.noContent().build();
    }
}