package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.MaterialRequestNotEditableException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.MaterialRequestLineItemMapperImpl;
import com.bcconstructionservices.inventory.mapper.MaterialRequestMapperImpl;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialRequestServiceTest {

    private static final Long SITE_WAREHOUSE_ID = 2L;
    private static final Long ITEM_ID = 42L;
    private static final Long CURRENT_USER_ID = 7L;
    private static final Long REQUEST_ID = 14L;

    @Mock
    private MaterialRequestRepository materialRequestRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ItemRepository itemRepository;
    @Spy
    private MaterialRequestMapperImpl materialRequestMapper = new MaterialRequestMapperImpl();
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private MaterialRequestService materialRequestService;

    @Mock
    private UserLookupHelper userLookupHelper;

    private Warehouse site;
    private Item item;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(materialRequestMapper, "materialRequestLineItemMapper",
                new MaterialRequestLineItemMapperImpl());
        ReflectionTestUtils.setField(materialRequestMapper, "userLookupHelper", userLookupHelper);

        site = new Warehouse();
        site.setId(SITE_WAREHOUSE_ID);
        site.setCode("WH-SITE1");
        site.setName("Site Warehouse");
        site.setActive(true);
        site.setType(WarehouseType.SITE);

        item = new Item();
        item.setId(ITEM_ID);
        item.setName("Portland Cement 40kg");
        item.setActive(true);
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private MaterialRequestLineItemRequest lineRequest(Long itemId, Integer quantity) {
        MaterialRequestLineItemRequest req = new MaterialRequestLineItemRequest();
        req.setItemId(itemId);
        req.setQuantityRequested(quantity);
        return req;
    }

    private MaterialRequestCreateRequest createRequest(List<MaterialRequestLineItemRequest> lines) {
        MaterialRequestCreateRequest req = new MaterialRequestCreateRequest();
        req.setSiteWarehouseId(SITE_WAREHOUSE_ID);
        req.setLines(lines);
        return req;
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(materialRequestRepository.save(any(MaterialRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MaterialRequest captureSavedRequest() {
        ArgumentCaptor<MaterialRequest> captor = ArgumentCaptor.forClass(MaterialRequest.class);
        verify(materialRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------
    // create
    // ---------------------------------------------------------------

    @Nested
    class CreateTests {

        @Test
        void shouldSaveRequestWithLinesRequestedByAndSubmittedStatus() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(site));
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(currentUserService.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
            givenSavesEchoTheirArgument();

            materialRequestService.create(createRequest(List.of(lineRequest(ITEM_ID, 50))));

            MaterialRequest saved = captureSavedRequest();
            assertThat(saved.getSite()).isEqualTo(site);
            assertThat(saved.getRequestedBy()).isEqualTo(CURRENT_USER_ID);
            assertThat(saved.getStatus()).isEqualTo(MaterialRequestStatus.SUBMITTED);
            assertThat(saved.getLineItems()).hasSize(1);
            assertThat(saved.getLineItems().get(0).getItem()).isEqualTo(item);
            assertThat(saved.getLineItems().get(0).getQuantityRequested()).isEqualTo(50);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSiteWarehouseDoesNotExist() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> materialRequestService.create(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(materialRequestRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenWarehouseIsNotTypeSite() {
            site.setType(WarehouseType.MAIN);
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(site));

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> materialRequestService.create(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(materialRequestRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenALineItemDoesNotExist() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(site));
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> materialRequestService.create(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(materialRequestRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Nested
    class UpdateTests {

        private MaterialRequest existingRequestAtStatus(MaterialRequestStatus status) {
            MaterialRequest request = new MaterialRequest();
            request.setId(REQUEST_ID);
            request.setSite(site);
            request.setRequestedBy(CURRENT_USER_ID);
            request.setDateNeeded(LocalDate.of(2026, 8, 1));
            request.setStatus(status);
            request.setNotes("Original notes");
            MaterialRequestLineItem originalLine = new MaterialRequestLineItem();
            originalLine.setId(88L);
            originalLine.setItem(item);
            originalLine.setQuantityRequested(20);
            request.setLineItems(new ArrayList<>(List.of(originalLine)));
            return request;
        }

        private MaterialRequestUpdateRequest updateRequest(List<MaterialRequestLineItemRequest> lines) {
            MaterialRequestUpdateRequest req = new MaterialRequestUpdateRequest();
            req.setDateNeeded(LocalDate.of(2026, 9, 1));
            req.setNotes("Updated notes");
            req.setLines(lines);
            return req;
        }

        @Test
        void shouldReplaceDateNeededNotesAndLineItemsWhenDraft() {
            MaterialRequest existing = existingRequestAtStatus(MaterialRequestStatus.DRAFT);
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(existing));
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            givenSavesEchoTheirArgument();

            materialRequestService.update(REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 75))));

            MaterialRequest saved = captureSavedRequest();
            assertThat(saved.getDateNeeded()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(saved.getNotes()).isEqualTo("Updated notes");
            assertThat(saved.getLineItems()).hasSize(1);
            assertThat(saved.getLineItems().get(0).getItem()).isEqualTo(item);
            assertThat(saved.getLineItems().get(0).getQuantityRequested()).isEqualTo(75);
        }

        @Test
        void shouldAllowUpdateWhenStatusIsSubmitted() {
            MaterialRequest existing = existingRequestAtStatus(MaterialRequestStatus.SUBMITTED);
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(existing));
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            givenSavesEchoTheirArgument();

            materialRequestService.update(REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 30))));

            verify(materialRequestRepository).save(any());
        }

        @Test
        void shouldRejectUpdateWhenStatusIsPartiallyFulfilled() {
            MaterialRequest existing = existingRequestAtStatus(MaterialRequestStatus.PARTIALLY_FULFILLED);
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(MaterialRequestNotEditableException.class)
                    .isThrownBy(() -> materialRequestService.update(
                            REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 30)))));

            verify(materialRequestRepository, never()).save(any());
        }

        @Test
        void shouldRejectUpdateWhenStatusIsFulfilled() {
            MaterialRequest existing = existingRequestAtStatus(MaterialRequestStatus.FULFILLED);
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(MaterialRequestNotEditableException.class)
                    .isThrownBy(() -> materialRequestService.update(
                            REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 30)))));

            verify(materialRequestRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenRequestDoesNotExistForUpdate() {
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> materialRequestService.update(
                            REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 30)))));

            verify(materialRequestRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenAnUpdatedLineItemDoesNotExist() {
            MaterialRequest existing = existingRequestAtStatus(MaterialRequestStatus.DRAFT);
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(existing));
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> materialRequestService.update(
                            REQUEST_ID, updateRequest(List.of(lineRequest(ITEM_ID, 30)))));

            verify(materialRequestRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // getById / search
    // ---------------------------------------------------------------

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnMappedResponseForExistingRequest() {
            MaterialRequest request = new MaterialRequest();
            request.setId(REQUEST_ID);
            request.setSite(site);
            request.setLineItems(List.of());
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.of(request));

            MaterialRequestResponse response = materialRequestService.getById(REQUEST_ID);

            assertThat(response.getId()).isEqualTo(REQUEST_ID);
            assertThat(response.getSiteWarehouseName()).isEqualTo("Site Warehouse");
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenRequestDoesNotExistForGetById() {
            when(materialRequestRepository.findByIdWithSite(REQUEST_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> materialRequestService.getById(REQUEST_ID));
        }

        @Test
        void shouldDelegateSearchToRepositoryAndWrapInPageResponse() {
            MaterialRequest request = new MaterialRequest();
            request.setId(REQUEST_ID);
            request.setSite(site);
            request.setLineItems(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            Page<MaterialRequest> page = new PageImpl<>(List.of(request), pageable, 1);
            when(materialRequestRepository.search(SITE_WAREHOUSE_ID, null, pageable)).thenReturn(page);

            PageResponse<MaterialRequestResponse> result =
                    materialRequestService.search(SITE_WAREHOUSE_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
