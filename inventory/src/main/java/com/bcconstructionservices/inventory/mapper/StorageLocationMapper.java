package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StorageLocationRequest;
import com.bcconstructionservices.inventory.dto.StorageLocationResponse;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between StorageLocation and its request/response DTOs.
 *
 * <p>Note: toEntity intentionally leaves `warehouse` unset — resolving
 * warehouseId to a managed Warehouse entity requires a repository lookup
 * (also where the service validates the warehouse exists and checks the
 * duplicate-code-within-warehouse rule), so the service must fetch it and
 * call setWarehouse(...) itself.
 */
@Mapper(componentModel = "spring")
public interface StorageLocationMapper {

    @Mapping(target = "warehouseId", source = "warehouse.id")
    StorageLocationResponse toResponse(StorageLocation location);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    StorageLocation toEntity(StorageLocationRequest request);
}
