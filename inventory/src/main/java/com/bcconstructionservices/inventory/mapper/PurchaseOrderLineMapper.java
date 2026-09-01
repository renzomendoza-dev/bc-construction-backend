package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseOrderLineResponse;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PurchaseOrderLineMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "itemSku", source = "item.sku")
    // receivedQuantity needs a repository query (sum of confirmed receipt
    // lines against this order for this item), not a plain field copy — set
    // by PurchaseOrderService after mapping, not mapper territory.
    @Mapping(target = "receivedQuantity", ignore = true)
    PurchaseOrderLineResponse toResponse(PurchaseOrderLine line);
}
