package com.bcconstructionservices.equipment.controller;

import com.bcconstructionservices.equipment.dto.*;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@Tag(name = "Equipment", description = "Equipment inventory, checkout, and check-in operations")
@SecurityRequirement(name = "bearerAuth")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final EquipmentMapper equipmentMapper;

    @PostMapping
    @Operation(
            summary = "Create new equipment",
            description = "Registers a new piece of equipment at a MAIN-type warehouse. warehouseId is required "
                    + "-  equipment.currentWarehouseId is always populated, so even newly-registered equipment "
                    + "that's never been checked out needs a starting location."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipment created",
                    content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, or warehouseId "
                    + "does not reference a MAIN-type warehouse",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "warehouseId not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "assetTag already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EQUIPMENT_CREATE')")
    public EquipmentResponse create(@Valid @RequestBody EquipmentCreateRequest request) {
        Equipment equipment = equipmentService.create(request);
        return equipmentMapper.toResponse(equipment);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update equipment details (name, category, serial, purchase info)",
            description = "Status, holder, and current warehouse are not editable here — they change only via "
                    + "checkout/checkin (or a batch that delegates to them).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment updated",
                    content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('EQUIPMENT_EDIT')")
    public EquipmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentUpdateRequest request) {
        Equipment equipment = equipmentService.update(id, request);
        return equipmentMapper.toResponse(equipment);
    }

    @GetMapping
    @Operation(summary = "List equipment, optionally filtered by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of equipment",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EquipmentResponse.class))))
    })
    public List<EquipmentResponse> findAll(
            @Parameter(description = "Optional status filter")
            @RequestParam(required = false) EquipmentStatus status) {
        return equipmentService.findAll(status).stream()
                .map(equipmentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get equipment by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment found",
                    content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public EquipmentResponse findById(@PathVariable Long id) {
        Equipment equipment = equipmentService.findById(id);
        return equipmentMapper.toResponse(equipment);
    }

    @PostMapping("/{id}/checkout")
    @Operation(
            summary = "Check out equipment to a user at a site — also handles a direct site-to-site transfer",
            description = "siteWarehouseId must reference a SITE-type warehouse — a checkout targeting a MAIN "
                    + "warehouse is rejected with 400. Two starting states are accepted: AVAILABLE equipment "
                    + "(an ordinary checkout), or equipment already CHECKED_OUT/IN_USE at a *different* SITE "
                    + "warehouse (a direct transfer — closes its current assignment and opens a new one at "
                    + "siteWarehouseId, without an intermediate check-in to a MAIN warehouse). userId is "
                    + "required either way, including for a transfer: it either reconfirms the same holder or "
                    + "reassigns to someone new. Targeting the SITE warehouse the equipment is already at is "
                    + "rejected with 400. For processing many pieces of equipment in one action (including "
                    + "transfers), see POST /api/equipment/assignment-batches instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment checked out or transferred",
                    content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, siteWarehouseId "
                    + "does not reference a SITE-type warehouse, or (for a transfer) siteWarehouseId is the "
                    + "warehouse the equipment is already at",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment not found, siteWarehouseId not found, or "
                    + "userId does not reference an existing user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Equipment is not currently AVAILABLE, CHECKED_OUT, "
                    + "or IN_USE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('EQUIPMENT_CHECKOUT')")
    public EquipmentResponse checkOut(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentCheckOutRequest request) {
        Equipment equipment = equipmentService.checkOut(
                id, request.getUserId(), request.getSiteWarehouseId(), request.getConditionOut());
        return equipmentMapper.toResponse(equipment);
    }

    @PostMapping("/{id}/checkin")
    @Operation(
            summary = "Check in equipment, closing the open assignment",
            description = "destinationWarehouseId must reference a MAIN-type warehouse — a check-in targeting a "
                    + "SITE warehouse is rejected with 400. For returning many pieces of equipment in one action, "
                    + "see POST /api/equipment/assignment-batches instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipment checked in",
                    content = @Content(schema = @Schema(implementation = EquipmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, or "
                    + "destinationWarehouseId does not reference a MAIN-type warehouse",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Equipment not found, or destinationWarehouseId "
                    + "not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Equipment is not currently CHECKED_OUT or IN_USE, "
                    + "or has no open assignment to close",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('EQUIPMENT_CHECKIN')")
    public EquipmentResponse checkIn(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentCheckInRequest request) {
        Equipment equipment = equipmentService.checkIn(
                id, request.getDestinationWarehouseId(), request.getConditionIn());
        return equipmentMapper.toResponse(equipment);
    }

    @GetMapping("/overdue")
    @Operation(summary = "List equipment checked out longer than the given number of days")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of overdue equipment",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EquipmentResponse.class))))
    })
    public List<EquipmentResponse> findOverdue(
            @Parameter(description = "Threshold in days", example = "7")
            @RequestParam int days) {
        return equipmentService.findOverdue(days).stream()
                .map(equipmentMapper::toResponse)
                .toList();
    }
}
