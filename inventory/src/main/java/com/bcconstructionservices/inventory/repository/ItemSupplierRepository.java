package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.ItemSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemSupplierRepository extends JpaRepository<ItemSupplier, Long> {

    Optional<ItemSupplier> findByItemIdAndSupplierId(Long itemId, Long supplierId);

    /**
     * Fetches all supplier links for an item, eagerly joining both item and
     * supplier so ItemSupplierMapper.toResponse's flattened itemName/
     * supplierName fields don't trigger a lazy-load per row.
     */
    @Query("SELECT isup FROM ItemSupplier isup JOIN FETCH isup.item JOIN FETCH isup.supplier WHERE isup.item.id = :itemId")
    List<ItemSupplier> findByItemIdWithSupplier(@Param("itemId") Long itemId);
}
 