package com.bcconstructionservices.equipment.mapper;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.inventory.service.WarehouseLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {UserLookupHelper.class, WarehouseLookupHelper.class})
public interface EquipmentMapper {

    @Mapping(target = "currentHolderName", source = "currentHolderId", qualifiedByName = "resolveUserName")
    @Mapping(target = "currentWarehouseName", source = "currentWarehouseId", qualifiedByName = "resolveWarehouseName")
    EquipmentResponse toResponse(Equipment equipment);

    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentAssetTag", source = "equipment.assetTag")
    @Mapping(target = "assignedToName", source = "assignedToId", qualifiedByName = "resolveUserName")
    @Mapping(target = "warehouseName", source = "warehouseId", qualifiedByName = "resolveWarehouseName")
    @Mapping(target = "returnWarehouseName", source = "returnWarehouseId", qualifiedByName = "resolveWarehouseName")
    EquipmentAssignmentResponse toResponse(EquipmentAssignment assignment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentHolderId", ignore = true)
    // currentWarehouseId isn't a 1:1 copy of request.warehouseId — the service
    // validates it's a real, MAIN-type warehouse first (same reasoning as
    // supplier/warehouse elsewhere in this codebase), so it's set there, not here.
    @Mapping(target = "currentWarehouseId", ignore = true)
    @Mapping(target = "checkedOutAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Equipment toEntity(EquipmentCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "assetTag", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentHolderId", ignore = true)
    @Mapping(target = "currentWarehouseId", ignore = true)
    @Mapping(target = "checkedOutAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(EquipmentUpdateRequest request, @MappingTarget Equipment equipment);
}