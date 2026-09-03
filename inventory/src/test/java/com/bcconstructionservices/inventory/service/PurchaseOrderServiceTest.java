package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionItem;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionSource;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionsResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.InventoryStock;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemSupplier;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import com.bcconstructionservices.inventory.entity.Supplier;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.PurchaseOrderHasReceiptsException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotDeletableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotEditableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotOpenException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.PurchaseOrderLineMapperImpl;
import com.bcconstructionservices.inventory.mapper.PurchaseOrderMapperImpl;
import com.bcconstructionservices.inventory.repository.InventoryStockRepository;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.ItemSupplierRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestLineItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.PurchaseOrderLineRepository;
import com.bcconstructionservices.inventory.repository.PurchaseOrderRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptLineRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptRepository;
import com.bcconstructionservices.inventory.repository.SupplierRepository;
import com.bcconstructionservices.inventory.repository.TransferBatchRepository;
import com.bcconstructionservices.inventory.repository.TransferLineItemRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    private static final Long SUPPLIER_ID = 5L;
    private static final Long ORDER_ID = 12L;
    private static final Long CEMENT_ITEM_ID = 42L;
    private static final Long REBAR_ITEM_ID = 43L;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemSupplierRepository itemSupplierRepository;
    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private TransferBatchRepository transferBatchRepository;
    @Mock
    private TransferLineItemRepository transferLineItemRepository;
    @Mock
    private MaterialRequestRepository materialRequestRepository;
    @Mock
    private MaterialRequestLineItemRepository materialRequestLineItemRepository;
    @Mock
    private PurchaseReceiptLineRepository purchaseReceiptLineRepository;
    @Mock
    private PurchaseReceiptRepository purchaseReceiptRepository;
    @Spy
    private PurchaseOrderMapperImpl purchaseOrderMapper = new PurchaseOrderMapperImpl();
    @Mock
    private PurchaseOrderLineRepository purchaseOrderLineRepository;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private UserLookupHelper userLookupHelper;

    private Supplier supplier;
    private Item cementItem;
    private Item rebarItem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(purchaseOrderMapper, "purchaseOrderLineMapper", new PurchaseOrderLineMapperImpl());
        ReflectionTestUtils.setField(purchaseOrderMapper, "userLookupHelper", userLookupHelper);

        supplier = new Supplier();
        supplier.setId(SUPPLIER_ID);
        supplier.setName("Acme Distribution Co.");

        cementItem = new Item();
        cementItem.setId(CEMENT_ITEM_ID);
        cementItem.setName("Portland Cement 40kg");
        cementItem.setSku("CEM-40KG");

        rebarItem = new Item();
        rebarItem.setId(REBAR_ITEM_ID);
        rebarItem.setName("Deformed Rebar 10mm");
        rebarItem.setSku("RBR-10MM");
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private PurchaseOrderLineRequest lineRequest(Long itemId, Integer quantity) {
        PurchaseOrderLineRequest req = new PurchaseOrderLineRequest();
        req.setItemId(itemId);
        req.setQuantity(quantity);
        return req;
    }

    private PurchaseOrderCreateRequest createRequest(List<PurchaseOrderLineRequest> lines) {
        PurchaseOrderCreateRequest req = new PurchaseOrderCreateRequest();
        req.setSupplierId(SUPPLIER_ID);
        req.setLines(lines);
        return req;
    }

    private PurchaseOrder buildOrder(PurchaseOrderStatus status, List<PurchaseOrderLine> lines) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setSupplier(supplier);
        order.setStatus(status);
        order.setLines(new ArrayList<>(lines));
        return order;
    }

    private PurchaseOrderLine line(PurchaseOrder order, Item item, int quantity) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId((long) (100 + quantity));
        line.setPurchaseOrder(order);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(purchaseOrderRepository.save(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PurchaseOrder captureSavedOrder() {
        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderRepository).save(captor.capture());
        return captor.getValue();
    }

    private void givenNoConfirmedReceiptsAgainst(Long orderId) {
        lenient().when(purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(orderId)).thenReturn(List.of());
    }

    // ---------------------------------------------------------------
    // createDraft
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldSaveDraftOrderWithLines() {
            when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier));
            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));
            givenSavesEchoTheirArgument();
            givenNoConfirmedReceiptsAgainst(null);
            lenient().when(purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(any())).thenReturn(List.of());

            purchaseOrderService.createDraft(createRequest(List.of(lineRequest(CEMENT_ITEM_ID, 100))));

            PurchaseOrder saved = captureSavedOrder();
            assertThat(saved.getSupplier()).isEqualTo(supplier);
            assertThat(saved.getLines()).hasSize(1);
            assertThat(saved.getLines().get(0).getItem()).isEqualTo(cementItem);
            assertThat(saved.getLines().get(0).getQuantity()).isEqualTo(100);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSupplierDoesNotExist() {
            when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.createDraft(
                            createRequest(List.of(lineRequest(CEMENT_ITEM_ID, 100)))));

            verify(purchaseOrderRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenALineItemDoesNotExist() {
            when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier));
            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.createDraft(
                            createRequest(List.of(lineRequest(CEMENT_ITEM_ID, 100)))));

            verify(purchaseOrderRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Nested
    class UpdateTests {

        @Test
        void shouldReplaceLinesAndNotesWhenDraft() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of(line(null, cementItem, 50)));
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));
            when(itemRepository.findById(REBAR_ITEM_ID)).thenReturn(Optional.of(rebarItem));
            givenSavesEchoTheirArgument();
            givenNoConfirmedReceiptsAgainst(ORDER_ID);

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setNotes("Updated notes");
            request.setLines(List.of(lineRequest(REBAR_ITEM_ID, 20)));

            purchaseOrderService.update(ORDER_ID, request);

            PurchaseOrder saved = captureSavedOrder();
            assertThat(saved.getNotes()).isEqualTo("Updated notes");
            assertThat(saved.getLines()).hasSize(1);
            assertThat(saved.getLines().get(0).getItem()).isEqualTo(rebarItem);
            assertThat(saved.getLines().get(0).getQuantity()).isEqualTo(20);
        }

        @Test
        void shouldThrowPurchaseOrderNotEditableExceptionWhenStatusIsSubmitted() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.SUBMITTED, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setLines(List.of(lineRequest(CEMENT_ITEM_ID, 10)));

            assertThatExceptionOfType(PurchaseOrderNotEditableException.class)
                    .isThrownBy(() -> purchaseOrderService.update(ORDER_ID, request))
                    .satisfies(ex -> {
                        assertThat(ex.getPurchaseOrderId()).isEqualTo(ORDER_ID);
                        assertThat(ex.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
                    });

            verify(purchaseOrderRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            when(purchaseOrderRepository.findByIdWithSupplier(999L)).thenReturn(Optional.empty());

            PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
            request.setLines(List.of(lineRequest(CEMENT_ITEM_ID, 10)));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.update(999L, request));
        }
    }

    // ---------------------------------------------------------------
    // submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldTransitionDraftToSubmitted() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));
            givenSavesEchoTheirArgument();
            givenNoConfirmedReceiptsAgainst(ORDER_ID);

            purchaseOrderService.submit(ORDER_ID);

            PurchaseOrder saved = captureSavedOrder();
            assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        }

        @Test
        void shouldThrowPurchaseOrderNotEditableExceptionWhenNotDraft() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatExceptionOfType(PurchaseOrderNotEditableException.class)
                    .isThrownBy(() -> purchaseOrderService.submit(ORDER_ID));

            verify(purchaseOrderRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // close
    // ---------------------------------------------------------------

    @Nested
    class CloseTests {

        @Test
        void shouldCloseADraftOrder() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));
            givenSavesEchoTheirArgument();
            givenNoConfirmedReceiptsAgainst(ORDER_ID);

            purchaseOrderService.close(ORDER_ID);

            assertThat(captureSavedOrder().getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
        }

        @Test
        void shouldClosseAPartiallyReceivedOrder() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));
            givenSavesEchoTheirArgument();
            givenNoConfirmedReceiptsAgainst(ORDER_ID);

            purchaseOrderService.close(ORDER_ID);

            assertThat(captureSavedOrder().getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
        }

        @Test
        void shouldThrowPurchaseOrderNotOpenExceptionWhenAlreadyReceived() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.RECEIVED, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatExceptionOfType(PurchaseOrderNotOpenException.class)
                    .isThrownBy(() -> purchaseOrderService.close(ORDER_ID));

            verify(purchaseOrderRepository, never()).save(any());
        }

        @Test
        void shouldThrowPurchaseOrderNotOpenExceptionWhenAlreadyClosed() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.CLOSED, List.of());
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatExceptionOfType(PurchaseOrderNotOpenException.class)
                    .isThrownBy(() -> purchaseOrderService.close(ORDER_ID));

            verify(purchaseOrderRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------

    @Nested
    class DeleteTests {

        @Test
        void shouldDeleteOrderWhenDraftAndNoReceiptsReferenceIt() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of());
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(purchaseReceiptRepository.existsByPurchaseOrderId(ORDER_ID)).thenReturn(false);

            purchaseOrderService.delete(ORDER_ID);

            verify(purchaseOrderRepository).delete(order);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExist() {
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.delete(ORDER_ID));

            verify(purchaseOrderRepository, never()).delete(any());
        }

        @Test
        void shouldThrowPurchaseOrderNotDeletableExceptionWhenNotDraft() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.SUBMITTED, List.of());
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatExceptionOfType(PurchaseOrderNotDeletableException.class)
                    .isThrownBy(() -> purchaseOrderService.delete(ORDER_ID))
                    .satisfies(ex -> {
                        assertThat(ex.getPurchaseOrderId()).isEqualTo(ORDER_ID);
                        assertThat(ex.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
                    });

            verify(purchaseOrderRepository, never()).delete(any());
            verify(purchaseReceiptRepository, never()).existsByPurchaseOrderId(any());
        }

        @Test
        void shouldThrowPurchaseOrderHasReceiptsExceptionWhenAReceiptReferencesADraftOrder() {
            // createPurchaseReceipt() allows linking to a DRAFT order (only
            // RECEIVED/CLOSED are rejected), so this combination is real.
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of());
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(purchaseReceiptRepository.existsByPurchaseOrderId(ORDER_ID)).thenReturn(true);

            assertThatExceptionOfType(PurchaseOrderHasReceiptsException.class)
                    .isThrownBy(() -> purchaseOrderService.delete(ORDER_ID))
                    .satisfies(ex -> assertThat(ex.getPurchaseOrderId()).isEqualTo(ORDER_ID));

            verify(purchaseOrderRepository, never()).delete(any());
        }
    }

    // ---------------------------------------------------------------
    // updateStatusFromReceipts
    // ---------------------------------------------------------------

    @Nested
    class UpdateStatusFromReceiptsTests {

        private PurchaseReceiptLine receiptLine(Item item, int quantity) {
            PurchaseReceiptLine line = new PurchaseReceiptLine();
            line.setItem(item);
            line.setQuantity(quantity);
            return line;
        }

        @Test
        void shouldSetPartiallyReceivedWhenSomeButNotAllLinesAreCovered() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.SUBMITTED,
                    List.of(line(null, cementItem, 100), line(null, rebarItem, 20)));
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(ORDER_ID))
                    .thenReturn(List.of(receiptLine(cementItem, 100)));
            givenSavesEchoTheirArgument();

            purchaseOrderService.updateStatusFromReceipts(ORDER_ID);

            assertThat(captureSavedOrder().getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }

        @Test
        void shouldSetReceivedWhenEveryLineIsFullyCovered() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED,
                    List.of(line(null, cementItem, 100)));
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(ORDER_ID))
                    .thenReturn(List.of(receiptLine(cementItem, 60), receiptLine(cementItem, 40)));
            givenSavesEchoTheirArgument();

            purchaseOrderService.updateStatusFromReceipts(ORDER_ID);

            assertThat(captureSavedOrder().getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        }

        @Test
        void shouldNoOpWhenOrderDoesNotExist() {
            when(purchaseOrderRepository.findById(999L)).thenReturn(Optional.empty());

            purchaseOrderService.updateStatusFromReceipts(999L);

            verify(purchaseOrderRepository, never()).save(any());
        }

        @Test
        void shouldNoOpWhenOrderIsAlreadyClosed() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.CLOSED, List.of(line(null, cementItem, 100)));
            when(purchaseOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            purchaseOrderService.updateStatusFromReceipts(ORDER_ID);

            verify(purchaseOrderRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // getById / search
    // ---------------------------------------------------------------

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnMappedResponseWithReceivedQuantityPerLine() {
            PurchaseOrderLine cementLine = line(null, cementItem, 100);
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.PARTIALLY_RECEIVED, List.of(cementLine));
            when(purchaseOrderRepository.findByIdWithSupplier(ORDER_ID)).thenReturn(Optional.of(order));
            when(purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(ORDER_ID))
                    .thenReturn(List.of(receiptLineFor(cementItem, 60)));

            PurchaseOrderResponse response = purchaseOrderService.getById(ORDER_ID);

            assertThat(response.getId()).isEqualTo(ORDER_ID);
            assertThat(response.getLines()).hasSize(1);
            assertThat(response.getLines().get(0).getReceivedQuantity()).isEqualTo(60);
        }

        private PurchaseReceiptLine receiptLineFor(Item item, int quantity) {
            PurchaseReceiptLine line = new PurchaseReceiptLine();
            line.setItem(item);
            line.setQuantity(quantity);
            return line;
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenOrderDoesNotExistForGetById() {
            when(purchaseOrderRepository.findByIdWithSupplier(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.getById(999L));
        }

        @Test
        void shouldDelegateSearchToRepositoryAndWrapInPageResponse() {
            PurchaseOrder order = buildOrder(PurchaseOrderStatus.DRAFT, List.of());
            Pageable pageable = PageRequest.of(0, 10);
            Page<PurchaseOrder> page = new PageImpl<>(List.of(order), pageable, 1);
            when(purchaseOrderRepository.search(SUPPLIER_ID, null, pageable)).thenReturn(page);
            givenNoConfirmedReceiptsAgainst(ORDER_ID);

            PageResponse<PurchaseOrderResponse> result = purchaseOrderService.search(SUPPLIER_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------
    // getSuggestions
    // ---------------------------------------------------------------

    @Nested
    class SuggestionsTests {

        private Warehouse mainWarehouse;

        @BeforeEach
        void setUpSuggestions() {
            mainWarehouse = new Warehouse();
            mainWarehouse.setId(1L);
            mainWarehouse.setName("Main Warehouse");

            lenient().when(supplierRepository.existsById(SUPPLIER_ID)).thenReturn(true);
            lenient().when(transferBatchRepository.findByStatus(TransferBatchStatus.AWAITING_PURCHASE))
                    .thenReturn(List.of());
            lenient().when(inventoryStockRepository.findLowStock()).thenReturn(List.of());
            lenient().when(materialRequestRepository.findByStatusIn(any())).thenReturn(List.of());
            lenient().when(itemSupplierRepository.findByItemIdAndSupplierId(any(), any())).thenReturn(Optional.empty());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSupplierDoesNotExist() {
            when(supplierRepository.existsById(999L)).thenReturn(false);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseOrderService.getSuggestions(999L));
        }

        @Test
        void shouldSuggestShortfallFromAwaitingPurchaseBatch_reCheckedAgainstCurrentStock() {
            TransferBatch blockedBatch = new TransferBatch();
            blockedBatch.setId(15L);
            blockedBatch.setOriginWarehouse(mainWarehouse);
            when(transferBatchRepository.findByStatus(TransferBatchStatus.AWAITING_PURCHASE))
                    .thenReturn(List.of(blockedBatch));

            TransferLineItem shortLine = new TransferLineItem();
            shortLine.setItem(cementItem);
            shortLine.setQuantity(100);
            when(transferLineItemRepository.findByTransferBatchId(15L)).thenReturn(List.of(shortLine));

            InventoryStock currentStock = InventoryStock.builder().quantity(30).build();
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(CEMENT_ITEM_ID, 1L, null))
                    .thenReturn(Optional.of(currentStock));
            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).hasSize(1);
            PurchaseOrderSuggestionItem suggestion = response.getSuggestions().get(0);
            assertThat(suggestion.getItemId()).isEqualTo(CEMENT_ITEM_ID);
            assertThat(suggestion.getSuggestedQuantity()).isEqualTo(70); // 100 requested - 30 available
            assertThat(suggestion.getSources()).containsExactly(PurchaseOrderSuggestionSource.AWAITING_PURCHASE_TRANSFER);
        }

        @Test
        void shouldNotSuggestAwaitingPurchaseBatchLine_whenCurrentStockNowCoversIt() {
            TransferBatch blockedBatch = new TransferBatch();
            blockedBatch.setId(15L);
            blockedBatch.setOriginWarehouse(mainWarehouse);
            when(transferBatchRepository.findByStatus(TransferBatchStatus.AWAITING_PURCHASE))
                    .thenReturn(List.of(blockedBatch));

            TransferLineItem shortLine = new TransferLineItem();
            shortLine.setItem(cementItem);
            shortLine.setQuantity(50);
            when(transferLineItemRepository.findByTransferBatchId(15L)).thenReturn(List.of(shortLine));

            // Stock arrived from elsewhere since the batch failed - now fully covers it.
            InventoryStock currentStock = InventoryStock.builder().quantity(100).build();
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(CEMENT_ITEM_ID, 1L, null))
                    .thenReturn(Optional.of(currentStock));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).isEmpty();
        }

        @Test
        void shouldSuggestLowStockItems_withDeficitUpToReorderThreshold() {
            InventoryStock lowStock = InventoryStock.builder().item(rebarItem).quantity(5).reorderThreshold(20).build();
            when(inventoryStockRepository.findLowStock()).thenReturn(List.of(lowStock));
            when(itemRepository.findById(REBAR_ITEM_ID)).thenReturn(Optional.of(rebarItem));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).hasSize(1);
            assertThat(response.getSuggestions().get(0).getItemId()).isEqualTo(REBAR_ITEM_ID);
            assertThat(response.getSuggestions().get(0).getSuggestedQuantity()).isEqualTo(15); // 20 - 5
            assertThat(response.getSuggestions().get(0).getSources())
                    .containsExactly(PurchaseOrderSuggestionSource.LOW_STOCK);
        }

        @Test
        void shouldSuggestOpenMaterialRequestItems_netOfAlreadyDispatchedQuantity() {
            MaterialRequest openRequest = new MaterialRequest();
            openRequest.setId(20L);
            openRequest.setStatus(MaterialRequestStatus.PARTIALLY_FULFILLED);
            when(materialRequestRepository.findByStatusIn(any())).thenReturn(List.of(openRequest));

            MaterialRequestLineItem requestLine = new MaterialRequestLineItem();
            requestLine.setItem(cementItem);
            requestLine.setQuantityRequested(50);
            when(materialRequestLineItemRepository.findByMaterialRequestId(20L)).thenReturn(List.of(requestLine));

            TransferBatch completedBatch = new TransferBatch();
            completedBatch.setId(16L);
            when(transferBatchRepository.findBySourceMaterialRequestIdAndStatus(20L, TransferBatchStatus.COMPLETED))
                    .thenReturn(List.of(completedBatch));
            TransferLineItem dispatchedLine = new TransferLineItem();
            dispatchedLine.setItem(cementItem);
            dispatchedLine.setQuantity(30);
            when(transferLineItemRepository.findByTransferBatchId(16L)).thenReturn(List.of(dispatchedLine));

            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).hasSize(1);
            assertThat(response.getSuggestions().get(0).getSuggestedQuantity()).isEqualTo(20); // 50 - 30
            assertThat(response.getSuggestions().get(0).getSources())
                    .containsExactly(PurchaseOrderSuggestionSource.OPEN_MATERIAL_REQUEST);
        }

        @Test
        void shouldSumQuantitiesAndCombineSourcesWhenSameItemQualifiesFromMultipleSources() {
            InventoryStock lowStock = InventoryStock.builder().item(cementItem).quantity(5).reorderThreshold(20).build();
            when(inventoryStockRepository.findLowStock()).thenReturn(List.of(lowStock));

            MaterialRequest openRequest = new MaterialRequest();
            openRequest.setId(20L);
            openRequest.setStatus(MaterialRequestStatus.SUBMITTED);
            when(materialRequestRepository.findByStatusIn(any())).thenReturn(List.of(openRequest));
            MaterialRequestLineItem requestLine = new MaterialRequestLineItem();
            requestLine.setItem(cementItem);
            requestLine.setQuantityRequested(30);
            when(materialRequestLineItemRepository.findByMaterialRequestId(20L)).thenReturn(List.of(requestLine));
            when(transferBatchRepository.findBySourceMaterialRequestIdAndStatus(20L, TransferBatchStatus.COMPLETED))
                    .thenReturn(List.of());

            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).hasSize(1);
            PurchaseOrderSuggestionItem suggestion = response.getSuggestions().get(0);
            assertThat(suggestion.getSuggestedQuantity()).isEqualTo(45); // 15 (low-stock) + 30 (open request)
            assertThat(suggestion.getSources()).containsExactlyInAnyOrder(
                    PurchaseOrderSuggestionSource.LOW_STOCK, PurchaseOrderSuggestionSource.OPEN_MATERIAL_REQUEST);
        }

        @Test
        void shouldNotFilterBySupplierLink_butShouldFlagWhetherItIsLinked() {
            InventoryStock lowStock = InventoryStock.builder().item(cementItem).quantity(5).reorderThreshold(20).build();
            when(inventoryStockRepository.findLowStock()).thenReturn(List.of(lowStock));
            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));
            when(itemSupplierRepository.findByItemIdAndSupplierId(CEMENT_ITEM_ID, SUPPLIER_ID))
                    .thenReturn(Optional.empty());

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            // Suggested even though there's no ItemSupplier link — never
            // filtered out, only flagged.
            assertThat(response.getSuggestions()).hasSize(1);
            assertThat(response.getSuggestions().get(0).isLinkedToSupplier()).isFalse();
        }

        @Test
        void shouldSortSuggestionsBySuggestedQuantityDescending() {
            InventoryStock smallDeficit =
                    InventoryStock.builder().item(cementItem).quantity(18).reorderThreshold(20).build();
            InventoryStock bigDeficit =
                    InventoryStock.builder().item(rebarItem).quantity(2).reorderThreshold(20).build();
            when(inventoryStockRepository.findLowStock()).thenReturn(List.of(smallDeficit, bigDeficit));
            when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));
            when(itemRepository.findById(REBAR_ITEM_ID)).thenReturn(Optional.of(rebarItem));

            PurchaseOrderSuggestionsResponse response = purchaseOrderService.getSuggestions(SUPPLIER_ID);

            assertThat(response.getSuggestions()).extracting(PurchaseOrderSuggestionItem::getItemId)
                    .containsExactly(REBAR_ITEM_ID, CEMENT_ITEM_ID); // 18 deficit vs 2 deficit
        }
    }
}
