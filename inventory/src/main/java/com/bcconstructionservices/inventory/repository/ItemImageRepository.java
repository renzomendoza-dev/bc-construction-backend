package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    List<ItemImage> findByItemIdOrderBySortOrderAsc(Long itemId);
}
