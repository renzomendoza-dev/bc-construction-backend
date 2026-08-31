package com.bcconstructionservices.equipment.controller;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.dto.ErrorResponse;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.equipment.service.EquipmentAssignmentBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for batch equipment assignment (checkout) and return
 * (check-in) — process many pieces of equipment in one action instead of
 * one-by-one via /api/equipment/{id}/checkout|checkin, which stay available
 * for one-off cases.
 */
@RestController
@RequestMapping("/api/equipment/assignment-batches")
@RequiredArgsConstructor
@Tag(name = "Equipment Assignment Batches", description = "Batch equipment checkout (assign-out) and check-in (return)")
@SecurityRequirement(name = "bearerAuth")
public class EquipmentAssignmentBatchController {

    private final EquipmentAssignmentBatchService equipmentAssignmentBatchService;

    @PostMapping
    @Operation(
            summary = "Create a draft equipment assignment batch",
            description = "Records a batch of equipment assignments (checkouts), site-to-site transfers, or "
                    + "returns (check-ins) as a draft. This step does NOT change any equipment's status — the "
                    + "batch must be submitted via POST /{id}/submit before it takes effect. destinationWarehouseId's "
                    + "type only determines whether holderId is required, not which of assign-out/transfer this "
                    + "turns out to be per line: a SITE-type destination requires holderId (it covers BOTH "
                    + "assign-out, for equipment that's currently AVAILABLE, and a direct transfer, for equipment "
                    + "that's already CHECKED_OUT/IN_USE at a different site — which one a given line actually is "
                    + "depends on that equipment's status at submit time, not anything checked here); a MAIN-type "
                    + "destination is always a return and holderId must be omitted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft batch created",
                    content = @Content(schema = @Schema(implementation = EquipmentAssignmentBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation (e.g. empty lines), "
                    + "or holderId's presence doesn't match destinationWarehouseId's type (holderId required for "
                    + "a SITE destination — assign-out or transfer — must be omitted for a MAIN destination)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "destinationWarehouseId not found, holderId does "
                    + "not reference an existing user, or a line's equipmentId not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EQUIPMENT_ASSIGNMENT_BATCH_CREATE')")
    public EquipmentAssignmentBatchResponse createDraft(
            @Valid @RequestBody EquipmentAssignmentBatchCreateRequest request) {
        return equipmentAssignmentBatchService.createDraft(request);
    }

    @PostMapping("/{id}/submit")
    @Operation(
            summary = "Submit a draft equipment assignment batch",
            description = "Applies a draft batch: for each line, delegates to the same single-item checkout/"
                    + "check-in logic used by /api/equipment/{id}/checkout|checkin (checkOut is used whenever "
                    + "this batch has a holderId — it resolves assign-out vs. transfer per line itself, from "
                    + "that equipment's current status; checkIn is used otherwise, for a return). The whole "
                    + "operation is one transaction — if any line fails, nothing is applied and the batch stays "
                    + "in its prior state. Per line, the equipment's current status must be valid: AVAILABLE for "
                    + "an assign-out line, CHECKED_OUT or IN_USE for a transfer or return line — violating this "
                    + "is rejected with 409, matching this module's existing checkOut() convention for "
                    + "'equipment not in the right status for this operation' (InvalidEquipmentStatusException), "
                    + "rather than the 422 used for the analogous case in the inventory module. A transfer line "
                    + "whose destination is the warehouse that equipment is already at is rejected with 400 "
                    + "(EquipmentAlreadyAtWarehouseException)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch submitted and applied",
                    content = @Content(schema = @Schema(implementation = EquipmentAssignmentBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "The batch has no lines to submit, or a transfer "
                    + "line's destination is the warehouse that equipment is already at",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment assignment batch not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A line's equipment isn't in a valid status: not "
                    + "AVAILABLE for an assign-out line, or not CHECKED_OUT/IN_USE for a transfer or return line",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('EQUIPMENT_ASSIGNMENT_BATCH_SUBMIT')")
    public EquipmentAssignmentBatchResponse submit(
            @Parameter(description = "Identifier of the batch to submit", example = "15")
            @PathVariable Long id) {
        return equipmentAssignmentBatchService.submit(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an equipment assignment batch by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch found",
                    content = @Content(schema = @Schema(implementation = EquipmentAssignmentBatchResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment assignment batch not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public EquipmentAssignmentBatchResponse getById(
            @Parameter(description = "Identifier of the batch to retrieve", example = "15")
            @PathVariable Long id) {
        return equipmentAssignmentBatchService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List equipment assignment batches, optionally filtered by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of batches",
                    content = @Content(schema = @Schema(implementation = EquipmentAssignmentBatchResponse.class)))
    })
    public List<EquipmentAssignmentBatchResponse> findAll(
            @Parameter(description = "Optional status filter")
            @RequestParam(required = false) EquipmentAssignmentBatchStatus status) {
        return equipmentAssignmentBatchService.findAll(status);
    }
}
