package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemImage;
import com.bcconstructionservices.inventory.exception.DuplicateResourceException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.ItemImageMapper;
import com.bcconstructionservices.inventory.mapper.ItemMapper;
import com.bcconstructionservices.inventory.repository.ItemImageRepository;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service layer for Item and ItemImage management: creation, updates,
 * lookups, soft-deactivation, and image ordering. All entity&lt;-&gt;DTO
 * conversion is delegated to ItemMapper/ItemImageMapper — this class holds
 * only business logic (validation, lookups, ordering) that a mapper
 * legitimately can't own.
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;

    @Transactional
    public ItemResponse createItem(ItemCreateRequest request) {
        if (itemRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Item", "sku", request.getSku());
        }

        Item item = itemMapper.toEntity(request);
        Item saved = itemRepository.save(item);
        return itemMapper.toResponse(saved);
    }

    @Transactional
    public ItemResponse updateItem(Long itemId, ItemUpdateRequest request) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        // Checked against the item's current (pre-update) sku, before
        // itemMapper applies the request onto it.
        if (request.getSku() != null && !request.getSku().equals(item.getSku())
                && itemRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Item", "sku", request.getSku());
        }

        itemMapper.updateEntityFromRequest(request, item);

        Item saved = itemRepository.save(item);
        return itemMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ItemResponse getItemById(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        return itemMapper.toResponse(item);
    }

    @Transactional(readOnly = true)
    public ItemResponse getItemBySku(String sku) {
        Item item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with sku: " + sku));
        return itemMapper.toResponse(item);
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemSummaryResponse> listItems(String category, Boolean active, String search, Pageable pageable) {
        String normalizedCategory = StringUtils.hasText(category) ? category : null;
        String normalizedSearch = StringUtils.hasText(search) ? search : null;

        Page<Item> page = itemRepository.search(normalizedCategory, active, normalizedSearch, pageable);
        return PageResponse.of(page, itemMapper::toSummaryResponse);
    }

    @Transactional
    public void deactivateItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        item.setActive(false);
        itemRepository.save(item);
    }

    @Transactional
    public ItemImageResponse addItemImage(Long itemId, ItemImageRequest request) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        ItemImage image = itemImageMapper.toEntity(request);
        image.setItem(item);
        // itemImageMapper copies sortOrder as-is (including null); defaulting a
        // missing sortOrder to "append at the end" is business logic that stays
        // here rather than in the mapper.
        if (image.getSortOrder() == null) {
            image.setSortOrder(item.getImages().size());
        }

        ItemImage saved = itemImageRepository.save(image);
        item.getImages().add(saved);

        return itemImageMapper.toResponse(saved);
    }

    /**
     * Deletes the ItemImage row and returns a response view of the row that was
     * removed - notably its imageUrl - so the caller can clean up the backing
     * file on disk after the DB record is gone.
     *
     * @param imageId id of the image to remove
     * @return the removed image as an ItemImageResponse (carrying the stored imageUrl)
     * @throws ResourceNotFoundException if no image with that id exists
     */
    @Transactional
    public ItemImageResponse removeItemImage(Long imageId) {
        ItemImage image = itemImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Item image not found: " + imageId));

        // Capture the response (including imageUrl) BEFORE deleting, so the
        // mapped DTO is fully populated from the still-attached entity.
        ItemImageResponse response = itemImageMapper.toResponse(image);

        itemImageRepository.delete(image);

        return response;
    }

    /**
     * Sets sortOrder on each image according to its position in orderedImageIds
     * (index 0 becomes sortOrder 0, and so on). Every id must belong to the
     * given item; images not referenced in orderedImageIds are left unchanged.
     */
    @Transactional
    public List<ItemImageResponse> reorderImages(Long itemId, List<Long> orderedImageIds) {
        if (!itemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("Item", itemId);
        }

        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortOrderAsc(itemId);
        Map<Long, ItemImage> imagesById = images.stream()
                .collect(Collectors.toMap(ItemImage::getId, Function.identity()));

        List<ItemImageResponse> result = new ArrayList<>(orderedImageIds.size());
        for (int position = 0; position < orderedImageIds.size(); position++) {
            Long imageId = orderedImageIds.get(position);
            ItemImage image = imagesById.get(imageId);
            if (image == null) {
                throw new ResourceNotFoundException(
                        "ItemImage not found with id: " + imageId + " for item: " + itemId);
            }
            image.setSortOrder(position);
            result.add(itemImageMapper.toResponse(image));
        }

        itemImageRepository.saveAll(imagesById.values());
        return result;
    }
}
