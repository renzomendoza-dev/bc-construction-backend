package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.ItemImageRequest;
import com.bcconstructionservices.inventory.dto.ItemImageResponse;
import com.bcconstructionservices.inventory.entity.ItemImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between ItemImage and its request/response DTOs.
 *
 * <p>Note: toEntity intentionally leaves `item` unset — ItemImageRequest has
 * no itemId field (the parent item comes from the controller's path variable),
 * so the service must call setItem(...) after fetching the parent Item. The
 * "default sortOrder to end-of-list when null" business rule also stays in
 * the service; this mapper just copies sortOrder as given, including null.
 */
@Mapper(componentModel = "spring")
public interface ItemImageMapper {

    ItemImageResponse toResponse(ItemImage image);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "item", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    ItemImage toEntity(ItemImageRequest request);
}
