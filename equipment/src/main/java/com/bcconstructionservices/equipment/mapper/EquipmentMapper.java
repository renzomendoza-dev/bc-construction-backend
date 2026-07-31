package com.bcconstructionservices.equipment.mapper;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentResponse;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = UserLookupHelper.class)
public interface EquipmentMapper {

    @Mapping(target = "currentHolderName", source = "currentHolderId", qualifiedByName = "resolveUserName")
    EquipmentResponse toResponse(Equipment equipment);

    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentAssetTag", source = "equipment.assetTag")
    @Mapping(target = "assignedToName", source = "assignedToId", qualifiedByName = "resolveUserName")
    EquipmentAssignmentResponse toResponse(EquipmentAssignment assignment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "currentHolderId", ignore = true)
    @Mapping(target = "currentSite", ignore = true)
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
    @Mapping(target = "currentSite", ignore = true)
    @Mapping(target = "checkedOutAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(EquipmentUpdateRequest request, @MappingTarget Equipment equipment);
}