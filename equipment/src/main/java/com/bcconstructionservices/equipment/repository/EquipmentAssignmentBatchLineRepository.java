package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentAssignmentBatchLineRepository extends JpaRepository<EquipmentAssignmentBatchLine, Long> {

    List<EquipmentAssignmentBatchLine> findByBatchId(Long batchId);
}
