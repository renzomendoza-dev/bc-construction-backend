package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialRequestLineItemRepository extends JpaRepository<MaterialRequestLineItem, Long> {

    List<MaterialRequestLineItem> findByMaterialRequestId(Long materialRequestId);
}
