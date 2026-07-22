package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.LowStockItemResponse;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.InventoryStockMapper;
import com.bcconstructionservices.inventory.mapper.StockMovementMapper;
import com.bcconstructionservices.inventory.repository.InventoryStockRepository;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.StockMovementRepository;
import com.bcconstructionservices.inventory.repository.StorageLocationRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only query methods of InventoryService.
 *
 * <p>IMPORTANT - low-stock filtering: InventoryStockRepository.findLowStock()
 * applies "reorderThreshold IS NOT NULL AND quantity <= reorderThreshold"
 * inside its JPQL @Query. That means the <=-vs-< boundary and the
 * null-threshold exclusion are enforced by the database query itself, not
 * by InventoryService. A Mockito-mocked repository cannot exercise that
 * WHERE clause, so the tests in GetLowStockItemsTests verify pass-through
 * and mapping fidelity only (the service correctly maps whatever the
 * repository hands back). Genuine verification of the filtering rule
 * requires a @DataJpaTest against InventoryStockRepository with a real
 * (e.g. H2) database.
 *
 * <p>IMPORTANT - date range conversion: getMovementHistory accepts
 * LocalDate fromDate/toDate, but StockMovementRepository.search() takes
 * Instant from/to. The exact LocalDate->Instant conversion (and which
 * time zone is used) isn't known from the provided sources, so the
 * date-range tests assert on the *shape* of what's passed to the
 * repository (non-null, from before to) via an ArgumentCaptor rather than
 * pinning an exact Instant value. Tighten these once the conversion logic
 * is confirmed.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceReadMethodsTest {

    private static final Long ITEM_ID = 42L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long LOCATION_ID = 21L;

    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StorageLocationRepository storageLocationRepository;
    @Mock
    private InventoryStockMapper inventoryStockMapper;
    @Mock
    private StockMovementMapper stockMovementMapper;

    @InjectMocks
    private InventoryService inventoryService;

    private Item item;
    private Warehouse warehouse;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setId(ITEM_ID);
        item.setSku("SKU-001");
        item.setName("Portland Cement 40kg");
        item.setActive(true);

        warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setCode("WH-MAIN");
        warehouse.setName("Main Yard Warehouse");
        warehouse.setActive(true);

        pageable = PageRequest.of(0, 20);
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private InventoryStock stock(Long id, int quantity, Integer reorderThreshold) {
        InventoryStock s = new InventoryStock();
        s.setId(id);
        s.setItem(item);
        s.setWarehouse(warehouse);
        s.setQuantity(quantity);
        s.setReorderThreshold(reorderThreshold);
        return s;
    }

    private StockMovement movement(Long id, MovementType type, int quantity) {
        StockMovement m = new StockMovement();
        m.setId(id);
        m.setItem(item);
        m.setWarehouse(warehouse);
        m.setType(type);
        m.setQuantity(quantity);
        return m;
    }

    // ---------------------------------------------------------------
    // getStockLevel
    // ---------------------------------------------------------------

    @Nested
    class GetStockLevelTests {

        @Test
        void shouldReturnMappedStockLevelWhenRowExists() {
            InventoryStock existing = stock(500L, 120, 30);
            StockLevelResponse mapped = new StockLevelResponse();
            when(inventoryStockRepository
                    .findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, LOCATION_ID))
                    .thenReturn(Optional.of(existing));
            when(inventoryStockMapper.toStockLevelResponse(existing)).thenReturn(mapped);

            StockLevelResponse result =
                    inventoryService.getStockLevel(ITEM_ID, WAREHOUSE_ID, LOCATION_ID);

            assertThat(result).isSameAs(mapped);
            verify(inventoryStockMapper).toStockLevelResponse(same(existing));
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenNoRowMatches() {
            when(inventoryStockRepository
                    .findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, LOCATION_ID))
                    .thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.getStockLevel(ITEM_ID, WAREHOUSE_ID, LOCATION_ID));
        }

        @Test
        void shouldLookUpWarehouseLevelStockWhenLocationIdIsNull() {
            InventoryStock warehouseLevelStock = stock(501L, 80, 20);
            StockLevelResponse mapped = new StockLevelResponse();
            when(inventoryStockRepository
                    .findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, null))
                    .thenReturn(Optional.of(warehouseLevelStock));
            when(inventoryStockMapper.toStockLevelResponse(warehouseLevelStock)).thenReturn(mapped);

            StockLevelResponse result =
                    inventoryService.getStockLevel(ITEM_ID, WAREHOUSE_ID, null);

            assertThat(result).isSameAs(mapped);
            verify(inventoryStockRepository)
                    .findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, null);
        }
    }

    // ---------------------------------------------------------------
    // listStock
    // ---------------------------------------------------------------

    @Nested
    class ListStockTests {

        @Test
        void shouldReturnAllStockPaginatedWhenNoFiltersProvided() {
            InventoryStock stockA = stock(500L, 120, 30);
            InventoryStock stockB = stock(501L, 45, 10);
            Page<InventoryStock> page = new PageImpl<>(List.of(stockA, stockB), pageable, 2);
            StockLevelResponse responseA = new StockLevelResponse();
            StockLevelResponse responseB = new StockLevelResponse();

            when(inventoryStockRepository.search(null, null, pageable)).thenReturn(page);
            when(inventoryStockMapper.toStockLevelResponse(stockA)).thenReturn(responseA);
            when(inventoryStockMapper.toStockLevelResponse(stockB)).thenReturn(responseB);

            PageResponse<StockLevelResponse> result = inventoryService.listStock(null, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA, responseB);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void shouldFilterByItemIdOnly() {
            InventoryStock stockA = stock(500L, 120, 30);
            Page<InventoryStock> page = new PageImpl<>(List.of(stockA), pageable, 1);
            StockLevelResponse responseA = new StockLevelResponse();

            when(inventoryStockRepository.search(ITEM_ID, null, pageable)).thenReturn(page);
            when(inventoryStockMapper.toStockLevelResponse(stockA)).thenReturn(responseA);

            PageResponse<StockLevelResponse> result =
                    inventoryService.listStock(ITEM_ID, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(inventoryStockRepository).search(ITEM_ID, null, pageable);
        }

        @Test
        void shouldFilterByWarehouseIdOnly() {
            InventoryStock stockA = stock(500L, 120, 30);
            Page<InventoryStock> page = new PageImpl<>(List.of(stockA), pageable, 1);
            StockLevelResponse responseA = new StockLevelResponse();

            when(inventoryStockRepository.search(null, WAREHOUSE_ID, pageable)).thenReturn(page);
            when(inventoryStockMapper.toStockLevelResponse(stockA)).thenReturn(responseA);

            PageResponse<StockLevelResponse> result =
                    inventoryService.listStock(null, WAREHOUSE_ID, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(inventoryStockRepository).search(null, WAREHOUSE_ID, pageable);
        }

        @Test
        void shouldFilterByBothItemIdAndWarehouseId() {
            InventoryStock stockA = stock(500L, 120, 30);
            Page<InventoryStock> page = new PageImpl<>(List.of(stockA), pageable, 1);
            StockLevelResponse responseA = new StockLevelResponse();

            when(inventoryStockRepository.search(ITEM_ID, WAREHOUSE_ID, pageable)).thenReturn(page);
            when(inventoryStockMapper.toStockLevelResponse(stockA)).thenReturn(responseA);

            PageResponse<StockLevelResponse> result =
                    inventoryService.listStock(ITEM_ID, WAREHOUSE_ID, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(inventoryStockRepository).search(ITEM_ID, WAREHOUSE_ID, pageable);
        }

        @Test
        void shouldReturnEmptyPageResponseWhenNoRowsMatch() {
            Page<InventoryStock> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(inventoryStockRepository.search(ITEM_ID, WAREHOUSE_ID, pageable))
                    .thenReturn(emptyPage);

            PageResponse<StockLevelResponse> result =
                    inventoryService.listStock(ITEM_ID, WAREHOUSE_ID, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------
    // getLowStockItems
    // ---------------------------------------------------------------
    // NOTE: InventoryStockRepository.findLowStock() already applies
    // "reorderThreshold IS NOT NULL AND quantity <= reorderThreshold" inside
    // its JPQL @Query. These tests therefore mock findLowStock() to return
    // exactly the rows the real query WOULD return, and verify only that
    // InventoryService maps and returns them correctly (pass-through +
    // field-flattening fidelity). They do NOT and cannot verify the <=
    // boundary or the null-threshold exclusion - that rule lives in the
    // query itself and needs a @DataJpaTest to prove.
    //
    // InventoryStockMapper has no toLowStockItemResponse method, so these
    // tests call the real (non-mocked) InventoryService and assert directly
    // on the returned LowStockItemResponse fields rather than intercepting a
    // mapper call. Field names (itemId, itemName, sku, warehouseId,
    // warehouseName, quantity, reorderThreshold) are assumed by analogy with
    // StockLevelResponse minus location - adjust getter names if the real
    // DTO differs.

    @Nested
    class GetLowStockItemsTests {

        @Test
        void shouldMapAllRowsReturnedByLowStockQuery() {
            InventoryStock lowRow = stock(500L, 10, 30);
            InventoryStock alsoLowRow = stock(501L, 0, 5);
            // As the real query would already exclude above-threshold and
            // null-threshold rows, the mocked repository returns only the
            // qualifying subset here.
            when(inventoryStockRepository.findLowStock())
                    .thenReturn(List.of(lowRow, alsoLowRow));

            List<LowStockItemResponse> result = inventoryService.getLowStockItems();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getItemId()).isEqualTo(ITEM_ID);
            assertThat(result.get(0).getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(result.get(0).getSku()).isEqualTo("SKU-001");
            assertThat(result.get(0).getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(result.get(0).getWarehouseName()).isEqualTo("Main Yard Warehouse");
            assertThat(result.get(0).getQuantity()).isEqualTo(10);
            assertThat(result.get(0).getReorderThreshold()).isEqualTo(30);

            assertThat(result.get(1).getQuantity()).isEqualTo(0);
            assertThat(result.get(1).getReorderThreshold()).isEqualTo(5);
        }

        @Test
        void shouldMapRowWhereQuantityExactlyEqualsThreshold() {
            // The <= boundary itself is enforced by findLowStock()'s JPQL, not
            // by the service. This test only confirms the service correctly
            // maps a boundary row once the (mocked) repository returns it -
            // it does not prove the query includes quantity == threshold rows.
            InventoryStock exactlyAtThreshold = stock(500L, 30, 30);
            when(inventoryStockRepository.findLowStock())
                    .thenReturn(List.of(exactlyAtThreshold));

            List<LowStockItemResponse> result = inventoryService.getLowStockItems();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQuantity()).isEqualTo(30);
            assertThat(result.get(0).getReorderThreshold()).isEqualTo(30);
        }

        @Test
        void shouldReturnEmptyListWhenRepositoryReturnsNoLowStockRows() {
            when(inventoryStockRepository.findLowStock()).thenReturn(List.of());

            List<LowStockItemResponse> result = inventoryService.getLowStockItems();

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // getMovementHistory
    // ---------------------------------------------------------------

    @Nested
    class GetMovementHistoryTests {

        @Test
        void shouldReturnAllMovementsPaginatedWhenNoFiltersProvided() {
            StockMovement movementA = movement(9001L, MovementType.IN, 50);
            StockMovement movementB = movement(9002L, MovementType.OUT, 20);
            Page<StockMovement> page = new PageImpl<>(List.of(movementA, movementB), pageable, 2);
            StockMovementResponse responseA = new StockMovementResponse();
            StockMovementResponse responseB = new StockMovementResponse();

            when(stockMovementRepository.search(isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(page);
            when(stockMovementMapper.toResponse(movementA)).thenReturn(responseA);
            when(stockMovementMapper.toResponse(movementB)).thenReturn(responseB);

            PageResponse<StockMovementResponse> result =
                    inventoryService.getMovementHistory(null, null, null, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA, responseB);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void shouldFilterByItemId() {
            StockMovement movementA = movement(9001L, MovementType.IN, 50);
            Page<StockMovement> page = new PageImpl<>(List.of(movementA), pageable, 1);
            StockMovementResponse responseA = new StockMovementResponse();

            when(stockMovementRepository.search(eq(ITEM_ID), isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(page);
            when(stockMovementMapper.toResponse(movementA)).thenReturn(responseA);

            PageResponse<StockMovementResponse> result =
                    inventoryService.getMovementHistory(ITEM_ID, null, null, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA);
            verify(stockMovementRepository).search(eq(ITEM_ID), isNull(), isNull(), isNull(), eq(pageable));
        }

        @Test
        void shouldFilterByClosedDateRange() {
            LocalDate fromDate = LocalDate.of(2026, 7, 1);
            LocalDate toDate = LocalDate.of(2026, 7, 15);
            StockMovement movementA = movement(9001L, MovementType.TRANSFER, 30);
            Page<StockMovement> page = new PageImpl<>(List.of(movementA), pageable, 1);
            StockMovementResponse responseA = new StockMovementResponse();

            when(stockMovementRepository.search(isNull(), isNull(), any(Instant.class), any(Instant.class), eq(pageable)))
                    .thenReturn(page);
            when(stockMovementMapper.toResponse(movementA)).thenReturn(responseA);

            PageResponse<StockMovementResponse> result =
                    inventoryService.getMovementHistory(null, null, fromDate, toDate, pageable);

            assertThat(result.getContent()).containsExactly(responseA);

            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(stockMovementRepository)
                    .search(isNull(), isNull(), fromCaptor.capture(), toCaptor.capture(), eq(pageable));

            // Exact zone/offset used for the conversion isn't known from the
            // provided sources, so we assert the shape of the conversion
            // rather than a pinned Instant value: both bounds present, and
            // "from" strictly precedes "to".
            assertThat(fromCaptor.getValue()).isNotNull();
            assertThat(toCaptor.getValue()).isNotNull();
            assertThat(fromCaptor.getValue()).isBefore(toCaptor.getValue());
        }

        @Test
        void shouldFilterByOpenEndedRangeWhenOnlyFromDateProvided() {
            LocalDate fromDate = LocalDate.of(2026, 7, 1);
            StockMovement movementA = movement(9001L, MovementType.ADJUSTMENT, 5);
            Page<StockMovement> page = new PageImpl<>(List.of(movementA), pageable, 1);
            StockMovementResponse responseA = new StockMovementResponse();

            when(stockMovementRepository.search(isNull(), isNull(), any(Instant.class), isNull(), eq(pageable)))
                    .thenReturn(page);
            when(stockMovementMapper.toResponse(movementA)).thenReturn(responseA);

            PageResponse<StockMovementResponse> result =
                    inventoryService.getMovementHistory(null, null, fromDate, null, pageable);

            assertThat(result.getContent()).containsExactly(responseA);

            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(stockMovementRepository)
                    .search(isNull(), isNull(), fromCaptor.capture(), isNull(), eq(pageable));
            assertThat(fromCaptor.getValue()).isNotNull();
        }

        @Test
        void shouldReturnEmptyPageResponseWhenNoMovementsMatch() {
            Page<StockMovement> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(stockMovementRepository.search(eq(ITEM_ID), eq(WAREHOUSE_ID), isNull(), isNull(), eq(pageable)))
                    .thenReturn(emptyPage);

            PageResponse<StockMovementResponse> result =
                    inventoryService.getMovementHistory(ITEM_ID, WAREHOUSE_ID, null, null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}