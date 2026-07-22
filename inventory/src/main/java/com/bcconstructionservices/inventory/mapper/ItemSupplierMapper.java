package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.ItemSupplierRequest;
import com.bcconstructionservices.inventory.dto.ItemSupplierResponse;
import com.bcconstructionservices.inventory.entity.ItemSupplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between ItemSupplier and its request/response DTOs.
 *
 * <p>Note: toEntity intentionally leaves `item` and `supplier` unset —
 * ItemSupplierRequest only carries their ids, and resolving an id to a
 * managed entity requires a repository lookup, which doesn't belong in a
 * mapper. The service must fetch both entities (also the point where it
 * validates they exist and are active) and call setItem(...)/setSupplier(...)
 * itself, along with the create-vs-update-existing-row upsert logic.
 */
@Mapper(componentModel = "spring")
public interface ItemSupplierMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    ItemSupplierResponse toResponse(ItemSupplier itemSupplier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "item", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    ItemSupplier toEntity(ItemSupplierRequest request);
}
