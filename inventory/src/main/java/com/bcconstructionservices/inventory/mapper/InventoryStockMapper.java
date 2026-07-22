package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.entity.InventoryStock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps InventoryStock to its flattened response view.
 *
 * <p>No toEntity method: InventoryStock rows are never created directly
 * from a request DTO in this domain — they're only ever created or mutated
 * internally by InventoryService's stock-adjustment logic, which builds the
 * entity itself and is the sole owner of InventoryStock.quantity changes.
 */
@Mapper(componentModel = "spring")
public interface InventoryStockMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "sku", source = "item.sku")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "locationId", source = "location.id")
    @Mapping(target = "locationCode", source = "location.code")
    StockLevelResponse toStockLevelResponse(InventoryStock stock);
}
