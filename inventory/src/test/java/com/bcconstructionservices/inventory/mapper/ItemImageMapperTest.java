package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.ItemImageRequest;
import com.bcconstructionservices.inventory.dto.ItemImageResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ItemImageMapperTest {

    private ItemImageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ItemImageMapperImpl();
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapItemImageToResponseWithAllFields() {
            Item parentItem = new Item();
            parentItem.setId(42L);
            parentItem.setName("Portland Cement 40kg");

            ItemImage image = new ItemImage();
            image.setId(15L);
            image.setItem(parentItem);
            image.setImageUrl("https://cdn.example.com/cement-front.jpg");
            image.setSortOrder(2);
            image.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));

            ItemImageResponse response = mapper.toResponse(image);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(15L);
            assertThat(response.getImageUrl()).isEqualTo("https://cdn.example.com/cement-front.jpg");
            assertThat(response.getSortOrder()).isEqualTo(2);
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapRequestToEntityWithAllFields() {
            ItemImageRequest request = new ItemImageRequest();
            request.setImageUrl("https://cdn.example.com/rebar-side.jpg");
            request.setSortOrder(0);

            ItemImage entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getImageUrl()).isEqualTo("https://cdn.example.com/rebar-side.jpg");
            assertThat(entity.getSortOrder()).isEqualTo(0);
        }

        @Test
        void shouldNotSetServerManagedFieldsFromRequest() {
            ItemImageRequest request = new ItemImageRequest();
            request.setImageUrl("https://cdn.example.com/rebar-side.jpg");
            request.setSortOrder(0);

            ItemImage entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getItem()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
        }
    }
}