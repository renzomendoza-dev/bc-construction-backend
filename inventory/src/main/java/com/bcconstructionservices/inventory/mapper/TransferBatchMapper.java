package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between TransferBatch and its request/response DTOs. Composed with
 * {@link TransferLineItemMapper} so the lines collection on
 * TransferBatchResponse is mapped element-by-element automatically.
 *
 * <p>toEntity is intentionally thin — most of what
 * TransferBatchService.createDraft does isn't field copying: origin/destination
 * warehouses come from repository lookups (and get validated there),
 * initiatedBy comes from CurrentUserService rather than the request body, and
 * lines themselves need per-line Item resolution. All of that stays in the
 * service; this mapper only covers the handful of fields that really are a
 * 1:1 copy (sourceMaterialRequestId, notes).
 */
@Mapper(componentModel = "spring", uses = {TransferLineItemMapper.class, UserLookupHelper.class})
public interface TransferBatchMapper {

    @Mapping(target = "originWarehouseId", source = "originWarehouse.id")
    @Mapping(target = "originWarehouseName", source = "originWarehouse.name")
    @Mapping(target = "destinationWarehouseId", source = "destinationWarehouse.id")
    @Mapping(target = "destinationWarehouseName", source = "destinationWarehouse.name")
    @Mapping(target = "initiatedByName", source = "initiatedBy", qualifiedByName = "resolveUserName")
    @Mapping(target = "lines", source = "lineItems")
    TransferBatchResponse toResponse(TransferBatch batch);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "originWarehouse", ignore = true)
    @Mapping(target = "destinationWarehouse", ignore = true)
    @Mapping(target = "status", ignore = true) // left at the entity's own field default (DRAFT)
    @Mapping(target = "initiatedBy", ignore = true) // set from CurrentUserService, not the request body
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    @Mapping(target = "updatedAt", ignore = true) // set by @PrePersist
    TransferBatch toEntity(TransferBatchCreateRequest request);
}
