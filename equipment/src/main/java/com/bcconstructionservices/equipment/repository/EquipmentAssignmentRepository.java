package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, Long> {

    Optional<EquipmentAssignment> findByEquipmentIdAndCheckedInAtIsNull(Long equipmentId);

    List<EquipmentAssignment> findByAssignedToIdAndCheckedInAtIsNull(Long userId);
}
