package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.entity.Supplier;
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

class PurchaseOrderMapperTest {

    private PurchaseOrderMapper mapper;

    private UserLookupHelper userLookupHelper;

    private Supplier supplier;
    private Item item;

    @BeforeEach
    void setUp() {
        mapper = new PurchaseOrderMapperImpl();
        ReflectionTestUtils.setField(mapper, "purchaseOrderLineMapper", new PurchaseOrderLineMapperImpl());

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);

        supplier = new Supplier();
        supplier.setId(5L);
        supplier.setName("Acme Distribution Co.");

        item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");
        item.setSku("CEM-40KG");
    }

    private PurchaseOrder buildOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(12L);
        order.setSupplier(supplier);
        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        order.setNotes("Requested delivery before end of month");
        order.setInitiatedBy(3L);
        order.setCreatedAt(Instant.parse("2026-07-18T09:15:30Z"));
        order.setUpdatedAt(Instant.parse("2026-07-18T09:20:00Z"));
        order.setLines(new ArrayList<>());
        return order;
    }

    private PurchaseOrderLine buildLine(Long id, PurchaseOrder order, Item item, int quantity) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId(id);
        line.setPurchaseOrder(order);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapOrderToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(3L)).thenReturn("Juan Dela Cruz");
            PurchaseOrder order = buildOrder();

            PurchaseOrderResponse response = mapper.toResponse(order);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(12L);
            assertThat(response.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
            assertThat(response.getInitiatedBy()).isEqualTo(3L);
            assertThat(response.getInitiatedByName()).isEqualTo("Juan Dela Cruz");
            assertThat(response.getNotes()).isEqualTo("Requested delivery before end of month");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T09:20:00Z"));
        }

        @Test
        void shouldFlattenSupplierNameFromNestedSupplier() {
            PurchaseOrder order = buildOrder();

            PurchaseOrderResponse response = mapper.toResponse(order);

            assertThat(response.getSupplierId()).isEqualTo(5L);
            assertThat(response.getSupplierName()).isEqualTo("Acme Distribution Co.");
        }

        @Test
        void shouldMapLineItemsInOrder() {
            PurchaseOrder order = buildOrder();
            order.getLines().add(buildLine(301L, order, item, 100));

            PurchaseOrderResponse response = mapper.toResponse(order);

            assertThat(response.getLines()).hasSize(1);
            assertThat(response.getLines().get(0).getId()).isEqualTo(301L);
            assertThat(response.getLines().get(0).getItemId()).isEqualTo(42L);
            assertThat(response.getLines().get(0).getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getLines().get(0).getItemSku()).isEqualTo("CEM-40KG");
            assertThat(response.getLines().get(0).getQuantity()).isEqualTo(100);
            // receivedQuantity is ignore=true in the mapper — set by the
            // service afterward, so it must come back null here.
            assertThat(response.getLines().get(0).getReceivedQuantity()).isNull();
        }

        @Test
        void shouldMapOrderWithEmptyLinesListWithoutError() {
            PurchaseOrder order = buildOrder();

            assertThatCode(() -> mapper.toResponse(order)).doesNotThrowAnyException();

            PurchaseOrderResponse response = mapper.toResponse(order);
            assertThat(response.getLines()).isNotNull();
            assertThat(response.getLines()).isEmpty();
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            PurchaseOrderLineRequest lineRequest = new PurchaseOrderLineRequest();
            lineRequest.setItemId(42L);
            lineRequest.setQuantity(100);

            PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest();
            request.setSupplierId(5L);
            request.setNotes("Requested delivery before end of month");
            request.setLines(List.of(lineRequest));

            PurchaseOrder entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getNotes()).isEqualTo("Requested delivery before end of month");
        }

        @Test
        void shouldNotSetServerManagedOrResolvedFieldsFromCreateRequest() {
            PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest();
            request.setSupplierId(5L);
            request.setLines(List.of());

            PurchaseOrder entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getSupplier()).isNull();
            assertThat(entity.getInitiatedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteNotesWithRequestValue() {
            PurchaseOrder existing = buildOrder();

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setNotes("Updated notes");
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getNotes()).isEqualTo("Updated notes");
        }

        @Test
        void shouldClearNotesWhenRequestSendsItAsNull() {
            // Full-replacement PUT semantics — explicit null in the request
            // must clear the field, not leave it as-is.
            PurchaseOrder existing = buildOrder();
            assertThat(existing.getNotes()).isNotNull();

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setNotes(null);
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getNotes()).isNull();
        }

        @Test
        void shouldNeverChangeSupplierOrStatus() {
            PurchaseOrder existing = buildOrder();
            Supplier originalSupplier = existing.getSupplier();
            PurchaseOrderStatus originalStatus = existing.getStatus();

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setNotes("Updated notes");
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getSupplier()).isEqualTo(originalSupplier);
            assertThat(existing.getStatus()).isEqualTo(originalStatus);
        }
    }
}
