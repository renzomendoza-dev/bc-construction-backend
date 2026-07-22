package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.dto.StockAdjustmentRequest;
import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.InsufficientStockException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptLineMapper;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptMapper;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.ItemSupplierRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptLineRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptRepository;
import com.bcconstructionservices.inventory.repository.SupplierRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PurchaseReceiptService.confirmPurchaseReceipt.
 *
 * <p>ASSUMPTIONS (no confirmPurchaseReceipt source was provided; these are
 * inferred from the stated business rules plus what the corrected
 * PurchaseReceiptServiceCreateTest revealed about the real entity shape):
 *
 * <ul>
 *   <li>PurchaseReceipt.warehouse is a Warehouse association
 *       (receipt.getWarehouse().getId()), NOT a raw warehouseId Long field —
 *       confirmed by the corrected create test's
 *       {@code saved.getWarehouse().getId()} assertion. WarehouseRepository
 *       is mocked here too since it's a genuine constructor dependency of
 *       PurchaseReceiptService, though confirmPurchaseReceipt itself likely
 *       doesn't call it (the warehouse association already lives on the
 *       fetched receipt).</li>
 *   <li>ItemSupplierRepository exposes
 *       {@code findByItemIdAndSupplierId(Long itemId, Long supplierId)}
 *       returning Optional&lt;ItemSupplier&gt; and a standard
 *       {@code save(ItemSupplier)} — this matches the Long-id-parameter
 *       convention used by the real InventoryStockRepository /
 *       StockMovementRepository @Query methods seen elsewhere in this
 *       codebase. Rename the stub if the real method differs.</li>
 *   <li>StockAdjustmentRequest.locationId is assumed null for receipt
 *       confirmation (warehouse-level intake, no specific bin/location on a
 *       purchase receipt line) — adjust if lines carry a location.</li>
 *   <li>Per-line processing (adjustStock, then the matching ItemSupplier
 *       upsert) is assumed to happen sequentially, one line fully processed
 *       before the next begins, which is what makes fail-fast behavior
 *       (scenario 7) observable at all. If lines are instead batched (all
 *       adjustStock calls first, then all ItemSupplier upserts), the
 *       fail-fast assertions in FailureScenarios will need adjusting.</li>
 *   <li>No status/confirmed field exists on PurchaseReceipt in any version
 *       of the entity seen so far (including the corrected create test), so
 *       the "confirming an already-confirmed receipt throws
 *       ReceiptProcessingException" scenario is skipped per your
 *       instructions. GAP: add it back once/if such a field is introduced.</li>
 *   <li>Since the method is @Transactional and PurchaseReceiptLine/
 *       PurchaseReceipt are presumably JPA-managed entities fetched within
 *       that transaction, per-line quantity mutations may persist via
 *       dirty-checking without an explicit repository.save(receipt) call —
 *       so these tests don't assert such a call either way.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseReceiptServiceConfirmTest {

    private static final Long RECEIPT_ID = 300L;
    private static final Long SUPPLIER_ID = 7L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long CEMENT_ITEM_ID = 42L;
    private static final Long REBAR_ITEM_ID = 43L;
    private static final Long GRAVEL_ITEM_ID = 44L;

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
    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;
    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private PurchaseReceiptService purchaseReceiptService;

    private Supplier activeSupplier;
    private Warehouse activeWarehouse;
    private Item cementItem;
    private Item rebarItem;
    private Item gravelItem;

    @BeforeEach
    void setUp() {
        activeSupplier = new Supplier();
        activeSupplier.setId(SUPPLIER_ID);
        activeSupplier.setName("Luzon Steel Trading");
        activeSupplier.setActive(true);

        activeWarehouse = new Warehouse();
        activeWarehouse.setId(WAREHOUSE_ID);
        activeWarehouse.setName("Main Warehouse");
        activeWarehouse.setActive(true);

        cementItem = new Item();
        cementItem.setId(CEMENT_ITEM_ID);
        cementItem.setName("Portland Cement 40kg");
        cementItem.setActive(true);

        rebarItem = new Item();
        rebarItem.setId(REBAR_ITEM_ID);
        rebarItem.setName("Deformed Rebar 10mm x 6m");
        rebarItem.setActive(true);

        gravelItem = new Item();
        gravelItem.setId(GRAVEL_ITEM_ID);
        gravelItem.setName("Gravel 3/4 Minus");
        gravelItem.setActive(true);
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private PurchaseReceiptLine buildLine(Long id, Item item, int quantity, String unitCost) {
        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setId(id);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal(unitCost));
        line.setLineTotal(new BigDecimal(unitCost).multiply(BigDecimal.valueOf(quantity)));
        return line;
    }

    private PurchaseReceipt buildReceipt(List<PurchaseReceiptLine> lines) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setId(RECEIPT_ID);
        receipt.setSupplier(activeSupplier);
        receipt.setWarehouse(activeWarehouse);
        receipt.setReceiptNumber("OR-2026-004512");
        receipt.setPurchaseDate(LocalDate.of(2026, 7, 10));
        receipt.setLines(lines);
        lines.forEach(line -> line.setPurchaseReceipt(receipt));
        return receipt;
    }

    private void givenReceiptExists(PurchaseReceipt receipt) {
        when(purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(RECEIPT_ID)).thenReturn(Optional.of(receipt));
    }

    /** Lenient default: every adjustStock call succeeds unless a test overrides it for a specific item. */
    private void givenAdjustStockSucceedsForAnyLine() {
        lenient().when(inventoryService.adjustStock(any(StockAdjustmentRequest.class)))
                .thenReturn(new StockMovementResponse());
    }

    /** Lenient default: no pre-existing ItemSupplier row for any item+supplier pair, unless overridden. */
    private void givenNoExistingItemSupplierRows() {
        lenient().when(itemSupplierRepository.findByItemIdAndSupplierId(any(), eq(SUPPLIER_ID)))
                .thenReturn(Optional.empty());
    }

    private void givenItemSupplierSaveEchoesArgument() {
        lenient().when(itemSupplierRepository.save(any(ItemSupplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenReceiptSaveEchoesArgument() {
        lenient().when(purchaseReceiptRepository.save(any(PurchaseReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ItemSupplier existingItemSupplier(Long id, Item item, String currentUnitCost) {
        ItemSupplier itemSupplier = new ItemSupplier();
        itemSupplier.setId(id);
        itemSupplier.setItem(item);
        itemSupplier.setSupplier(activeSupplier);
        itemSupplier.setUnitCost(new BigDecimal(currentUnitCost));
        return itemSupplier;
    }

    // ---------------------------------------------------------------
    // Successful confirmation
    // ---------------------------------------------------------------

    @Nested
    class SuccessfulConfirmation {

        @Test
        void shouldCallAdjustStockExactlyOnceForASingleLineReceipt() {
            PurchaseReceiptLine line = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceipt receipt = buildReceipt(List.of(line));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();
            givenNoExistingItemSupplierRows();
            givenItemSupplierSaveEchoesArgument();

            purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            ArgumentCaptor<StockAdjustmentRequest> captor = ArgumentCaptor.forClass(StockAdjustmentRequest.class);
            verify(inventoryService, times(1)).adjustStock(captor.capture());

            StockAdjustmentRequest request = captor.getValue();
            assertThat(request.getItemId()).isEqualTo(CEMENT_ITEM_ID);
            assertThat(request.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(request.getQuantity()).isEqualTo(50);
            assertThat(request.getType()).isEqualTo(MovementType.IN);
        }

        @Test
        void shouldCallAdjustStockOncePerLineForAMultiLineReceipt() {
            PurchaseReceiptLine lineA = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceiptLine lineB = buildLine(2L, rebarItem, 8, "158.75");
            PurchaseReceiptLine lineC = buildLine(3L, gravelItem, 15, "99.00");
            PurchaseReceipt receipt = buildReceipt(List.of(lineA, lineB, lineC));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();
            givenNoExistingItemSupplierRows();
            givenItemSupplierSaveEchoesArgument();

            purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            ArgumentCaptor<StockAdjustmentRequest> captor = ArgumentCaptor.forClass(StockAdjustmentRequest.class);
            verify(inventoryService, times(3)).adjustStock(captor.capture());

            List<StockAdjustmentRequest> requests = captor.getAllValues();
            assertThat(requests).hasSize(3);

            assertThat(requests.get(0).getItemId()).isEqualTo(CEMENT_ITEM_ID);
            assertThat(requests.get(0).getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(requests.get(0).getQuantity()).isEqualTo(50);
            assertThat(requests.get(0).getType()).isEqualTo(MovementType.IN);

            assertThat(requests.get(1).getItemId()).isEqualTo(REBAR_ITEM_ID);
            assertThat(requests.get(1).getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(requests.get(1).getQuantity()).isEqualTo(8);
            assertThat(requests.get(1).getType()).isEqualTo(MovementType.IN);

            assertThat(requests.get(2).getItemId()).isEqualTo(GRAVEL_ITEM_ID);
            assertThat(requests.get(2).getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(requests.get(2).getQuantity()).isEqualTo(15);
            assertThat(requests.get(2).getType()).isEqualTo(MovementType.IN);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenReceiptDoesNotExist() {
            when(purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(RECEIPT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID));

            verifyNoInteractions(inventoryService);
            verify(itemSupplierRepository, never()).save(any());
        }

        @Test
        void shouldReturnResponseMappedFromTheConfirmedReceipt() {
            PurchaseReceiptLine line = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceipt receipt = buildReceipt(List.of(line));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();
            givenNoExistingItemSupplierRows();
            givenItemSupplierSaveEchoesArgument();
            givenReceiptSaveEchoesArgument();

            PurchaseReceiptResponse mappedResponse = new PurchaseReceiptResponse();
            mappedResponse.setId(RECEIPT_ID);
            mappedResponse.setSupplierId(SUPPLIER_ID);
            mappedResponse.setSupplierName("Luzon Steel Trading");
            when(purchaseReceiptMapper.toResponse(any(PurchaseReceipt.class))).thenReturn(mappedResponse);

            PurchaseReceiptResponse result = purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            // The service must return exactly what the mapper produced...
            assertThat(result).isSameAs(mappedResponse);

            // ...and the mapper must have been fed the same receipt entity that
            // was fetched and processed, so downstream callers see the effect
            // of confirmation (whatever form that state takes once confirmed).
            ArgumentCaptor<PurchaseReceipt> mappedCaptor = ArgumentCaptor.forClass(PurchaseReceipt.class);
            verify(purchaseReceiptMapper).toResponse(mappedCaptor.capture());
            assertThat(mappedCaptor.getValue()).isSameAs(receipt);
        }
    }

    // ---------------------------------------------------------------
    // Supplier cost updates
    // ---------------------------------------------------------------

    @Nested
    class SupplierCostUpdates {

        @Test
        void shouldUpdateItemSupplierUnitCostToMatchLineUnitCostAfterStockIsApplied() {
            PurchaseReceiptLine line = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceipt receipt = buildReceipt(List.of(line));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();

            ItemSupplier existing = existingItemSupplier(900L, cementItem, "230.00");
            when(itemSupplierRepository.findByItemIdAndSupplierId(CEMENT_ITEM_ID, SUPPLIER_ID))
                    .thenReturn(Optional.of(existing));
            givenItemSupplierSaveEchoesArgument();

            purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            ArgumentCaptor<ItemSupplier> captor = ArgumentCaptor.forClass(ItemSupplier.class);
            verify(itemSupplierRepository).save(captor.capture());
            assertThat(captor.getValue().getUnitCost()).isEqualByComparingTo(new BigDecimal("245.00"));

            // "After each line's stock is applied" - the adjustStock call for
            // this line must precede the ItemSupplier save for the same line.
            InOrder inOrder = inOrder(inventoryService, itemSupplierRepository);
            inOrder.verify(inventoryService).adjustStock(any(StockAdjustmentRequest.class));
            inOrder.verify(itemSupplierRepository).save(any(ItemSupplier.class));
        }

        @Test
        void shouldCreateNewItemSupplierRowWhenNoneExistsForItemAndSupplierPair() {
            PurchaseReceiptLine line = buildLine(1L, rebarItem, 8, "158.75");
            PurchaseReceipt receipt = buildReceipt(List.of(line));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();
            when(itemSupplierRepository.findByItemIdAndSupplierId(REBAR_ITEM_ID, SUPPLIER_ID))
                    .thenReturn(Optional.empty());
            givenItemSupplierSaveEchoesArgument();

            purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            ArgumentCaptor<ItemSupplier> captor = ArgumentCaptor.forClass(ItemSupplier.class);
            verify(itemSupplierRepository).save(captor.capture());

            ItemSupplier saved = captor.getValue();
            assertThat(saved.getId()).isNull();
            assertThat(saved.getItem()).isEqualTo(rebarItem);
            assertThat(saved.getSupplier()).isEqualTo(activeSupplier);
            assertThat(saved.getUnitCost()).isEqualByComparingTo(new BigDecimal("158.75"));
        }

        @Test
        void shouldUpdateExistingItemSupplierRowRatherThanCreatingADuplicate() {
            PurchaseReceiptLine line = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceipt receipt = buildReceipt(List.of(line));
            givenReceiptExists(receipt);
            givenAdjustStockSucceedsForAnyLine();

            ItemSupplier existing = existingItemSupplier(900L, cementItem, "230.00");
            when(itemSupplierRepository.findByItemIdAndSupplierId(CEMENT_ITEM_ID, SUPPLIER_ID))
                    .thenReturn(Optional.of(existing));
            givenItemSupplierSaveEchoesArgument();

            purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID);

            // Exactly one save - the existing row is updated, not duplicated.
            ArgumentCaptor<ItemSupplier> captor = ArgumentCaptor.forClass(ItemSupplier.class);
            verify(itemSupplierRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(900L);
            assertThat(captor.getValue().getUnitCost()).isEqualByComparingTo(new BigDecimal("245.00"));
        }
    }

    // ---------------------------------------------------------------
    // Failure scenarios
    // ---------------------------------------------------------------

    @Nested
    class FailureScenarios {

        @Test
        void shouldThrowResourceNotFoundExceptionAndNeverCallInventoryServiceWhenReceiptDoesNotExist() {
            when(purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(RECEIPT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID));

            verifyNoInteractions(inventoryService);
            verifyNoInteractions(itemSupplierRepository);
        }

        @Test
        void shouldPropagateInsufficientStockExceptionAndStopProcessingSubsequentLines() {
            PurchaseReceiptLine lineA = buildLine(1L, cementItem, 50, "245.00");
            PurchaseReceiptLine lineB = buildLine(2L, rebarItem, 8, "158.75");
            PurchaseReceiptLine lineC = buildLine(3L, gravelItem, 15, "99.00");
            PurchaseReceipt receipt = buildReceipt(List.of(lineA, lineB, lineC));
            givenReceiptExists(receipt);
            givenNoExistingItemSupplierRows();
            givenItemSupplierSaveEchoesArgument();

            // Line A (cement) succeeds; line B (rebar) fails with insufficient
            // stock; line C (gravel) must never be attempted.
            lenient().when(inventoryService.adjustStock(any(StockAdjustmentRequest.class)))
                    .thenReturn(new StockMovementResponse());
            when(inventoryService.adjustStock(
                    argThat(req -> req != null && REBAR_ITEM_ID.equals(req.getItemId()))))
                    .thenThrow(new InsufficientStockException(REBAR_ITEM_ID, WAREHOUSE_ID, 8, 3));

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID))
                    .satisfies(ex -> {
                        assertThat(ex.getItemId()).isEqualTo(REBAR_ITEM_ID);
                        assertThat(ex.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
                        assertThat(ex.getRequestedQuantity()).isEqualTo(8);
                        assertThat(ex.getAvailableQuantity()).isEqualTo(3);
                    });

            // Fail-fast: adjustStock was attempted for cement (succeeded) and
            // rebar (failed), but never reached gravel.
            ArgumentCaptor<StockAdjustmentRequest> captor = ArgumentCaptor.forClass(StockAdjustmentRequest.class);
            verify(inventoryService, times(2)).adjustStock(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(StockAdjustmentRequest::getItemId)
                    .containsExactly(CEMENT_ITEM_ID, REBAR_ITEM_ID);

            // Only cement's ItemSupplier upsert completed before the failure;
            // rebar's never runs (it fails first) and gravel's is never reached.
            verify(itemSupplierRepository, times(1)).save(any(ItemSupplier.class));
        }
    }

    // ---------------------------------------------------------------
    // GAP: "confirming an already-confirmed receipt throws
    // ReceiptProcessingException" is intentionally not covered here.
    // PurchaseReceipt has no status/confirmed field in any version of the
    // entity seen so far (per the original spec and the corrected
    // PurchaseReceiptServiceCreateTest), so there's no observable state to
    // assert this against. Add a test here once such a field exists, e.g.:
    //
    // @Test
    // void shouldThrowReceiptProcessingExceptionWhenReceiptAlreadyConfirmed() {
    //     PurchaseReceipt alreadyConfirmed = buildReceipt(List.of(...));
    //     alreadyConfirmed.setStatus(PurchaseReceiptStatus.CONFIRMED); // hypothetical
    //     givenReceiptExists(alreadyConfirmed);
    //
    //     assertThatExceptionOfType(ReceiptProcessingException.class)
    //             .isThrownBy(() -> purchaseReceiptService.confirmPurchaseReceipt(RECEIPT_ID));
    //     verifyNoInteractions(inventoryService);
    // }
    // ---------------------------------------------------------------
}