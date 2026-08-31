package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemResponse;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps MaterialRequestLineItem to its response DTO.
 *
 * <p>No toEntity method: building a MaterialRequestLineItem from a
 * MaterialRequestLineItemRequest needs an Item repository lookup (with a
 * ResourceNotFoundException if the itemId doesn't exist) — real business
 * logic that belongs in MaterialRequestService, not a mapper.
 */
@Mapper(componentModel = "spring")
public interface MaterialRequestLineItemMapper {

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    MaterialRequestLineItemResponse toResponse(MaterialRequestLineItem line);
}
