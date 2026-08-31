package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialRequestMapperTest {

    private MaterialRequestMapper mapper;

    private UserLookupHelper userLookupHelper;

    private Warehouse site;
    private Item item;

    @BeforeEach
    void setUp() {
        mapper = new MaterialRequestMapperImpl();
        ReflectionTestUtils.setField(mapper, "materialRequestLineItemMapper", new MaterialRequestLineItemMapperImpl());

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);

        site = new Warehouse();
        site.setId(2L);
        site.setCode("WH-SITE1");
        site.setName("Site Warehouse - Sta. Maria Project");
        site.setType(WarehouseType.SITE);

        item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");
    }

    private MaterialRequest buildRequest() {
        MaterialRequest request = new MaterialRequest();
        request.setId(14L);
        request.setSite(site);
        request.setRequestedBy(7L);
        request.setDateNeeded(LocalDate.of(2026, 8, 1));
        request.setStatus(MaterialRequestStatus.SUBMITTED);
        request.setNotes("For the east wing pour");
        request.setCreatedAt(Instant.parse("2026-07-18T09:15:30Z"));
        request.setUpdatedAt(Instant.parse("2026-07-18T09:20:00Z"));
        request.setLineItems(new ArrayList<>());
        return request;
    }

    private MaterialRequestLineItem buildLine(Long id, MaterialRequest request, Item item, int quantity) {
        MaterialRequestLineItem line = new MaterialRequestLineItem();
        line.setId(id);
        line.setMaterialRequest(request);
        line.setItem(item);
        line.setQuantityRequested(quantity);
        return line;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapRequestToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(7L)).thenReturn("Maria Santos");
            MaterialRequest request = buildRequest();

            MaterialRequestResponse response = mapper.toResponse(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(14L);
            assertThat(response.getRequestedBy()).isEqualTo(7L);
            assertThat(response.getRequestedByName()).isEqualTo("Maria Santos");
            assertThat(response.getDateNeeded()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(response.getStatus()).isEqualTo(MaterialRequestStatus.SUBMITTED);
            assertThat(response.getNotes()).isEqualTo("For the east wing pour");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T09:20:00Z"));
        }

        @Test
        void shouldFlattenSiteWarehouseNameFromNestedSite() {
            MaterialRequest request = buildRequest();

            MaterialRequestResponse response = mapper.toResponse(request);

            assertThat(response.getSiteWarehouseId()).isEqualTo(2L);
            assertThat(response.getSiteWarehouseName()).isEqualTo("Site Warehouse - Sta. Maria Project");
        }

        @Test
        void shouldMapLineItemsInOrder() {
            MaterialRequest request = buildRequest();
            request.getLineItems().add(buildLine(88L, request, item, 50));

            MaterialRequestResponse response = mapper.toResponse(request);

            assertThat(response.getLines()).hasSize(1);
            assertThat(response.getLines().get(0).getId()).isEqualTo(88L);
            assertThat(response.getLines().get(0).getItemId()).isEqualTo(42L);
            assertThat(response.getLines().get(0).getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getLines().get(0).getQuantityRequested()).isEqualTo(50);
        }

        @Test
        void shouldMapRequestWithEmptyLineItemsListWithoutError() {
            MaterialRequest request = buildRequest();

            assertThatCode(() -> mapper.toResponse(request)).doesNotThrowAnyException();

            MaterialRequestResponse response = mapper.toResponse(request);
            assertThat(response.getLines()).isNotNull();
            assertThat(response.getLines()).isEmpty();
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            MaterialRequestLineItemRequest lineRequest = new MaterialRequestLineItemRequest();
            lineRequest.setItemId(42L);
            lineRequest.setQuantityRequested(50);

            MaterialRequestCreateRequest request = new MaterialRequestCreateRequest();
            request.setSiteWarehouseId(2L);
            request.setDateNeeded(LocalDate.of(2026, 8, 1));
            request.setNotes("For the east wing pour");
            request.setLines(List.of(lineRequest));

            MaterialRequest entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getDateNeeded()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(entity.getNotes()).isEqualTo("For the east wing pour");
        }

        @Test
        void shouldNotSetServerManagedOrResolvedFieldsFromCreateRequest() {
            MaterialRequestCreateRequest request = new MaterialRequestCreateRequest();
            request.setSiteWarehouseId(2L);
            request.setLines(List.of());

            MaterialRequest entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getSite()).isNull();
            assertThat(entity.getRequestedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteDateNeededAndNotesWithRequestValues() {
            MaterialRequest existing = buildRequest();

            MaterialRequestUpdateRequest request = new MaterialRequestUpdateRequest();
            request.setDateNeeded(LocalDate.of(2026, 9, 1));
            request.setNotes("Updated notes");
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getDateNeeded()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(existing.getNotes()).isEqualTo("Updated notes");
        }

        @Test
        void shouldClearDateNeededAndNotesWhenRequestSendsThemAsNull() {
            // Full-replacement PUT semantics, unlike WarehouseUpdateRequest's
            // partial-patch "null means don't touch" convention - explicit
            // null in the request must clear the field, not leave it as-is.
            MaterialRequest existing = buildRequest();
            assertThat(existing.getDateNeeded()).isNotNull();
            assertThat(existing.getNotes()).isNotNull();

            MaterialRequestUpdateRequest request = new MaterialRequestUpdateRequest();
            request.setDateNeeded(null);
            request.setNotes(null);
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getDateNeeded()).isNull();
            assertThat(existing.getNotes()).isNull();
        }

        @Test
        void shouldNeverChangeSiteRequestedByOrStatus() {
            MaterialRequest existing = buildRequest();
            Warehouse originalSite = existing.getSite();
            Long originalRequestedBy = existing.getRequestedBy();
            MaterialRequestStatus originalStatus = existing.getStatus();

            MaterialRequestUpdateRequest request = new MaterialRequestUpdateRequest();
            request.setNotes("Updated notes");
            request.setLines(List.of());

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getSite()).isEqualTo(originalSite);
            assertThat(existing.getRequestedBy()).isEqualTo(originalRequestedBy);
            assertThat(existing.getStatus()).isEqualTo(originalStatus);
        }
    }
}
