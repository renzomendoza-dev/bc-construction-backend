package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.ItemCreateRequest;
import com.bcconstructionservices.inventory.dto.ItemUpdateRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.exception.DuplicateResourceException;
import com.bcconstructionservices.inventory.mapper.ItemImageMapper;
import com.bcconstructionservices.inventory.mapper.ItemMapper;
import com.bcconstructionservices.inventory.repository.ItemImageRepository;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private ItemImageMapper itemImageMapper;

    @InjectMocks
    private ItemService itemService;

    /** What Spring throws when an insert/update violates the named DB constraint. */
    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("constraint violated",
                new ConstraintViolationException("constraint violated",
                        new SQLException("duplicate key"), constraintName));
    }

    private static Item item(String sku) {
        Item item = new Item();
        item.setSku(sku);
        return item;
    }

    /**
     * Two concurrent requests claiming the same SKU can both pass the
     * existsBySku pre-check; uq_item_sku rejects the second.
     */
    @Nested
    class ConcurrentDuplicateSku {

        @Test
        void createThatHitsTheSkuConstraintThrowsDuplicateResourceException() {
            ItemCreateRequest request = ItemCreateRequest.builder().sku("SKU-1").build();
            when(itemRepository.existsBySku("SKU-1")).thenReturn(false);
            when(itemMapper.toEntity(request)).thenReturn(item("SKU-1"));
            when(itemRepository.save(any(Item.class))).thenThrow(violationOf(ItemService.SKU_CONSTRAINT));

            assertThatExceptionOfType(DuplicateResourceException.class)
                    .isThrownBy(() -> itemService.createItem(request));
        }

        @Test
        void updateThatHitsTheSkuConstraintOnFlushThrowsDuplicateResourceException() {
            Item existing = item("SKU-OLD");
            ItemUpdateRequest request = ItemUpdateRequest.builder().sku("SKU-NEW").build();
            when(itemRepository.findById(7L)).thenReturn(Optional.of(existing));
            when(itemRepository.existsBySku("SKU-NEW")).thenReturn(false);
            when(itemRepository.save(existing)).thenReturn(existing);
            // An UPDATE only reaches the database on flush.
            doThrow(violationOf(ItemService.SKU_CONSTRAINT)).when(itemRepository).flush();

            assertThatExceptionOfType(DuplicateResourceException.class)
                    .isThrownBy(() -> itemService.updateItem(7L, request));
        }

        @Test
        void anUnrelatedIntegrityViolationIsRethrownUnchanged() {
            ItemCreateRequest request = ItemCreateRequest.builder().sku("SKU-1").build();
            when(itemRepository.existsBySku("SKU-1")).thenReturn(false);
            when(itemMapper.toEntity(request)).thenReturn(item("SKU-1"));
            when(itemRepository.save(any(Item.class))).thenThrow(violationOf("some_other_constraint"));

            assertThatExceptionOfType(DataIntegrityViolationException.class)
                    .isThrownBy(() -> itemService.createItem(request));
        }
    }
}
