package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.ItemCreateRequest;
import com.bcconstructionservices.inventory.dto.ItemResponse;
import com.bcconstructionservices.inventory.dto.ItemSummaryResponse;
import com.bcconstructionservices.inventory.dto.ItemUpdateRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemImage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Comparator;

/**
 * Maps between Item and its request/response DTOs. Composed with
 * {@link ItemImageMapper} so the images collection on ItemResponse is
 * mapped element-by-element automatically.
 *
 * <p>Note: this mapper only handles field-level transformation. Business
 * rules that currently live in ItemService (SKU-uniqueness checks before
 * create/update, defaulting a new image's sortOrder to the end of the
 * list, etc.) are NOT reproduced here and still belong in the service layer
 * even if it's refactored to delegate mapping to this interface.
 */
@Mapper(componentModel = "spring", uses = ItemImageMapper.class)
public interface ItemMapper {

    ItemResponse toResponse(Item item);

    @Mapping(target = "primaryImageUrl", expression = "java(resolvePrimaryImageUrl(item))")
    ItemSummaryResponse toSummaryResponse(Item item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true) // left at the entity's own field default (true)
    @Mapping(target = "images", ignore = true) // left at the entity's own field default (empty list)
    @Mapping(target = "createdAt", ignore = true) // set by @PrePersist
    @Mapping(target = "updatedAt", ignore = true) // set by @PrePersist
    Item toEntity(ItemCreateRequest request);

    /**
     * Applies only the non-null fields present in request onto the existing item,
     * matching the partial-update semantics ItemUpdateRequest documents. Fields
     * ItemUpdateRequest doesn't carry (active, images, timestamps) are left untouched.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ItemUpdateRequest request, @MappingTarget Item item);

    /**
     * Lowest-sortOrder image's URL, or null if the item has no images.
     * Used to populate ItemSummaryResponse.primaryImageUrl.
     */
    default String resolvePrimaryImageUrl(Item item) {
        if (item.getImages() == null || item.getImages().isEmpty()) {
            return null;
        }
        return item.getImages().stream()
                .min(Comparator.comparing(ItemImage::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ItemImage::getImageUrl)
                .orElse(null);
    }
}
