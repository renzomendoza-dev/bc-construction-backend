package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findByPurchaseOrderId(Long purchaseOrderId);
}
