package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between MaterialRequest and its request/response DTOs. Composed with
 * {@link MaterialRequestLineItemMapper} so the lines collection on
 * MaterialRequestResponse is mapped element-by-element automatically.
 *
 * <p>toEntity is intentionally thin — site (resolved + validated as type
 * SITE), requestedBy (from CurrentUserService), status (always SUBMITTED on
 * creation), and lines (per-line Item resolution) all stay in the service —
 * this mapper only covers the handful of fields that really are a 1:1 copy
 * (dateNeeded, notes).
 */
@Mapper(componentModel = "spring", uses = {MaterialRequestLineItemMapper.class, UserLookupHelper.class})
public interface MaterialRequestMapper {

    @Mapping(target = "siteWarehouseId", source = "site.id")
    @Mapping(target = "siteWarehouseName", source = "site.name")
    @Mapping(target = "requestedByName", source = "requestedBy", qualifiedByName = "resolveUserName")
    @Mapping(target = "lines", source = "lineItems")
    MaterialRequestResponse toResponse(MaterialRequest materialRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "site", ignore = true)
    @Mapping(target = "requestedBy", ignore = true) // set from CurrentUserService, not the request body
    @Mapping(target = "status", ignore = true) // set explicitly to SUBMITTED in the service
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    @Mapping(target = "updatedAt", ignore = true) // set by @PrePersist
    MaterialRequest toEntity(MaterialRequestCreateRequest request);

    /**
     * Deliberately NOT using NullValuePropertyMappingStrategy.IGNORE (unlike
     * WarehouseMapper.updateEntityFromRequest) — this is a full-replacement
     * PUT, not a partial PATCH, so dateNeeded/notes are copied as-is
     * including null, letting a caller explicitly clear either field.
     * site/requestedBy/status/lineItems never change via this call — site is
     * immutable after creation, and lineItems is rebuilt by hand in the
     * service (needs per-line Item resolution, which isn't mapper territory).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "site", ignore = true)
    @Mapping(target = "requestedBy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(MaterialRequestUpdateRequest request, @MappingTarget MaterialRequest materialRequest);
}
