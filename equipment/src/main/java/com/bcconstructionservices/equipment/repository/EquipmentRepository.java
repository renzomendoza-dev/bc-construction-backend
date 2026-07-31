package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByAssetTag(String assetTag);

    List<Equipment> findByStatus(EquipmentStatus status);

    List<Equipment> findByStatusAndCheckedOutAtBefore(EquipmentStatus status, LocalDateTime cutoff);
}
