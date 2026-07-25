package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.entity.StockMovement;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps StockMovement to its response DTO.
 *
 * <p>No toEntity method: StockMovement rows are an append-only audit trail
 * written internally by InventoryService (and, via it, PurchaseReceiptService)
 * as a side effect of a stock change — there's no request DTO that creates
 * one directly.
 */
@Mapper(componentModel = "spring", uses = UserLookupHelper.class)
public interface StockMovementMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "fromLocationId", source = "fromLocation.id")
    @Mapping(target = "toLocationId", source = "toLocation.id")
    @Mapping(target = "createdByName", source = "createdBy", qualifiedByName = "resolveUserName")
    StockMovementResponse toResponse(StockMovement movement);
}