package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.TransferLineItemResponse;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps TransferLineItem to its response DTO.
 *
 * <p>No toEntity method: building a TransferLineItem from a
 * TransferLineItemRequest needs an Item repository lookup (with a
 * ResourceNotFoundException if the itemId doesn't exist) — real business
 * logic that belongs in TransferBatchService, not a mapper.
 */
@Mapper(componentModel = "spring")
public interface TransferLineItemMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    TransferLineItemResponse toResponse(TransferLineItem line);
}
