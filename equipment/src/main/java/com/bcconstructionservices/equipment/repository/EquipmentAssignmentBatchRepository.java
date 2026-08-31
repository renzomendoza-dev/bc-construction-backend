package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentAssignmentBatchRepository extends JpaRepository<EquipmentAssignmentBatch, Long> {

    List<EquipmentAssignmentBatch> findByStatus(EquipmentAssignmentBatchStatus status);
}
