package com.bcconstructionservices.equipment.controller;

import com.bcconstructionservices.equipment.dto.*;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @Operation(summary = "Create new equipment")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse create(@Valid @RequestBody EquipmentCreateRequest request) {
        Equipment equipment = equipmentService.create(request);
        return equipmentMapper.toResponse(equipment);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update equipment details (name, category, serial, purchase info)")
    public EquipmentResponse update(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentUpdateRequest request) {
        Equipment equipment = equipmentService.update(id, request);
        return equipmentMapper.toResponse(equipment);
    }

    @GetMapping
    @Operation(summary = "List equipment, optionally filtered by status")
    public List<EquipmentResponse> findAll(
            @Parameter(description = "Optional status filter")
            @RequestParam(required = false) EquipmentStatus status) {
        return equipmentService.findAll(status).stream()
                .map(equipmentMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get equipment by id")
    public EquipmentResponse findById(@PathVariable Long id) {
        Equipment equipment = equipmentService.findById(id);
        return equipmentMapper.toResponse(equipment);
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Check out equipment to a user at a site")
    public EquipmentResponse checkOut(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentCheckOutRequest request) {
        Equipment equipment = equipmentService.checkOut(
                id, request.getUserId(), request.getSite(), request.getConditionOut());
        return equipmentMapper.toResponse(equipment);
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "Check in equipment, closing the open assignment")
    public EquipmentResponse checkIn(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentCheckInRequest request) {
        Equipment equipment = equipmentService.checkIn(id, request.getConditionIn());
        return equipmentMapper.toResponse(equipment);
    }

    @GetMapping("/overdue")
    @Operation(summary = "List equipment checked out longer than the given number of days")
    public List<EquipmentResponse> findOverdue(
            @Parameter(description = "Threshold in days", example = "7")
            @RequestParam int days) {
        return equipmentService.findOverdue(days).stream()
                .map(equipmentMapper::toResponse)
                .toList();
    }
}
