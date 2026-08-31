package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.TransferLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferLineItemRepository extends JpaRepository<TransferLineItem, Long> {

    List<TransferLineItem> findByTransferBatchId(Long transferBatchId);
}
