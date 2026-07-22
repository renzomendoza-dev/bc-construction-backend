package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByCode(String code);

    /**
     * Returns warehouses filtered by active status; passing null returns all warehouses.
     */
    @Query("SELECT w FROM Warehouse w WHERE (:active IS NULL OR w.active = :active)")
    Page<Warehouse> search(@Param("active") Boolean active, Pageable pageable);
}
