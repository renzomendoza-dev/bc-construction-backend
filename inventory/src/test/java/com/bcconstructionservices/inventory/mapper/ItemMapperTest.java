package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ItemMapperTest {

    private ItemMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ItemMapperImpl();
        ReflectionTestUtils.setField(mapper, "itemImageMapper", new ItemImageMapperImpl());
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private Item buildItem() {
        Item item = new Item();
        item.setId(42L);
        item.setSku("SKU-001");
        item.setName("Portland Cement 40kg");
        item.setCategory("Cement");
        item.setUnitOfMeasure("bag");
        item.setSellingPrice(new BigDecimal("289.50"));
        item.setDefaultCostPrice(new BigDecimal("245.00"));
        item.setActive(true);
        item.setImages(new ArrayList<>());
        item.setCreatedAt(Instant.parse("2026-01-15T08:30:00Z"));
        item.setUpdatedAt(Instant.parse("2026-02-20T10:45:00Z"));
        return item;
    }

    private ItemImage buildImage(Long id, Item item, String url, Integer sortOrder) {
        ItemImage image = new ItemImage();
        image.setId(id);
        image.setItem(item);
        image.setImageUrl(url);
        image.setSortOrder(sortOrder);
        image.setCreatedAt(Instant.parse("2026-01-15T08:35:00Z"));
        return image;
    }

    // ---------------------------------------------------------------
    // toResponse
    // ---------------------------------------------------------------

    @Nested
    class ToResponse {

        @Test
        void shouldMapItemToResponseWithAllFields() {
            Item item = buildItem();
            item.getImages().add(buildImage(1L, item, "https://cdn.example.com/cement-front.jpg", 0));
            item.getImages().add(buildImage(2L, item, "https://cdn.example.com/cement-back.jpg", 1));

            ItemResponse response = mapper.toResponse(item);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(42L);
            assertThat(response.getSku()).isEqualTo("SKU-001");
            assertThat(response.getName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getCategory()).isEqualTo("Cement");
            assertThat(response.getUnitOfMeasure()).isEqualTo("bag");
            assertThat(response.getSellingPrice()).isEqualByComparingTo(new BigDecimal("289.50"));
            assertThat(response.getDefaultCostPrice()).isEqualByComparingTo(new BigDecimal("245.00"));
            assertThat(response.isActive()).isTrue();
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-01-15T08:30:00Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-02-20T10:45:00Z"));
            assertThat(response.getImages()).hasSize(2);
            assertThat(response.getImages())
                    .extracting(ItemImageResponse::getImageUrl, ItemImageResponse::getSortOrder)
                    .containsExactly(
                            tuple("https://cdn.example.com/cement-front.jpg", 0),
                            tuple("https://cdn.example.com/cement-back.jpg", 1)
                    );
        }

        @Test
        void shouldMapNestedImagesToImageResponses() {
            Item item = buildItem();
            item.getImages().add(buildImage(1L, item, "https://cdn.example.com/cement-front.jpg", 0));
            item.getImages().add(buildImage(2L, item, "https://cdn.example.com/cement-back.jpg", 1));

            ItemResponse response = mapper.toResponse(item);

            assertThat(response.getImages()).hasSize(2);
            assertThat(response.getImages().get(0).getId()).isEqualTo(1L);
            assertThat(response.getImages().get(0).getImageUrl())
                    .isEqualTo("https://cdn.example.com/cement-front.jpg");
            assertThat(response.getImages().get(0).getSortOrder()).isEqualTo(0);
            assertThat(response.getImages().get(1).getId()).isEqualTo(2L);
            assertThat(response.getImages().get(1).getImageUrl())
                    .isEqualTo("https://cdn.example.com/cement-back.jpg");
            assertThat(response.getImages().get(1).getSortOrder()).isEqualTo(1);
        }

        @Test
        void shouldMapItemWithEmptyImagesToEmptyImageList() {
            Item item = buildItem();

            ItemResponse response = mapper.toResponse(item);

            assertThat(response.getImages()).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // toSummaryResponse
    // ---------------------------------------------------------------

    @Nested
    class ToSummaryResponse {

        @Test
        void shouldPickImageWithLowestSortOrderAsPrimaryImageUrl() {
            Item item = buildItem();
            // Deliberately added out of order so the test fails if the mapper
            // naively takes the first element of the list.
            item.getImages().add(buildImage(3L, item, "https://cdn.example.com/third.jpg", 5));
            item.getImages().add(buildImage(1L, item, "https://cdn.example.com/primary.jpg", 1));
            item.getImages().add(buildImage(2L, item, "https://cdn.example.com/second.jpg", 3));

            ItemSummaryResponse summary = mapper.toSummaryResponse(item);

            assertThat(summary.getPrimaryImageUrl()).isEqualTo("https://cdn.example.com/primary.jpg");
        }

        @Test
        void shouldReturnNullPrimaryImageUrlWhenNoImages() {
            Item item = buildItem();

            ItemSummaryResponse summary = mapper.toSummaryResponse(item);

            assertThat(summary.getPrimaryImageUrl()).isNull();
        }

        @Test
        void shouldUseSingleImageAsPrimaryImageUrl() {
            Item item = buildItem();
            item.getImages().add(buildImage(7L, item, "https://cdn.example.com/only-image.jpg", 4));

            ItemSummaryResponse summary = mapper.toSummaryResponse(item);

            assertThat(summary.getPrimaryImageUrl()).isEqualTo("https://cdn.example.com/only-image.jpg");
        }

        @Test
        void shouldMapAllSummaryFields() {
            Item item = buildItem();
            item.getImages().add(buildImage(1L, item, "https://cdn.example.com/primary.jpg", 0));

            ItemSummaryResponse summary = mapper.toSummaryResponse(item);

            assertThat(summary.getId()).isEqualTo(42L);
            assertThat(summary.getSku()).isEqualTo("SKU-001");
            assertThat(summary.getName()).isEqualTo("Portland Cement 40kg");
            assertThat(summary.getCategory()).isEqualTo("Cement");
            assertThat(summary.getPrimaryImageUrl()).isEqualTo("https://cdn.example.com/primary.jpg");
            assertThat(summary.isActive()).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // toEntity
    // ---------------------------------------------------------------

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            ItemCreateRequest request = new ItemCreateRequest();
            request.setSku("SKU-100");
            request.setName("Deformed Rebar 10mm x 6m");
            request.setCategory("Steel");
            request.setUnitOfMeasure("piece");
            request.setSellingPrice(new BigDecimal("185.00"));
            request.setDefaultCostPrice(new BigDecimal("158.75"));

            Item entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getSku()).isEqualTo("SKU-100");
            assertThat(entity.getName()).isEqualTo("Deformed Rebar 10mm x 6m");
            assertThat(entity.getCategory()).isEqualTo("Steel");
            assertThat(entity.getUnitOfMeasure()).isEqualTo("piece");
            assertThat(entity.getSellingPrice()).isEqualByComparingTo(new BigDecimal("185.00"));
            assertThat(entity.getDefaultCostPrice()).isEqualByComparingTo(new BigDecimal("158.75"));
        }

        @Test
        void shouldNotSetServerManagedFieldsFromCreateRequest() {
            ItemCreateRequest request = new ItemCreateRequest();
            request.setSku("SKU-100");
            request.setName("Deformed Rebar 10mm x 6m");

            Item entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    // ---------------------------------------------------------------
    // updateEntityFromRequest
    // ---------------------------------------------------------------

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteEntityFieldsWithRequestValues() {
            Item existing = buildItem();

            ItemUpdateRequest request = new ItemUpdateRequest();
            request.setSku("SKU-001-R2");
            request.setName("Portland Cement 40kg (Type 1P)");
            request.setCategory("Cement & Aggregates");
            request.setUnitOfMeasure("sack");
            request.setSellingPrice(new BigDecimal("299.00"));
            request.setDefaultCostPrice(new BigDecimal("252.50"));

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getSku()).isEqualTo("SKU-001-R2");
            assertThat(existing.getName()).isEqualTo("Portland Cement 40kg (Type 1P)");
            assertThat(existing.getCategory()).isEqualTo("Cement & Aggregates");
            assertThat(existing.getUnitOfMeasure()).isEqualTo("sack");
            assertThat(existing.getSellingPrice()).isEqualByComparingTo(new BigDecimal("299.00"));
            assertThat(existing.getDefaultCostPrice()).isEqualByComparingTo(new BigDecimal("252.50"));
        }

        @Test
        void shouldNotChangeFieldsAbsentFromUpdateRequest() {
            Item existing = buildItem();
            Long originalId = existing.getId();
            boolean originalActive = existing.isActive();
            Instant originalCreatedAt = existing.getCreatedAt();
            List<ItemImage> originalImages = existing.getImages();

            ItemUpdateRequest request = new ItemUpdateRequest();
            request.setSku("SKU-001-R2");
            request.setName("Portland Cement 40kg (Type 1P)");
            request.setCategory("Cement & Aggregates");
            request.setUnitOfMeasure("sack");
            request.setSellingPrice(new BigDecimal("299.00"));
            request.setDefaultCostPrice(new BigDecimal("252.50"));

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getId()).isEqualTo(originalId);
            assertThat(existing.isActive()).isEqualTo(originalActive);
            assertThat(existing.getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(existing.getImages()).isSameAs(originalImages);
        }
    }
}