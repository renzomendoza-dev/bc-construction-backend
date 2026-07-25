package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between PurchaseReceipt and its request/response DTOs. Composed with
 * {@link PurchaseReceiptLineMapper} so the lines collection on
 * PurchaseReceiptResponse is mapped element-by-element automatically.
 *
 * <p>toEntity is intentionally thin — most of what
 * PurchaseReceiptService.createPurchaseReceipt does isn't field copying:
 * supplier/warehouse come from repository lookups (and get validated there),
 * totalAmount is the sum of computed line totals, lines themselves need
 * per-line Item resolution plus a computed lineTotal, and confirmed/
 * confirmedAt/createdAt are either entity defaults or set by @PrePersist.
 * All of that stays in the service; this mapper only covers the handful of
 * fields that really are a 1:1 copy (receiptNumber, purchaseDate, imageUrl,
 * notes).
 */
@Mapper(componentModel = "spring", uses = {PurchaseReceiptLineMapper.class, UserLookupHelper.class})
public interface PurchaseReceiptMapper {

    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "warehouseId", source = "warehouse.id")
    @Mapping(target = "warehouseName", source = "warehouse.name")
    @Mapping(target = "confirmedByName", source = "confirmedBy", qualifiedByName = "resolveUserName")
    @Mapping(target = "createdByName", source = "createdBy", qualifiedByName = "resolveUserName")
    PurchaseReceiptResponse toResponse(PurchaseReceipt receipt);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "warehouse", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "lines", ignore = true)
    @Mapping(target = "confirmed", ignore = true) // left at the entity's own field default (false)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    PurchaseReceipt toEntity(PurchaseReceiptCreateRequest request);
}
