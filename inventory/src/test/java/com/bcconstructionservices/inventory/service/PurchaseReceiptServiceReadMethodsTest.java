package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.PurchaseHistoryEntry;
import com.bcconstructionservices.inventory.dto.PurchaseHistoryResponse;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import com.bcconstructionservices.inventory.entity.Supplier;
import com.bcconstructionservices.inventory.entity.Warehouse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only query methods of PurchaseReceiptService.
 *
 * <p>ASSUMPTIONS (no PurchaseReceiptService source was provided for these
 * methods; inferred from stated business rules plus what the corrected
 * PurchaseReceiptServiceConfirmTest revealed):
 *
 * <ul>
 *   <li>getPurchaseReceiptById uses
 *       {@code purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(receiptId)}
 *       — confirmed by the corrected confirm test, which uses this same
 *       method (not plain findById) to fetch a single receipt.</li>
 *   <li>listPurchaseReceipts is assumed to call
 *       {@code purchaseReceiptRepository.search(supplierId, fromDate, toDate, pageable)}
 *       returning Page&lt;PurchaseReceipt&gt;, matching the "search" naming
 *       convention used by the real InventoryStockRepository /
 *       StockMovementRepository. Unlike StockMovementRepository.search
 *       (which takes Instant), purchaseDate is a LocalDate field directly on
 *       PurchaseReceipt, so no Instant conversion is assumed here — the
 *       LocalDate filters are passed straight through.</li>
 *   <li>getPurchaseHistoryForItem is assumed to call
 *       {@code purchaseReceiptLineRepository.findByItemId(itemId)} returning
 *       List&lt;PurchaseReceiptLine&gt;, and to resolve itemName via
 *       {@code itemRepository.findById(itemId)} (needed regardless of
 *       whether any history exists, so the response can still report
 *       itemName on an empty-history result).</li>
 *   <li>PurchaseHistoryResponse/PurchaseHistoryEntry field names were not
 *       given beyond the ones listed in the prompt. The list accessor is
 *       assumed to be {@code getEntries()}; rename in the tests below if the
 *       real DTO uses a different name (e.g. getPurchases()).</li>
 *   <li>ORDERING (scenario 7) is the important one to double-check: this
 *       test feeds the mocked repository call results in scrambled (not
 *       pre-sorted) order and asserts the service's response comes back
 *       sorted descending by purchaseDate. That's only meaningful if the
 *       SERVICE performs the sort itself. If sorting instead lives purely in
 *       a repository {@code @Query}'s ORDER BY clause, a real database would
 *       never hand the service unsorted rows in the first place — in that
 *       case this test should be changed to feed pre-sorted mock data
 *       (pass-through/mapping-fidelity only, same treatment as the low-stock
 *       filtering tests in InventoryServiceReadMethodsTest), with a
 *       companion @DataJpaTest verifying the actual ORDER BY.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseReceiptServiceReadMethodsTest {

    private static final Long RECEIPT_ID = 300L;
    private static final Long SUPPLIER_ID = 7L;
    private static final Long OTHER_SUPPLIER_ID = 8L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long ITEM_ID = 42L;

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

    private Supplier supplierA;
    private Supplier supplierB;
    private Warehouse warehouse;
    private Item cementItem;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        supplierA = new Supplier();
        supplierA.setId(SUPPLIER_ID);
        supplierA.setName("Luzon Steel Trading");
        supplierA.setActive(true);

        supplierB = new Supplier();
        supplierB.setId(OTHER_SUPPLIER_ID);
        supplierB.setName("Bulacan Hardware Supply");
        supplierB.setActive(true);

        warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setName("Main Warehouse");
        warehouse.setActive(true);

        cementItem = new Item();
        cementItem.setId(ITEM_ID);
        cementItem.setName("Portland Cement 40kg");
        cementItem.setActive(true);

        pageable = PageRequest.of(0, 20);
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private PurchaseReceipt buildReceipt(Long id, Supplier supplier, LocalDate purchaseDate, String receiptNumber) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setId(id);
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setPurchaseDate(purchaseDate);
        return receipt;
    }

    private PurchaseReceiptLine buildLine(Long id, PurchaseReceipt receipt, Item item, int quantity, String unitCost) {
        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setId(id);
        line.setPurchaseReceipt(receipt);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal(unitCost));
        line.setLineTotal(new BigDecimal(unitCost).multiply(BigDecimal.valueOf(quantity)));
        return line;
    }

    // ---------------------------------------------------------------
    // getPurchaseReceiptById
    // ---------------------------------------------------------------

    @Nested
    class GetPurchaseReceiptByIdTests {

        @Test
        void shouldReturnMappedReceiptWhenFound() {
            PurchaseReceipt receipt = buildReceipt(RECEIPT_ID, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");
            PurchaseReceiptResponse mapped = new PurchaseReceiptResponse();
            when(purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(RECEIPT_ID))
                    .thenReturn(Optional.of(receipt));
            when(purchaseReceiptMapper.toResponse(receipt)).thenReturn(mapped);

            PurchaseReceiptResponse result = purchaseReceiptService.getPurchaseReceiptById(RECEIPT_ID);

            assertThat(result).isSameAs(mapped);
            verify(purchaseReceiptMapper).toResponse(same(receipt));
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenReceiptDoesNotExist() {
            when(purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(RECEIPT_ID))
                    .thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.getPurchaseReceiptById(RECEIPT_ID));
        }
    }

    // ---------------------------------------------------------------
    // listPurchaseReceipts
    // ---------------------------------------------------------------

    @Nested
    class ListPurchaseReceiptsTests {

        @Test
        void shouldReturnAllReceiptsPaginatedWhenNoFiltersProvided() {
            PurchaseReceipt receiptA = buildReceipt(300L, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");
            PurchaseReceipt receiptB = buildReceipt(301L, supplierB, LocalDate.of(2026, 7, 12), "OR-2026-004513");
            Page<PurchaseReceipt> page = new PageImpl<>(List.of(receiptA, receiptB), pageable, 2);
            PurchaseReceiptResponse responseA = new PurchaseReceiptResponse();
            PurchaseReceiptResponse responseB = new PurchaseReceiptResponse();

            when(purchaseReceiptRepository.search(null, null, null, pageable)).thenReturn(page);
            when(purchaseReceiptMapper.toResponse(receiptA)).thenReturn(responseA);
            when(purchaseReceiptMapper.toResponse(receiptB)).thenReturn(responseB);

            PageResponse<PurchaseReceiptResponse> result =
                    purchaseReceiptService.listPurchaseReceipts(null, null, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA, responseB);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void shouldFilterBySupplierId() {
            PurchaseReceipt receiptA = buildReceipt(300L, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");
            Page<PurchaseReceipt> page = new PageImpl<>(List.of(receiptA), pageable, 1);
            PurchaseReceiptResponse responseA = new PurchaseReceiptResponse();

            when(purchaseReceiptRepository.search(SUPPLIER_ID, null, null, pageable)).thenReturn(page);
            when(purchaseReceiptMapper.toResponse(receiptA)).thenReturn(responseA);

            PageResponse<PurchaseReceiptResponse> result =
                    purchaseReceiptService.listPurchaseReceipts(SUPPLIER_ID, null, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(purchaseReceiptRepository).search(SUPPLIER_ID, null, null, pageable);
        }

        @Test
        void shouldFilterByDateRange() {
            LocalDate fromDate = LocalDate.of(2026, 7, 1);
            LocalDate toDate = LocalDate.of(2026, 7, 15);
            PurchaseReceipt receiptA = buildReceipt(300L, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");
            Page<PurchaseReceipt> page = new PageImpl<>(List.of(receiptA), pageable, 1);
            PurchaseReceiptResponse responseA = new PurchaseReceiptResponse();

            when(purchaseReceiptRepository.search(null, fromDate, toDate, pageable)).thenReturn(page);
            when(purchaseReceiptMapper.toResponse(receiptA)).thenReturn(responseA);

            PageResponse<PurchaseReceiptResponse> result =
                    purchaseReceiptService.listPurchaseReceipts(null, fromDate, toDate, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(purchaseReceiptRepository).search(null, fromDate, toDate, pageable);
        }

        @Test
        void shouldReturnEmptyPageResponseWhenNoReceiptsMatch() {
            Page<PurchaseReceipt> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(purchaseReceiptRepository.search(SUPPLIER_ID, null, null, pageable)).thenReturn(emptyPage);

            PageResponse<PurchaseReceiptResponse> result =
                    purchaseReceiptService.listPurchaseReceipts(SUPPLIER_ID, null, null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------
    // getPurchaseHistoryForItem
    // ---------------------------------------------------------------

    @Nested
    class GetPurchaseHistoryForItemTests {

        @Test
        void shouldMapEntriesInTheOrderReturnedByTheRepository() {
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(cementItem));

            PurchaseReceipt oldestReceipt = buildReceipt(300L, supplierA, LocalDate.of(2026, 5, 1), "OR-2026-004100");
            PurchaseReceipt middleReceipt = buildReceipt(301L, supplierB, LocalDate.of(2026, 6, 15), "OR-2026-004300");
            PurchaseReceipt mostRecentReceipt = buildReceipt(302L, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");

            PurchaseReceiptLine oldestLine = buildLine(1L, oldestReceipt, cementItem, 40, "230.00");
            PurchaseReceiptLine middleLine = buildLine(2L, middleReceipt, cementItem, 60, "238.00");
            PurchaseReceiptLine mostRecentLine = buildLine(3L, mostRecentReceipt, cementItem, 50, "245.00");

            // Sorting is Spring Data's job via the OrderByReceiptPurchaseDateDesc
            // query derivation — real Postgres does this in the query itself, so
            // the mock is stubbed pre-sorted to match what production actually returns.
            // This test only verifies the service maps/passes through in that order,
            // not that it re-sorts (it doesn't and shouldn't).
            when(purchaseReceiptLineRepository.findByItemIdOrderByReceiptPurchaseDateDesc(ITEM_ID))
                    .thenReturn(List.of(mostRecentLine, middleLine, oldestLine));

            PurchaseHistoryResponse result = purchaseReceiptService.getPurchaseHistoryForItem(ITEM_ID);

            assertThat(result.getPurchases()).hasSize(3);
            assertThat(result.getPurchases())
                    .extracting(PurchaseHistoryEntry::getReceiptId)
                    .containsExactly(302L, 301L, 300L);
            assertThat(result.getPurchases())
                    .extracting(PurchaseHistoryEntry::getPurchaseDate)
                    .containsExactly(
                            LocalDate.of(2026, 7, 10),
                            LocalDate.of(2026, 6, 15),
                            LocalDate.of(2026, 5, 1));
        }

        @Test
        void shouldMapSupplierNameQuantityAndUnitCostForEachEntry() {
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(cementItem));

            PurchaseReceipt receipt = buildReceipt(300L, supplierA, LocalDate.of(2026, 7, 10), "OR-2026-004512");
            PurchaseReceiptLine line = buildLine(1L, receipt, cementItem, 50, "245.00");
            when(purchaseReceiptLineRepository.findByItemIdOrderByReceiptPurchaseDateDesc(ITEM_ID)).thenReturn(List.of(line));

            PurchaseHistoryResponse result = purchaseReceiptService.getPurchaseHistoryForItem(ITEM_ID);

            assertThat(result.getItemId()).isEqualTo(ITEM_ID);
            assertThat(result.getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(result.getPurchases()).hasSize(1);

            PurchaseHistoryEntry entry = result.getPurchases().get(0);
            assertThat(entry.getReceiptId()).isEqualTo(300L);
            assertThat(entry.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(entry.getSupplierName()).isEqualTo("Luzon Steel Trading");
            assertThat(entry.getQuantity()).isEqualTo(50);
            assertThat(entry.getUnitCost()).isEqualByComparingTo(new BigDecimal("245.00"));
        }

        @Test
        void shouldReturnEmptyEntriesListWhenItemHasNoPurchaseHistory() {
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(cementItem));
            when(purchaseReceiptLineRepository.findByItemIdOrderByReceiptPurchaseDateDesc(ITEM_ID)).thenReturn(List.of());

            PurchaseHistoryResponse result = purchaseReceiptService.getPurchaseHistoryForItem(ITEM_ID);

            assertThat(result).isNotNull();
            assertThat(result.getItemId()).isEqualTo(ITEM_ID);
            assertThat(result.getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(result.getPurchases()).isNotNull();
            assertThat(result.getPurchases()).isEmpty();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> purchaseReceiptService.getPurchaseHistoryForItem(ITEM_ID));
        }
    }
}