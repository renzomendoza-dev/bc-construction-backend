package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    List<StorageLocation> findByWarehouseId(Long warehouseId);
}
