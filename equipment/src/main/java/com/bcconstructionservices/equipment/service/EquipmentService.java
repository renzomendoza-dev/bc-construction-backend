package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.exception.DuplicateAssetTagException;
import com.bcconstructionservices.equipment.exception.EquipmentAlreadyAtWarehouseException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.InvalidWarehouseTypeException;
import com.bcconstructionservices.equipment.exception.NoOpenAssignmentException;
import com.bcconstructionservices.equipment.exception.WarehouseNotFoundException;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentAssignmentRepository equipmentAssignmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    public Equipment create(EquipmentCreateRequest request) {
        equipmentRepository.findByAssetTag(request.getAssetTag())
                .ifPresent(existing -> {
                    throw new DuplicateAssetTagException(request.getAssetTag());
                });

        Warehouse warehouse = requireWarehouseOfType(request.getWarehouseId(), WarehouseType.MAIN);

        Equipment equipment = equipmentMapper.toEntity(request);
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setCurrentWarehouseId(warehouse.getId());

        return equipmentRepository.save(equipment);
    }

    /**
     * Resolves warehouseId and confirms it's the type this operation
     * requires (SITE for checkout, MAIN for checkin/create) — checkout/checkin/
     * create all need this same check, just against a different required type.
     */
    private Warehouse requireWarehouseOfType(Long warehouseId, WarehouseType requiredType) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseId));
        if (warehouse.getType() != requiredType) {
            throw new InvalidWarehouseTypeException(
                    "Warehouse " + warehouseId + " must be type " + requiredType
                            + " for this operation (actual type: " + warehouse.getType() + ")");
        }
        return warehouse;
    }

    public Equipment update(Long equipmentId, EquipmentUpdateRequest request) {
        Equipment equipment = findById(equipmentId);
        equipmentMapper.updateEntityFromRequest(request, equipment);
        return equipmentRepository.save(equipment);
    }

    @Transactional(readOnly = true)
    public List<Equipment> findAll(EquipmentStatus statusFilter) {
        if (statusFilter != null) {
            return equipmentRepository.findByStatus(statusFilter);
        }
        return equipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Equipment findById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }

    /**
     * Handles two cases in one method, matching how checkIn already covers
     * both single-item and batch-driven returns:
     *
     * <ul>
     *   <li><b>Fresh assignment</b> — equipment is AVAILABLE (sitting at a
     *       MAIN warehouse). Ordinary checkout.</li>
     *   <li><b>Site-to-site transfer</b> — equipment is already CHECKED_OUT
     *       or IN_USE at a different SITE warehouse. Closes its current open
     *       assignment (recording the new site as that assignment's return
     *       warehouse, same as a real check-in would) and opens a fresh one
     *       at the destination — two clean "one location-stay per row"
     *       EquipmentAssignment records instead of mutating one in place.
     *       holderId is required for a transfer too, same as a fresh
     *       assignment — this is what lets EquipmentAssignmentBatchService
     *       decide "holderId required" purely from destinationWarehouseId's
     *       type (SITE), without first knowing which of these two cases a
     *       given line will turn out to be.</li>
     * </ul>
     *
     * Any other current status (IN_REPAIR, RETIRED, LOST, MAINTENANCE) is
     * rejected the same way as before.
     */
    public Equipment checkOut(Long equipmentId, Long userId, Long siteWarehouseId, String conditionOut) {
        Equipment equipment = findById(equipmentId);

        boolean isTransfer = equipment.getStatus() == EquipmentStatus.CHECKED_OUT
                || equipment.getStatus() == EquipmentStatus.IN_USE;
        if (equipment.getStatus() != EquipmentStatus.AVAILABLE && !isTransfer) {
            throw new InvalidEquipmentStatusException(
                    "Equipment " + equipment.getAssetTag() + " is not available for checkout or transfer "
                            + "(current status: " + equipment.getStatus() + ")");
        }

        if (!userRepository.existsById(userId)) {
            throw new InvalidCheckoutUserException(userId);
        }

        // Looked up before the warehouse check, same precedence checkIn already
        // established: "is there even an open assignment to act on" takes
        // priority over "is the target warehouse valid".
        EquipmentAssignment openAssignment = isTransfer ? requireOpenAssignment(equipment) : null;

        Warehouse siteWarehouse = requireWarehouseOfType(siteWarehouseId, WarehouseType.SITE);

        if (isTransfer && siteWarehouseId.equals(equipment.getCurrentWarehouseId())) {
            throw new EquipmentAlreadyAtWarehouseException(equipmentId, siteWarehouseId);
        }

        if (isTransfer) {
            closeAssignment(openAssignment, siteWarehouse, conditionOut);
        }

        Instant now = Instant.now();

        EquipmentAssignment assignment = EquipmentAssignment.builder()
                .equipment(equipment)
                .assignedToId(userId)
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(now)
                .conditionOut(conditionOut)
                .build();
        equipmentAssignmentRepository.save(assignment);

        equipment.setStatus(EquipmentStatus.CHECKED_OUT);
        equipment.setCurrentHolderId(userId);
        equipment.setCurrentWarehouseId(siteWarehouse.getId());
        equipment.setCheckedOutAt(now);

        return equipmentRepository.save(equipment);
    }

    public Equipment checkIn(Long equipmentId, Long destinationWarehouseId, String conditionIn) {
        Equipment equipment = findById(equipmentId);

        // Explicit status check (not just "an open assignment exists") since
        // this is also reused by EquipmentAssignmentBatchService.submit for a
        // return batch, which needs to reject a line whose equipment isn't
        // actually checked out — same 409 InvalidEquipmentStatusException the
        // checkOut side of this module already uses for "wrong state".
        if (equipment.getStatus() != EquipmentStatus.CHECKED_OUT && equipment.getStatus() != EquipmentStatus.IN_USE) {
            throw new InvalidEquipmentStatusException(
                    "Equipment " + equipment.getAssetTag() + " is not checked out (current status: "
                            + equipment.getStatus() + ")");
        }

        EquipmentAssignment openAssignment = requireOpenAssignment(equipment);

        Warehouse destinationWarehouse = requireWarehouseOfType(destinationWarehouseId, WarehouseType.MAIN);

        closeAssignment(openAssignment, destinationWarehouse, conditionIn);

        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setCurrentHolderId(null);
        equipment.setCurrentWarehouseId(destinationWarehouse.getId());
        equipment.setCheckedOutAt(null);

        return equipmentRepository.save(equipment);
    }

    private EquipmentAssignment requireOpenAssignment(Equipment equipment) {
        return equipmentAssignmentRepository
                .findByEquipmentIdAndCheckedInAtIsNull(equipment.getId())
                .orElseThrow(() -> new NoOpenAssignmentException(equipment.getId()));
    }

    /**
     * Closes an already-fetched open assignment (checkedInAt/conditionIn/
     * returnWarehouseId), shared by checkIn (destination always MAIN) and
     * checkOut's transfer branch (destination always a different SITE) —
     * the closing logic itself doesn't care which type the destination is,
     * only the caller's own validation does. Takes the assignment already
     * looked up via requireOpenAssignment rather than looking it up itself,
     * so each caller controls exactly when in its own validation sequence
     * that lookup (and its NoOpenAssignmentException) happens.
     */
    private void closeAssignment(EquipmentAssignment openAssignment, Warehouse destinationWarehouse,
                                  String conditionNotes) {
        openAssignment.setCheckedInAt(Instant.now());
        openAssignment.setConditionIn(conditionNotes);
        openAssignment.setReturnWarehouseId(destinationWarehouse.getId());
        equipmentAssignmentRepository.save(openAssignment);
    }

    @Transactional(readOnly = true)
    public List<Equipment> findOverdue(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        return equipmentRepository.findByStatusAndCheckedOutAtBefore(EquipmentStatus.CHECKED_OUT, cutoff);
    }
}