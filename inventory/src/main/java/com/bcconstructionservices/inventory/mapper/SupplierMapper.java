package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.SupplierCreateRequest;
import com.bcconstructionservices.inventory.dto.SupplierResponse;
import com.bcconstructionservices.inventory.dto.SupplierUpdateRequest;
import com.bcconstructionservices.inventory.entity.Supplier;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Maps between Supplier and its request/response DTOs.
 */
@Mapper(componentModel = "spring")
public interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true) // left at the entity's own field default (true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    @Mapping(target = "updatedAt", ignore = true) // set by @PrePersist
    Supplier toEntity(SupplierCreateRequest request);

    /**
     * Applies only the non-null fields present in request (including active,
     * which SupplierUpdateRequest does carry unlike ItemUpdateRequest) onto
     * the existing supplier.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(SupplierUpdateRequest request, @MappingTarget Supplier supplier);
}
