package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Returns suppliers filtered by active status; passing null returns all suppliers.
     */
    @Query("SELECT s FROM Supplier s WHERE (:active IS NULL OR s.active = :active)")
    Page<Supplier> search(@Param("active") Boolean active, Pageable pageable);
}
