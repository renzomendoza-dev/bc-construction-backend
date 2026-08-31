package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.exception.ValidationErrorResponse;
import com.bcconstructionservices.inventory.service.MaterialRequestService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for a site's requests for materials to be pulled from a
 * MAIN warehouse.
 */
@RestController
@RequestMapping(value = "/api/inventory/material-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Material Requests", description = "Request materials from a site to be fulfilled from a warehouse")
public class MaterialRequestController {

    private final MaterialRequestService materialRequestService;

    @PostMapping
    @Operation(
            summary = "Create a material request",
            description = "Records a site's request for materials, along with its line items. siteWarehouseId "
                    + "must reference a Warehouse of type SITE — a request against a MAIN warehouse is rejected "
                    + "with 400. This does not move stock itself: fulfillment happens by creating a transfer "
                    + "batch with sourceMaterialRequestId set to this request's id and submitting it "
                    + "(see POST /api/inventory/transfer-batches), which is also what updates this request's "
                    + "status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Material request created",
                    content = @Content(schema = @Schema(implementation = MaterialRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, or "
                    + "siteWarehouseId does not reference a SITE-type warehouse",
                    content = {
                            @Content(schema = @Schema(implementation = ValidationErrorResponse.class)),
                            @Content(schema = @Schema(implementation = ErrorResponse.class))
                    }),
            @ApiResponse(responseCode = "404", description = "Site warehouse, or an item referenced by a line, "
                    + "not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('MATERIAL_REQUEST_CREATE')")
    public ResponseEntity<MaterialRequestResponse> create(
            @Valid @RequestBody MaterialRequestCreateRequest request) {
        MaterialRequestResponse response = materialRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a material request",
            description = "Replaces dateNeeded, notes, and line items on an existing request. This is a full "
                    + "replacement, not a partial patch: dateNeeded/notes are copied as given, including null "
                    + "(clearing the field), and lines entirely replace the existing line items — a line not "
                    + "present in this call is deleted, same mental model as re-saving a form. siteWarehouseId "
                    + "cannot be changed after creation and is not part of this request body. Rejected with 422 "
                    + "once status is PARTIALLY_FULFILLED or FULFILLED — i.e. once at least one submitted "
                    + "transfer batch has already moved real stock against this request. A transfer batch that "
                    + "merely references this request via sourceMaterialRequestId but hasn't been submitted yet "
                    + "does NOT lock it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Material request updated",
                    content = @Content(schema = @Schema(implementation = MaterialRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Material request not found, or an item referenced "
                    + "by a line not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The request can no longer be edited — status is "
                    + "already PARTIALLY_FULFILLED or FULFILLED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('MATERIAL_REQUEST_EDIT')")
    public ResponseEntity<MaterialRequestResponse> update(
            @Parameter(description = "Identifier of the material request to update", example = "14")
            @PathVariable Long id,
            @Valid @RequestBody MaterialRequestUpdateRequest request) {
        return ResponseEntity.ok(materialRequestService.update(id, request));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a material request by id",
            description = "Retrieves a single material request, including its line items and fulfillment status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Material request found",
                    content = @Content(schema = @Schema(implementation = MaterialRequestResponse.class))),
            @ApiResponse(responseCode = "404", description = "Material request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MaterialRequestResponse> getById(
            @Parameter(description = "Identifier of the material request to retrieve", example = "14")
            @PathVariable Long id) {
        return ResponseEntity.ok(materialRequestService.getById(id));
    }

    @GetMapping
    @Operation(
            summary = "List material requests",
            description = "Returns a paged list of material requests, optionally filtered by site warehouse "
                    + "and/or status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of material requests",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<MaterialRequestResponse>> search(
            @Parameter(description = "Filter by site warehouse id", example = "2")
            @RequestParam(required = false) Long siteWarehouseId,
            @Parameter(description = "Filter by status", example = "SUBMITTED")
            @RequestParam(required = false) MaterialRequestStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(materialRequestService.search(siteWarehouseId, status, pageable));
    }
}
