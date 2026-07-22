package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptLineResponse;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps PurchaseReceiptLine to its response DTO.
 *
 * <p>No toEntity method: building a PurchaseReceiptLine from
 * PurchaseReceiptLineRequest needs an Item repository lookup (with a
 * ReceiptProcessingException if the itemId doesn't exist) and a computed
 * lineTotal (quantity * unitCost) — both real business logic that belongs
 * in PurchaseReceiptService, not a mapper.
 */
@Mapper(componentModel = "spring")
public interface PurchaseReceiptLineMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    PurchaseReceiptLineResponse toResponse(PurchaseReceiptLine line);
}
