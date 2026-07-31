package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.exception.DuplicateAssetTagException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.NoOpenAssignmentException;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentAssignmentRepository equipmentAssignmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final UserRepository userRepository;

    public Equipment create(EquipmentCreateRequest request) {
        equipmentRepository.findByAssetTag(request.getAssetTag())
                .ifPresent(existing -> {
                    throw new DuplicateAssetTagException(request.getAssetTag());
                });

        Equipment equipment = equipmentMapper.toEntity(request);
        equipment.setStatus(EquipmentStatus.AVAILABLE);

        return equipmentRepository.save(equipment);
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

    public Equipment checkOut(Long equipmentId, Long userId, String site, String conditionOut) {
        Equipment equipment = findById(equipmentId);

        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new InvalidEquipmentStatusException(
                    "Equipment " + equipment.getAssetTag() + " is not available for checkout (current status: "
                            + equipment.getStatus() + ")");
        }

        if (!userRepository.existsById(userId)) {
            throw new InvalidCheckoutUserException(userId);
        }

        LocalDateTime now = LocalDateTime.now();

        EquipmentAssignment assignment = EquipmentAssignment.builder()
                .equipment(equipment)
                .assignedToId(userId)
                .site(site)
                .checkedOutAt(now)
                .conditionOut(conditionOut)
                .build();
        equipmentAssignmentRepository.save(assignment);

        equipment.setStatus(EquipmentStatus.CHECKED_OUT);
        equipment.setCurrentHolderId(userId);
        equipment.setCurrentSite(site);
        equipment.setCheckedOutAt(now);

        return equipmentRepository.save(equipment);
    }

    public Equipment checkIn(Long equipmentId, String conditionIn) {
        Equipment equipment = findById(equipmentId);

        EquipmentAssignment openAssignment = equipmentAssignmentRepository
                .findByEquipmentIdAndCheckedInAtIsNull(equipmentId)
                .orElseThrow(() -> new NoOpenAssignmentException(equipmentId));

        openAssignment.setCheckedInAt(LocalDateTime.now());
        openAssignment.setConditionIn(conditionIn);
        equipmentAssignmentRepository.save(openAssignment);

        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setCurrentHolderId(null);
        equipment.setCurrentSite(null);
        equipment.setCheckedOutAt(null);

        return equipmentRepository.save(equipment);
    }

    @Transactional(readOnly = true)
    public List<Equipment> findOverdue(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return equipmentRepository.findByStatusAndCheckedOutAtBefore(EquipmentStatus.CHECKED_OUT, cutoff);
    }
}