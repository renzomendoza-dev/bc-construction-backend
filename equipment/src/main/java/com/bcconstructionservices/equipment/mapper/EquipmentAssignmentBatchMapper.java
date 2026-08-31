package com.bcconstructionservices.equipment.mapper;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchLineResponse;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import com.bcconstructionservices.inventory.service.WarehouseLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between EquipmentAssignmentBatch(Line) and its request/response DTOs.
 *
 * <p>toEntity covers destinationWarehouseId/holderId/notes as plain 1:1
 * copies (matching TransferBatchMapper's treatment of its own analogous
 * sourceMaterialRequestId) — existence/type/direction validation for
 * destinationWarehouseId and holderId happens in the service, but doesn't
 * change the value, so there's nothing to re-set afterward. What the mapper
 * doesn't cover: initiatedBy comes from {@code @CreatedBy} auditing rather
 * than the request body, and lines need per-line Equipment resolution. Both
 * stay in the service.
 */
@Mapper(componentModel = "spring", uses = {UserLookupHelper.class, WarehouseLookupHelper.class})
public interface EquipmentAssignmentBatchMapper {

    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouseId", qualifiedByName = "resolveWarehouseName")
    @Mapping(target = "holderName", source = "holderId", qualifiedByName = "resolveUserName")
    @Mapping(target = "initiatedByName", source = "initiatedBy", qualifiedByName = "resolveUserName")
    EquipmentAssignmentBatchResponse toResponse(EquipmentAssignmentBatch batch);

    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentAssetTag", source = "equipment.assetTag")
    @Mapping(target = "equipmentName", source = "equipment.name")
    EquipmentAssignmentBatchLineResponse toResponse(EquipmentAssignmentBatchLine line);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true) // left at the entity's own field default (DRAFT)
    @Mapping(target = "initiatedBy", ignore = true) // set by @CreatedBy auditing, not the request body
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    EquipmentAssignmentBatch toEntity(EquipmentAssignmentBatchCreateRequest request);
}
