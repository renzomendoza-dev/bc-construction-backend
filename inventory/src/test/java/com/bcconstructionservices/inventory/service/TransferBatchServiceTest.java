package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.dto.StockTransferRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.dto.TransferLineItemRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.InactiveResourceException;
import com.bcconstructionservices.inventory.exception.InsufficientStockException;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.TransferBatchMapperImpl;
import com.bcconstructionservices.inventory.mapper.TransferLineItemMapperImpl;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestLineItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.TransferBatchRepository;
import com.bcconstructionservices.inventory.repository.TransferLineItemRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferBatchServiceTest {

    private static final Long ORIGIN_WAREHOUSE_ID = 1L;
    private static final Long DESTINATION_WAREHOUSE_ID = 2L;
    private static final Long ITEM_ID = 42L;
    private static final Long BATCH_ID = 15L;
    private static final Long CURRENT_USER_ID = 3L;

    @Mock
    private TransferBatchRepository transferBatchRepository;
    @Mock
    private TransferLineItemRepository transferLineItemRepository;
    @Mock
    private MaterialRequestRepository materialRequestRepository;
    @Mock
    private MaterialRequestLineItemRepository materialRequestLineItemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private InventoryService inventoryService;
    @Spy
    private TransferBatchMapperImpl transferBatchMapper = new TransferBatchMapperImpl();
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TransferBatchService transferBatchService;

    @Mock
    private UserLookupHelper userLookupHelper;

    private Warehouse origin;
    private Warehouse destination;
    private Item item;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transferBatchMapper, "transferLineItemMapper", new TransferLineItemMapperImpl());
        ReflectionTestUtils.setField(transferBatchMapper, "userLookupHelper", userLookupHelper);

        origin = new Warehouse();
        origin.setId(ORIGIN_WAREHOUSE_ID);
        origin.setCode("WH-MAIN");
        origin.setName("Main Warehouse");
        origin.setActive(true);

        destination = new Warehouse();
        destination.setId(DESTINATION_WAREHOUSE_ID);
        destination.setCode("WH-SITE1");
        destination.setName("Site Warehouse");
        destination.setActive(true);

        item = new Item();
        item.setId(ITEM_ID);
        item.setName("Portland Cement 40kg");
        item.setActive(true);
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private TransferLineItemRequest lineRequest(Long itemId, Integer quantity) {
        TransferLineItemRequest req = new TransferLineItemRequest();
        req.setItemId(itemId);
        req.setQuantity(quantity);
        return req;
    }

    private TransferBatchCreateRequest createRequest(List<TransferLineItemRequest> lines) {
        TransferBatchCreateRequest req = new TransferBatchCreateRequest();
        req.setOriginWarehouseId(ORIGIN_WAREHOUSE_ID);
        req.setDestinationWarehouseId(DESTINATION_WAREHOUSE_ID);
        req.setLines(lines);
        return req;
    }

    private void givenValidActiveWarehouses() {
        lenient().when(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID)).thenReturn(Optional.of(origin));
        lenient().when(warehouseRepository.findById(DESTINATION_WAREHOUSE_ID)).thenReturn(Optional.of(destination));
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(transferBatchRepository.save(any(TransferBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TransferBatch captureSavedBatch() {
        ArgumentCaptor<TransferBatch> captor = ArgumentCaptor.forClass(TransferBatch.class);
        verify(transferBatchRepository).save(captor.capture());
        return captor.getValue();
    }

    private TransferLineItem line(TransferBatch batch, Item item, int quantity) {
        TransferLineItem line = new TransferLineItem();
        line.setId((long) (100 + quantity));
        line.setTransferBatch(batch);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    private TransferBatch buildDraftBatchWithLines(List<TransferLineItem> lines) {
        TransferBatch batch = new TransferBatch();
        batch.setId(BATCH_ID);
        batch.setOriginWarehouse(origin);
        batch.setDestinationWarehouse(destination);
        batch.setStatus(TransferBatchStatus.DRAFT);
        batch.setLineItems(new ArrayList<>(lines));
        return batch;
    }

    // ---------------------------------------------------------------
    // createDraft
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldSaveDraftBatchWithLinesAndInitiatedBy() {
            givenValidActiveWarehouses();
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(currentUserService.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
            givenSavesEchoTheirArgument();

            transferBatchService.createDraft(createRequest(List.of(lineRequest(ITEM_ID, 50))));

            TransferBatch saved = captureSavedBatch();
            assertThat(saved.getOriginWarehouse()).isEqualTo(origin);
            assertThat(saved.getDestinationWarehouse()).isEqualTo(destination);
            assertThat(saved.getInitiatedBy()).isEqualTo(CURRENT_USER_ID);
            assertThat(saved.getLineItems()).hasSize(1);
            assertThat(saved.getLineItems().get(0).getItem()).isEqualTo(item);
            assertThat(saved.getLineItems().get(0).getQuantity()).isEqualTo(50);
        }

        @Test
        void shouldNeverInteractWithInventoryServiceDuringDraftCreation() {
            givenValidActiveWarehouses();
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(currentUserService.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
            givenSavesEchoTheirArgument();

            transferBatchService.createDraft(createRequest(List.of(lineRequest(ITEM_ID, 50))));

            verifyNoInteractions(inventoryService);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenOriginWarehouseDoesNotExist() {
            when(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenOriginWarehouseIsInactive() {
            origin.setActive(false);
            when(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID)).thenReturn(Optional.of(origin));

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenDestinationWarehouseDoesNotExist() {
            when(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID)).thenReturn(Optional.of(origin));
            when(warehouseRepository.findById(DESTINATION_WAREHOUSE_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenDestinationWarehouseIsInactive() {
            destination.setActive(false);
            givenValidActiveWarehouses();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenOriginAndDestinationAreTheSame() {
            when(warehouseRepository.findById(ORIGIN_WAREHOUSE_ID)).thenReturn(Optional.of(origin));

            TransferBatchCreateRequest request = createRequest(List.of(lineRequest(ITEM_ID, 50)));
            request.setDestinationWarehouseId(ORIGIN_WAREHOUSE_ID);

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(request));

            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenALineItemDoesNotExist() {
            givenValidActiveWarehouses();
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> transferBatchService.createDraft(
                            createRequest(List.of(lineRequest(ITEM_ID, 50)))));

            verify(transferBatchRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldCallTransferStockOncePerLineItemAndMarkBatchCompleted() {
            TransferLineItem lineA = line(null, item, 50);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA));
            lineA.setTransferBatch(batch);

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()));
            givenSavesEchoTheirArgument();

            transferBatchService.submit(BATCH_ID);

            verify(inventoryService, times(1)).transferStock(any(StockTransferRequest.class));
            TransferBatch saved = captureSavedBatch();
            assertThat(saved.getStatus()).isEqualTo(TransferBatchStatus.COMPLETED);
        }

        @Test
        void shouldBuildStockTransferRequestFromBatchOriginDestinationAndLineQuantity() {
            TransferLineItem lineA = line(null, item, 50);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA));
            lineA.setTransferBatch(batch);

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()));
            givenSavesEchoTheirArgument();

            transferBatchService.submit(BATCH_ID);

            ArgumentCaptor<StockTransferRequest> captor = ArgumentCaptor.forClass(StockTransferRequest.class);
            verify(inventoryService).transferStock(captor.capture());
            StockTransferRequest transferRequest = captor.getValue();

            assertThat(transferRequest.getItemId()).isEqualTo(ITEM_ID);
            assertThat(transferRequest.getFromWarehouseId()).isEqualTo(ORIGIN_WAREHOUSE_ID);
            assertThat(transferRequest.getToWarehouseId()).isEqualTo(DESTINATION_WAREHOUSE_ID);
            assertThat(transferRequest.getQuantity()).isEqualTo(50);
            assertThat(transferRequest.getFromLocationId()).isNull();
            assertThat(transferRequest.getToLocationId()).isNull();
        }

        @Test
        void shouldNotSaveBatchWhenALineFailsWithInsufficientStock() {
            Item rebar = new Item();
            rebar.setId(43L);
            rebar.setName("Deformed Rebar 10mm");

            TransferLineItem lineA = line(null, item, 50);
            TransferLineItem lineB = line(null, rebar, 8);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA, lineB));
            lineA.setTransferBatch(batch);
            lineB.setTransferBatch(batch);

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()))
                    .thenThrow(new InsufficientStockException(43L, ORIGIN_WAREHOUSE_ID, 8, 3));

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> transferBatchService.submit(BATCH_ID));

            // The batch is never persisted mid-submit — @Transactional at the
            // real call site rolls back any partial InventoryService side
            // effects too; this unit test only proves the batch save never
            // happens once a line fails.
            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenBatchHasNoLineItems() {
            TransferBatch batch = buildDraftBatchWithLines(List.of());

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(List.of());

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> transferBatchService.submit(BATCH_ID));

            verifyNoInteractions(inventoryService);
            verify(transferBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenBatchDoesNotExist() {
            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> transferBatchService.submit(BATCH_ID));

            verifyNoInteractions(inventoryService);
        }

        @Test
        void shouldMarkLinkedMaterialRequestFulfilledWhenAllQuantitiesCovered() {
            TransferLineItem lineA = line(null, item, 50);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA));
            lineA.setTransferBatch(batch);
            batch.setSourceMaterialRequestId(14L);

            MaterialRequest materialRequest = new MaterialRequest();
            materialRequest.setId(14L);
            MaterialRequestLineItem requestLine = new MaterialRequestLineItem();
            requestLine.setItem(item);
            requestLine.setQuantityRequested(50);
            materialRequest.setLineItems(new ArrayList<>(List.of(requestLine)));

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()));
            when(materialRequestRepository.findByIdWithSite(14L)).thenReturn(Optional.of(materialRequest));
            when(materialRequestLineItemRepository.findByMaterialRequestId(14L))
                    .thenReturn(List.of(requestLine));
            givenSavesEchoTheirArgument();
            lenient().when(materialRequestRepository.save(any(MaterialRequest.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            transferBatchService.submit(BATCH_ID);

            ArgumentCaptor<MaterialRequest> captor = ArgumentCaptor.forClass(MaterialRequest.class);
            verify(materialRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MaterialRequestStatus.FULFILLED);
        }

        @Test
        void shouldMarkLinkedMaterialRequestPartiallyFulfilledWhenQuantityFallsShort() {
            TransferLineItem lineA = line(null, item, 30);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA));
            lineA.setTransferBatch(batch);
            batch.setSourceMaterialRequestId(14L);

            MaterialRequest materialRequest = new MaterialRequest();
            materialRequest.setId(14L);
            MaterialRequestLineItem requestLine = new MaterialRequestLineItem();
            requestLine.setItem(item);
            requestLine.setQuantityRequested(50); // more than the 30 actually transferred
            materialRequest.setLineItems(new ArrayList<>(List.of(requestLine)));

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()));
            when(materialRequestRepository.findByIdWithSite(14L)).thenReturn(Optional.of(materialRequest));
            when(materialRequestLineItemRepository.findByMaterialRequestId(14L))
                    .thenReturn(List.of(requestLine));
            givenSavesEchoTheirArgument();
            lenient().when(materialRequestRepository.save(any(MaterialRequest.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            transferBatchService.submit(BATCH_ID);

            ArgumentCaptor<MaterialRequest> captor = ArgumentCaptor.forClass(MaterialRequest.class);
            verify(materialRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MaterialRequestStatus.PARTIALLY_FULFILLED);
        }

        @Test
        void shouldNotTouchMaterialRequestWhenBatchHasNoSourceMaterialRequest() {
            TransferLineItem lineA = line(null, item, 50);
            TransferBatch batch = buildDraftBatchWithLines(List.of(lineA));
            lineA.setTransferBatch(batch);
            // sourceMaterialRequestId left null.

            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));
            when(transferLineItemRepository.findByTransferBatchId(BATCH_ID)).thenReturn(batch.getLineItems());
            when(inventoryService.transferStock(any(StockTransferRequest.class)))
                    .thenReturn(List.of(new StockMovementResponse()));
            givenSavesEchoTheirArgument();

            transferBatchService.submit(BATCH_ID);

            verifyNoInteractions(materialRequestRepository, materialRequestLineItemRepository);
        }
    }

    // ---------------------------------------------------------------
    // getById / search
    // ---------------------------------------------------------------

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnMappedResponseForExistingBatch() {
            TransferBatch batch = buildDraftBatchWithLines(List.of());
            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.of(batch));

            TransferBatchResponse response = transferBatchService.getById(BATCH_ID);

            assertThat(response.getId()).isEqualTo(BATCH_ID);
            assertThat(response.getOriginWarehouseName()).isEqualTo("Main Warehouse");
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenBatchDoesNotExistForGetById() {
            when(transferBatchRepository.findByIdWithWarehouses(BATCH_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> transferBatchService.getById(BATCH_ID));
        }

        @Test
        void shouldDelegateSearchToRepositoryAndWrapInPageResponse() {
            TransferBatch batch = buildDraftBatchWithLines(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            Page<TransferBatch> page = new PageImpl<>(List.of(batch), pageable, 1);
            when(transferBatchRepository.search(ORIGIN_WAREHOUSE_ID, null, null, pageable)).thenReturn(page);

            PageResponse<TransferBatchResponse> result =
                    transferBatchService.search(ORIGIN_WAREHOUSE_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
