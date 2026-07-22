package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.WarehouseCreateRequest;
import com.bcconstructionservices.inventory.dto.WarehouseResponse;
import com.bcconstructionservices.inventory.dto.WarehouseUpdateRequest;
import com.bcconstructionservices.inventory.entity.Warehouse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Maps between Warehouse and its request/response DTOs.
 */
@Mapper(componentModel = "spring")
public interface WarehouseMapper {

    WarehouseResponse toResponse(Warehouse warehouse);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true) // left at the entity's own field default (true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    @Mapping(target = "updatedAt", ignore = true) // set by @PrePersist
    Warehouse toEntity(WarehouseCreateRequest request);

    /**
     * Applies only the non-null fields present in request onto the existing
     * warehouse. WarehouseUpdateRequest has no `code` field by design —
     * warehouse code is immutable once created — so code is explicitly
     * ignored here rather than left to an implicit "no matching source" skip.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(WarehouseUpdateRequest request, @MappingTarget Warehouse warehouse);
}
