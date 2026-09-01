package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between PurchaseOrder and its request/response DTOs. Composed with
 * {@link PurchaseOrderLineMapper} so the lines collection on
 * PurchaseOrderResponse is mapped element-by-element automatically (though
 * each line's receivedQuantity still needs a service-level pass — see that
 * mapper's javadoc).
 *
 * <p>toEntity/updateEntityFromRequest are intentionally thin — supplier
 * comes from a repository lookup (and gets validated there), status/
 * initiatedBy are system-managed, and lines need per-line Item resolution.
 * All of that stays in the service; these only cover the one field that
 * really is a 1:1 copy (notes).
 */
@Mapper(componentModel = "spring", uses = {PurchaseOrderLineMapper.class, UserLookupHelper.class})
public interface PurchaseOrderMapper {

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "initiatedByName", source = "initiatedBy", qualifiedByName = "resolveUserName")
    PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "status", ignore = true) // left at the entity's own field default (DRAFT)
    @Mapping(target = "initiatedBy", ignore = true) // set by @CreatedBy auditing, not the request body
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PurchaseOrder toEntity(PurchaseOrderCreateRequest request);

    // No NullValuePropertyMappingStrategy.IGNORE here, deliberately — this is
    // a full-replacement PUT (see PurchaseOrderUpdateRequest's javadoc), so
    // an explicit null in the request must clear notes, not leave it as-is.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "initiatedBy", ignore = true)
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(PurchaseOrderUpdateRequest request, @MappingTarget PurchaseOrder purchaseOrder);
}
