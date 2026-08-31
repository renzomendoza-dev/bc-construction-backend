package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.dto.TransferLineItemRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferBatchMapperTest {

    private TransferBatchMapper mapper;

    private UserLookupHelper userLookupHelper;

    private Warehouse origin;
    private Warehouse destination;
    private Item item;

    @BeforeEach
    void setUp() {
        mapper = new TransferBatchMapperImpl();
        ReflectionTestUtils.setField(mapper, "transferLineItemMapper", new TransferLineItemMapperImpl());

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);

        origin = new Warehouse();
        origin.setId(1L);
        origin.setCode("WH-MAIN");
        origin.setName("Main Warehouse");

        destination = new Warehouse();
        destination.setId(2L);
        destination.setCode("WH-SITE1");
        destination.setName("Site Warehouse");

        item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private TransferBatch buildBatch() {
        TransferBatch batch = new TransferBatch();
        batch.setId(15L);
        batch.setOriginWarehouse(origin);
        batch.setDestinationWarehouse(destination);
        batch.setStatus(TransferBatchStatus.DRAFT);
        batch.setInitiatedBy(3L);
        batch.setSourceMaterialRequestId(14L);
        batch.setNotes("Weekly resupply for Sta. Maria site");
        batch.setCreatedAt(Instant.parse("2026-07-18T09:15:30Z"));
        batch.setUpdatedAt(Instant.parse("2026-07-18T09:20:00Z"));
        batch.setLineItems(new ArrayList<>());
        return batch;
    }

    private TransferLineItem buildLine(Long id, TransferBatch batch, Item item, int quantity) {
        TransferLineItem line = new TransferLineItem();
        line.setId(id);
        line.setTransferBatch(batch);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    // ---------------------------------------------------------------
    // toResponse
    // ---------------------------------------------------------------

    @Nested
    class ToResponse {

        @Test
        void shouldMapBatchToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(3L)).thenReturn("Juan Dela Cruz");
            TransferBatch batch = buildBatch();

            TransferBatchResponse response = mapper.toResponse(batch);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(15L);
            assertThat(response.getStatus()).isEqualTo(TransferBatchStatus.DRAFT);
            assertThat(response.getInitiatedBy()).isEqualTo(3L);
            assertThat(response.getInitiatedByName()).isEqualTo("Juan Dela Cruz");
            assertThat(response.getSourceMaterialRequestId()).isEqualTo(14L);
            assertThat(response.getNotes()).isEqualTo("Weekly resupply for Sta. Maria site");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T09:20:00Z"));
        }

        @Test
        void shouldFlattenOriginAndDestinationWarehouseNames() {
            TransferBatch batch = buildBatch();

            TransferBatchResponse response = mapper.toResponse(batch);

            assertThat(response.getOriginWarehouseId()).isEqualTo(1L);
            assertThat(response.getOriginWarehouseName()).isEqualTo("Main Warehouse");
            assertThat(response.getDestinationWarehouseId()).isEqualTo(2L);
            assertThat(response.getDestinationWarehouseName()).isEqualTo("Site Warehouse");
        }

        @Test
        void shouldMapLineItemsInOrder() {
            TransferBatch batch = buildBatch();
            batch.getLineItems().add(buildLine(301L, batch, item, 50));

            TransferBatchResponse response = mapper.toResponse(batch);

            assertThat(response.getLines()).hasSize(1);
            assertThat(response.getLines().get(0).getId()).isEqualTo(301L);
            assertThat(response.getLines().get(0).getItemId()).isEqualTo(42L);
            assertThat(response.getLines().get(0).getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getLines().get(0).getQuantity()).isEqualTo(50);
        }

        @Test
        void shouldMapBatchWithEmptyLineItemsListWithoutError() {
            TransferBatch batch = buildBatch();

            assertThatCode(() -> mapper.toResponse(batch)).doesNotThrowAnyException();

            TransferBatchResponse response = mapper.toResponse(batch);
            assertThat(response.getLines()).isNotNull();
            assertThat(response.getLines()).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // toEntity
    // ---------------------------------------------------------------

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            TransferLineItemRequest lineRequest = new TransferLineItemRequest();
            lineRequest.setItemId(42L);
            lineRequest.setQuantity(50);

            TransferBatchCreateRequest request = new TransferBatchCreateRequest();
            request.setOriginWarehouseId(1L);
            request.setDestinationWarehouseId(2L);
            request.setSourceMaterialRequestId(14L);
            request.setNotes("Weekly resupply for Sta. Maria site");
            request.setLines(List.of(lineRequest));

            TransferBatch entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getSourceMaterialRequestId()).isEqualTo(14L);
            assertThat(entity.getNotes()).isEqualTo("Weekly resupply for Sta. Maria site");
        }

        @Test
        void shouldNotSetServerManagedOrResolvedFieldsFromCreateRequest() {
            TransferBatchCreateRequest request = new TransferBatchCreateRequest();
            request.setOriginWarehouseId(1L);
            request.setDestinationWarehouseId(2L);
            request.setLines(List.of());

            TransferBatch entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getOriginWarehouse()).isNull();
            assertThat(entity.getDestinationWarehouse()).isNull();
            assertThat(entity.getInitiatedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }
}
