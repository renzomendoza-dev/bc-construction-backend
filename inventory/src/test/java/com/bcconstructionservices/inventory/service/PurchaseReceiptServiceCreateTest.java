package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotOpenException;
import com.bcconstructionservices.inventory.exception.ReceiptProcessingException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.exception.TransferBatchNotAwaitingPurchaseException;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptLineMapper;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptLineMapperImpl;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptMapperImpl;
import com.bcconstructionservices.inventory.repository.*;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PurchaseReceiptService.createPurchaseReceipt.
 *
 * <p>purchaseReceiptMapper is a real spy (PurchaseReceiptMapperImpl), not a
 * blank-stubbed mock, so that receiptNumber/purchaseDate/lines etc. actually
 * get copied from the request the way they do in production. Its internal
 * delegate (purchaseReceiptLineMapper) is wired manually in setUp() since
 * there's no Spring context here to do it via @Autowired.
 *
 * <p>ASSUMPTIONS (no PurchaseReceiptService source was provided, so these
 * are inferred from the stated business rules and DTO shapes; adjust the
 * corresponding stubs/captures if the real implementation differs):
 *
 * <ul>
 *   <li>PurchaseReceipt has no explicit "status"/"confirmed" field per the
 *       given entity spec, so "saved as a draft, not yet applied to stock"
 *       is demonstrated the only way observable here: zero interaction
 *       with InventoryService during createPurchaseReceipt.</li>
 *   <li>Neither PurchaseReceiptLineRequest nor PurchaseReceiptCreateRequest
 *       expose a lineTotal/totalAmount field, so there is no "already
 *       provided" branch to test — lineTotal and totalAmount are asserted
 *       to always be computed from quantity * unitCost and their sum.</li>
 *   <li>The receipt (with its lines attached) passed to
 *       PurchaseReceiptRepository.save(...) is treated as the source of
 *       truth for computed values, since that holds regardless of whether
 *       lines persist via cascade or via explicit
 *       PurchaseReceiptLineRepository.save() calls. The latter is stubbed
 *       leniently but not strictly verified, since its call shape (cascade
 *       vs. per-line) isn't known.</li>
 *   <li>ItemSupplierRepository is mocked (required for @InjectMocks) but
 *       not exercised — none of the stated business rules for this method
 *       reference it.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseReceiptServiceCreateTest {

    private static final Long SUPPLIER_ID = 7L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long CEMENT_ITEM_ID = 42L;
    private static final Long REBAR_ITEM_ID = 43L;

    @Mock
    private PurchaseReceiptRepository purchaseReceiptRepository;
    @Mock
    private PurchaseReceiptLineRepository purchaseReceiptLineRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ItemSupplierRepository itemSupplierRepository;
    @Mock
    private InventoryService inventoryService;
    @Spy
    private PurchaseReceiptMapperImpl purchaseReceiptMapper = new PurchaseReceiptMapperImpl();
    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private TransferBatchRepository transferBatchRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderService purchaseOrderService;

    @InjectMocks
    private PurchaseReceiptService purchaseReceiptService;
    @Mock
    private UserLookupHelper userLookupHelper;

    private Supplier activeSupplier;
    private Item cementItem;
    private Item rebarItem;
    private Warehouse activeWarehouse;

    @BeforeEach
    void setUp() {
        // Wire the spy's internal delegates manually — no Spring context here
        // to do it via @Autowired, so the generated fields have to be set directly.
        ReflectionTestUtils.setField(purchaseReceiptMapper, "purchaseReceiptLineMapper", new PurchaseReceiptLineMapperImpl());
        ReflectionTestUtils.setField(purchaseReceiptMapper, "userLookupHelper", userLookupHelper);

        activeSupplier = new Supplier();
        activeSupplier.setId(SUPPLIER_ID);
        activeSupplier.setName("Luzon Steel Trading");
        activeSupplier.setActive(true);

        cementItem = new Item();
        cementItem.setId(CEMENT_ITEM_ID);
        cementItem.setName("Portland Cement 40kg");
        cementItem.setActive(true);

        rebarItem = new Item();
        rebarItem.setId(REBAR_ITEM_ID);
        rebarItem.setName("Deformed Rebar 10mm x 6m");
        rebarItem.setActive(true);

        activeWarehouse = new Warehouse();
        activeWarehouse.setId(WAREHOUSE_ID);
        activeWarehouse.setName("Main Warehouse");
        activeWarehouse.setActive(true);
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private PurchaseReceiptLineRequest lineRequest(Long itemId, Integer quantity, String unitCost) {
        PurchaseReceiptLineRequest req = new PurchaseReceiptLineRequest();
        req.setItemId(itemId);
        req.setQuantity(quantity);
        req.setUnitCost(new BigDecimal(unitCost));
        return req;
    }

    private PurchaseReceiptCreateRequest createRequest(List<PurchaseReceiptLineRequest> lines) {
        PurchaseReceiptCreateRequest req = new PurchaseReceiptCreateRequest();
        req.setSupplierId(SUPPLIER_ID);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setReceiptNumber("OR-2026-004512");
        req.setPurchaseDate(LocalDate.of(2026, 7, 10));
        req.setImageUrl("https://cdn.example.com/receipts/or-2026-004512.jpg");
        req.setNotes("Bulk order for Phase 2 foundation work");
        req.setLines(lines);
        return req;
    }

    private PurchaseReceiptCreateRequest multiLineRequest() {
        return createRequest(List.of(
                lineRequest(CEMENT_ITEM_ID, 50, "245.00"),
                lineRequest(REBAR_ITEM_ID, 8, "158.75")));
    }

    /** Lenient so tests remain valid regardless of the service's exact validation order. */
    private void givenValidSupplierAndItems() {
        lenient().when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(activeSupplier));
        lenient().when(itemRepository.findById(CEMENT_ITEM_ID)).thenReturn(Optional.of(cementItem));
        lenient().when(itemRepository.findById(REBAR_ITEM_ID)).thenReturn(Optional.of(rebarItem));
        lenient().when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(activeWarehouse));
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(purchaseReceiptRepository.save(any(PurchaseReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(purchaseReceiptLineRepository.save(any(PurchaseReceiptLine.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // No toEntity() stub anymore — purchaseReceiptMapper is a real spy now,
        // so its actual mapping logic runs and correctly copies every field.
    }

    private void assertNothingWasSaved() {
        verify(purchaseReceiptRepository, never()).save(any());
        verify(purchaseReceiptLineRepository, never()).save(any());
    }

    private PurchaseReceipt captureSavedReceipt() {
        ArgumentCaptor<PurchaseReceipt> captor = ArgumentCaptor.forClass(PurchaseReceipt.class);
        verify(purchaseReceiptRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------
    // Successful creation
    // ---------------------------------------------------------------

    @Nested
    class SuccessfulCreation {

        @Test
        void shouldComputeLineTotalsAndTotalAmountForMultipleLines() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            PurchaseReceipt saved = captureSavedReceipt();

            assertThat(saved.getLines()).hasSize(2);
            assertThat(saved.getLines().get(0).getQuantity()).isEqualTo(50);
            assertThat(saved.getLines().get(0).getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("12250.00"));
            assertThat(saved.getLines().get(1).getQuantity()).isEqualTo(8);
            assertThat(saved.getLines().get(1).getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("1270.00"));

            assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("13520.00"));
        }

        @Test
        void shouldComputeCorrectlyForSingleLineReceipt() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            PurchaseReceiptCreateRequest singleLineRequest =
                    createRequest(List.of(lineRequest(CEMENT_ITEM_ID, 20, "245.00")));

            purchaseReceiptService.createPurchaseReceipt(singleLineRequest);

            PurchaseReceipt saved = captureSavedReceipt();

            assertThat(saved.getLines()).hasSize(1);
            assertThat(saved.getLines().get(0).getQuantity()).isEqualTo(20);
            assertThat(saved.getLines().get(0).getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("4900.00"));
            assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("4900.00"));
        }

        @Test
        void shouldSaveReceiptWithCorrectSupplierAndWarehouseAssociations() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            PurchaseReceipt saved = captureSavedReceipt();

            assertThat(saved.getSupplier()).isEqualTo(activeSupplier);
            assertThat(saved.getWarehouse().getId()).isEqualTo(WAREHOUSE_ID);
            assertThat(saved.getReceiptNumber()).isEqualTo("OR-2026-004512");
            assertThat(saved.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        }

        @Test
        void shouldNeverInteractWithInventoryServiceDuringCreation() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            // createPurchaseReceipt persists a draft only; InventoryStock is
            // only ever touched by confirmPurchaseReceipt.
            verifyNoInteractions(inventoryService);
        }

        @Test
        void shouldReturnResponseMappedFromTheSavedReceiptWithSupplierNameAndLines() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            PurchaseReceiptResponse mappedResponse = new PurchaseReceiptResponse();
            mappedResponse.setSupplierId(SUPPLIER_ID);
            mappedResponse.setSupplierName("Luzon Steel Trading");
            mappedResponse.setReceiptNumber("OR-2026-004512");
            when(purchaseReceiptMapper.toResponse(any(PurchaseReceipt.class))).thenReturn(mappedResponse);

            PurchaseReceiptResponse result =
                    purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            // The service must return exactly what the mapper produced...
            assertThat(result).isSameAs(mappedResponse);
            assertThat(result.getSupplierName()).isEqualTo("Luzon Steel Trading");

            // ...and the mapper must have been fed the receipt that was saved,
            // so its lines (and their flattened itemName/lineTotal fields, per
            // PurchaseReceiptLineMapper) reflect the persisted state.
            PurchaseReceipt saved = captureSavedReceipt();
            ArgumentCaptor<PurchaseReceipt> mappedCaptor = ArgumentCaptor.forClass(PurchaseReceipt.class);
            verify(purchaseReceiptMapper).toResponse(mappedCaptor.capture());
            assertThat(mappedCaptor.getValue()).isSameAs(saved);
        }
    }

    // ---------------------------------------------------------------
    // fulfillsTransferBatchId
    // ---------------------------------------------------------------

    @Nested
    class FulfillsTransferBatchIdValidation {

        private TransferBatch buildBatch(Long id, TransferBatchStatus status) {
            TransferBatch batch = new TransferBatch();
            batch.setId(id);
            batch.setStatus(status);
            return batch;
        }

        @Test
        void shouldAcceptAndPersistFulfillsTransferBatchIdWhenBatchIsAwaitingPurchase() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();
            when(transferBatchRepository.findById(42L))
                    .thenReturn(Optional.of(buildBatch(42L, TransferBatchStatus.AWAITING_PURCHASE)));

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setFulfillsTransferBatchId(42L);

            purchaseReceiptService.createPurchaseReceipt(request);

            PurchaseReceipt saved = captureSavedReceipt();
            assertThat(saved.getFulfillsTransferBatchId()).isEqualTo(42L);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenFulfillsTransferBatchIdDoesNotExist() {
            givenValidSupplierAndItems();
            when(transferBatchRepository.findById(999L)).thenReturn(Optional.empty());

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setFulfillsTransferBatchId(999L);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(request));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowTransferBatchNotAwaitingPurchaseExceptionWhenBatchStatusIsNotAwaitingPurchase() {
            givenValidSupplierAndItems();
            when(transferBatchRepository.findById(42L))
                    .thenReturn(Optional.of(buildBatch(42L, TransferBatchStatus.DRAFT)));

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setFulfillsTransferBatchId(42L);

            assertThatExceptionOfType(TransferBatchNotAwaitingPurchaseException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(request))
                    .satisfies(ex -> {
                        assertThat(ex.getTransferBatchId()).isEqualTo(42L);
                        assertThat(ex.getStatus()).isEqualTo(TransferBatchStatus.DRAFT);
                    });

            assertNothingWasSaved();
        }

        @Test
        void shouldNotInteractWithTransferBatchRepositoryWhenFulfillsTransferBatchIdIsNull() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            verifyNoInteractions(transferBatchRepository);
        }
    }

    // ---------------------------------------------------------------
    // purchaseOrderId
    // ---------------------------------------------------------------

    @Nested
    class PurchaseOrderIdValidation {

        private PurchaseOrder buildOrder(Long id, PurchaseOrderStatus status) {
            PurchaseOrder order = new PurchaseOrder();
            order.setId(id);
            order.setStatus(status);
            return order;
        }

        @Test
        void shouldAcceptAndPersistPurchaseOrderIdWhenOrderIsOpen() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();
            when(purchaseOrderRepository.findById(12L))
                    .thenReturn(Optional.of(buildOrder(12L, PurchaseOrderStatus.SUBMITTED)));

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setPurchaseOrderId(12L);

            purchaseReceiptService.createPurchaseReceipt(request);

            PurchaseReceipt saved = captureSavedReceipt();
            assertThat(saved.getPurchaseOrderId()).isEqualTo(12L);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenPurchaseOrderIdDoesNotExist() {
            givenValidSupplierAndItems();
            when(purchaseOrderRepository.findById(999L)).thenReturn(Optional.empty());

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setPurchaseOrderId(999L);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(request));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowPurchaseOrderNotOpenExceptionWhenOrderIsReceived() {
            givenValidSupplierAndItems();
            when(purchaseOrderRepository.findById(12L))
                    .thenReturn(Optional.of(buildOrder(12L, PurchaseOrderStatus.RECEIVED)));

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setPurchaseOrderId(12L);

            assertThatExceptionOfType(PurchaseOrderNotOpenException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(request))
                    .satisfies(ex -> {
                        assertThat(ex.getPurchaseOrderId()).isEqualTo(12L);
                        assertThat(ex.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
                    });

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowPurchaseOrderNotOpenExceptionWhenOrderIsClosed() {
            givenValidSupplierAndItems();
            when(purchaseOrderRepository.findById(12L))
                    .thenReturn(Optional.of(buildOrder(12L, PurchaseOrderStatus.CLOSED)));

            PurchaseReceiptCreateRequest request = multiLineRequest();
            request.setPurchaseOrderId(12L);

            assertThatExceptionOfType(PurchaseOrderNotOpenException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(request));

            assertNothingWasSaved();
        }

        @Test
        void shouldNotInteractWithPurchaseOrderRepositoryWhenPurchaseOrderIdIsNull() {
            givenValidSupplierAndItems();
            givenSavesEchoTheirArgument();

            purchaseReceiptService.createPurchaseReceipt(multiLineRequest());

            verifyNoInteractions(purchaseOrderRepository);
        }
    }

    // ---------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------

    @Nested
    class ValidationFailures {

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSupplierDoesNotExist() {
            givenValidSupplierAndItems();
            when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(multiLineRequest()));

            assertNothingWasSaved();
            verifyNoInteractions(inventoryService);
        }

        @Test
        void shouldThrowReceiptProcessingExceptionWhenALineItemDoesNotExist() {
            givenValidSupplierAndItems();
            when(itemRepository.findById(REBAR_ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ReceiptProcessingException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(multiLineRequest()))
                    .withMessageContaining("unknown item id: " + REBAR_ITEM_ID);

            assertNothingWasSaved();
            verifyNoInteractions(inventoryService);
        }

        @Test
        void shouldThrowReceiptProcessingExceptionWhenLinesListIsEmpty() {
            givenValidSupplierAndItems();

            PurchaseReceiptCreateRequest requestWithNoLines = createRequest(List.of());

            assertThatExceptionOfType(ReceiptProcessingException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(requestWithNoLines));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowReceiptProcessingExceptionWhenLinesListIsNull() {
            givenValidSupplierAndItems();

            PurchaseReceiptCreateRequest requestWithNullLines = createRequest(null);

            assertThatExceptionOfType(ReceiptProcessingException.class)
                    .isThrownBy(() -> purchaseReceiptService.createPurchaseReceipt(requestWithNullLines));

            assertNothingWasSaved();
        }
    }
}