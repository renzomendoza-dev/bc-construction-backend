package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.InactiveResourceException;
import com.bcconstructionservices.inventory.exception.InsufficientStockException;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InventoryService.transferWarehouseStock — the warehouse-total
 * variant used by TransferBatchService.submit, distinct from
 * InventoryServiceTransferStockTest's single-location transferStock.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTransferWarehouseStockTest {

    private static final Long ITEM_ID = 42L;
    private static final Long FROM_WAREHOUSE_ID = 3L;
    private static final Long TO_WAREHOUSE_ID = 4L;

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

    private Item activeItem;
    private Warehouse fromWarehouse;
    private Warehouse toWarehouse;

    @BeforeEach
    void setUp() {
        activeItem = new Item();
        activeItem.setId(ITEM_ID);
        activeItem.setSku("SKU-001");
        activeItem.setName("Portland Cement 40kg");
        activeItem.setActive(true);

        fromWarehouse = new Warehouse();
        fromWarehouse.setId(FROM_WAREHOUSE_ID);
        fromWarehouse.setCode("WH-MAIN");
        fromWarehouse.setName("Main Yard Warehouse");
        fromWarehouse.setActive(true);

        toWarehouse = new Warehouse();
        toWarehouse.setId(TO_WAREHOUSE_ID);
        toWarehouse.setCode("WH-NORTH");
        toWarehouse.setName("North Satellite Warehouse");
        toWarehouse.setActive(true);
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private StorageLocation location(Long id, Warehouse warehouse, String code) {
        StorageLocation location = new StorageLocation();
        location.setId(id);
        location.setWarehouse(warehouse);
        location.setCode(code);
        return location;
    }

    private InventoryStock stock(Long id, Warehouse warehouse, StorageLocation location, int quantity) {
        InventoryStock s = new InventoryStock();
        s.setId(id);
        s.setItem(activeItem);
        s.setWarehouse(warehouse);
        s.setLocation(location);
        s.setQuantity(quantity);
        return s;
    }

    private void givenValidActiveReferences() {
        lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(java.util.Optional.of(activeItem));
        lenient().when(warehouseRepository.findById(FROM_WAREHOUSE_ID)).thenReturn(java.util.Optional.of(fromWarehouse));
        lenient().when(warehouseRepository.findById(TO_WAREHOUSE_ID)).thenReturn(java.util.Optional.of(toWarehouse));
    }

    private void givenOriginStocks(InventoryStock... stocks) {
        when(inventoryStockRepository.findAllByItemAndWarehouse(ITEM_ID, FROM_WAREHOUSE_ID))
                .thenReturn(List.of(stocks));
    }

    private void givenNoExistingDestinationStock() {
        lenient().when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, TO_WAREHOUSE_ID, null))
                .thenReturn(java.util.Optional.empty());
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(inventoryStockRepository.save(any(InventoryStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stockMovementMapper.toResponse(any(StockMovement.class)))
                .thenReturn(new StockMovementResponse());
    }

    private void assertNothingWasSaved() {
        verify(inventoryStockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Successful transfers
    // ---------------------------------------------------------------

    @Nested
    class SuccessfulTransfers {

        @Test
        void shouldDebitTheNoLocationBucketWhenItAloneCoversTheQuantity() {
            givenValidActiveReferences();
            givenOriginStocks(stock(500L, fromWarehouse, null, 100));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, null))
                    .thenReturn(java.util.Optional.of(stock(500L, fromWarehouse, null, 100)));
            givenNoExistingDestinationStock();
            givenSavesEchoTheirArgument();

            inventoryService.transferWarehouseStock(ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 40);

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository, times(2)).save(stockCaptor.capture());
            InventoryStock savedOrigin = stockCaptor.getAllValues().stream()
                    .filter(s -> s.getWarehouse().getId().equals(FROM_WAREHOUSE_ID)).findFirst().orElseThrow();
            InventoryStock savedDestination = stockCaptor.getAllValues().stream()
                    .filter(s -> s.getWarehouse().getId().equals(TO_WAREHOUSE_ID)).findFirst().orElseThrow();

            assertThat(savedOrigin.getQuantity()).isEqualTo(60);
            assertThat(savedDestination.getLocation()).isNull();
            assertThat(savedDestination.getQuantity()).isEqualTo(40);
        }

        @Test
        void shouldSumAcrossEveryLocationRatherThanCheckingOnlyTheNoLocationBucket() {
            // The exact bug being fixed: real stock sitting at a named location
            // (not the no-location bucket) must still count toward "does the
            // warehouse have enough" and be usable to cover the line.
            StorageLocation binA1 = location(10L, fromWarehouse, "A1");
            givenValidActiveReferences();
            givenOriginStocks(stock(500L, fromWarehouse, binA1, 50));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, 10L))
                    .thenReturn(java.util.Optional.of(stock(500L, fromWarehouse, binA1, 50)));
            givenNoExistingDestinationStock();
            givenSavesEchoTheirArgument();

            inventoryService.transferWarehouseStock(ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 50);

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository, times(2)).save(stockCaptor.capture());
            InventoryStock savedOrigin = stockCaptor.getAllValues().stream()
                    .filter(s -> s.getWarehouse().getId().equals(FROM_WAREHOUSE_ID)).findFirst().orElseThrow();
            assertThat(savedOrigin.getLocation()).isEqualTo(binA1);
            assertThat(savedOrigin.getQuantity()).isEqualTo(0);
        }

        @Test
        void shouldDrainNoLocationBucketFirstThenLocationsByIdAscendingWhenSplitAcrossMultiple() {
            StorageLocation binA1 = location(10L, fromWarehouse, "A1");
            StorageLocation binA2 = location(11L, fromWarehouse, "A2");
            InventoryStock noLocationStock = stock(500L, fromWarehouse, null, 5);
            InventoryStock a1Stock = stock(501L, fromWarehouse, binA1, 30);
            InventoryStock a2Stock = stock(502L, fromWarehouse, binA2, 20);

            givenValidActiveReferences();
            // Deliberately returned out of drain order to prove the service sorts.
            givenOriginStocks(a2Stock, noLocationStock, a1Stock);
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, null))
                    .thenReturn(java.util.Optional.of(noLocationStock));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, 10L))
                    .thenReturn(java.util.Optional.of(a1Stock));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, 11L))
                    .thenReturn(java.util.Optional.of(a2Stock));
            givenNoExistingDestinationStock();
            givenSavesEchoTheirArgument();

            // 5 (no-location) + 30 (A1) + 15 of A2's 20 = 50.
            inventoryService.transferWarehouseStock(ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 50);

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository, times(4)).save(stockCaptor.capture());
            List<InventoryStock> savedOriginStocks = stockCaptor.getAllValues().stream()
                    .filter(s -> s.getWarehouse().getId().equals(FROM_WAREHOUSE_ID)).toList();

            InventoryStock savedNoLocation = savedOriginStocks.stream()
                    .filter(s -> s.getLocation() == null).findFirst().orElseThrow();
            InventoryStock savedA1 = savedOriginStocks.stream()
                    .filter(s -> binA1.equals(s.getLocation())).findFirst().orElseThrow();
            InventoryStock savedA2 = savedOriginStocks.stream()
                    .filter(s -> binA2.equals(s.getLocation())).findFirst().orElseThrow();

            assertThat(savedNoLocation.getQuantity()).isEqualTo(0);
            assertThat(savedA1.getQuantity()).isEqualTo(0);
            assertThat(savedA2.getQuantity()).isEqualTo(5); // 20 - 15 drained

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository, times(4)).save(movementCaptor.capture());
            List<StockMovement> originMovements = movementCaptor.getAllValues().stream()
                    .filter(m -> m.getWarehouse().getId().equals(FROM_WAREHOUSE_ID)).toList();

            assertThat(originMovements).hasSize(3);
            StockMovement noLocationMovement = originMovements.stream()
                    .filter(m -> m.getFromLocation() == null).findFirst().orElseThrow();
            StockMovement a1Movement = originMovements.stream()
                    .filter(m -> binA1.equals(m.getFromLocation())).findFirst().orElseThrow();
            StockMovement a2Movement = originMovements.stream()
                    .filter(m -> binA2.equals(m.getFromLocation())).findFirst().orElseThrow();

            assertThat(noLocationMovement.getQuantity()).isEqualTo(5);
            assertThat(a1Movement.getQuantity()).isEqualTo(30);
            assertThat(a2Movement.getQuantity()).isEqualTo(15);
            assertThat(originMovements).allSatisfy(m -> {
                assertThat(m.getType()).isEqualTo(MovementType.TRANSFER);
                assertThat(m.getToLocation()).isNull();
                // The no-location-bucket origin row above has fromLocation
                // AND toLocation both null here — exactly as ambiguous, by
                // location alone, as the destination row below. direction
                // is what actually tells them apart.
                assertThat(m.getDirection()).isEqualTo(MovementDirection.OUT);
            });
        }

        @Test
        void shouldDepositTheFullQuantityIntoDestinationsNoLocationBucketAsOneMovement() {
            givenValidActiveReferences();
            givenOriginStocks(stock(500L, fromWarehouse, null, 100));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, null))
                    .thenReturn(java.util.Optional.of(stock(500L, fromWarehouse, null, 100)));
            givenNoExistingDestinationStock();
            givenSavesEchoTheirArgument();

            inventoryService.transferWarehouseStock(ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 40);

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
            StockMovement destinationMovement = movementCaptor.getAllValues().stream()
                    .filter(m -> m.getWarehouse().getId().equals(TO_WAREHOUSE_ID)).findFirst().orElseThrow();

            assertThat(destinationMovement.getType()).isEqualTo(MovementType.TRANSFER);
            assertThat(destinationMovement.getFromLocation()).isNull();
            assertThat(destinationMovement.getToLocation()).isNull();
            // Same fromLocation/toLocation shape (both null) as an origin
            // row draining the no-location bucket — direction is the only
            // reliable signal distinguishing this destination row from that.
            assertThat(destinationMovement.getDirection()).isEqualTo(MovementDirection.IN);
            assertThat(destinationMovement.getQuantity()).isEqualTo(40);
        }

        @Test
        void shouldReturnResponsesMappedFromEverySavedMovement() {
            givenValidActiveReferences();
            givenOriginStocks(stock(500L, fromWarehouse, null, 100));
            when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, FROM_WAREHOUSE_ID, null))
                    .thenReturn(java.util.Optional.of(stock(500L, fromWarehouse, null, 100)));
            givenNoExistingDestinationStock();
            givenSavesEchoTheirArgument();

            StockMovementResponse outResponse = new StockMovementResponse();
            StockMovementResponse inResponse = new StockMovementResponse();
            when(stockMovementMapper.toResponse(any(StockMovement.class))).thenReturn(outResponse, inResponse);

            List<StockMovementResponse> result =
                    inventoryService.transferWarehouseStock(ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 40);

            assertThat(result).containsExactly(outResponse, inResponse);
        }
    }

    // ---------------------------------------------------------------
    // Insufficient stock
    // ---------------------------------------------------------------

    @Nested
    class InsufficientStockScenarios {

        @Test
        void shouldThrowInsufficientStockExceptionWithTheAggregateTotalWhenSumAcrossLocationsFallsShort() {
            StorageLocation binA1 = location(10L, fromWarehouse, "A1");
            givenValidActiveReferences();
            givenOriginStocks(
                    stock(500L, fromWarehouse, null, 5),
                    stock(501L, fromWarehouse, binA1, 10));

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 20))
                    .satisfies(ex -> {
                        assertThat(ex.getItemId()).isEqualTo(ITEM_ID);
                        assertThat(ex.getWarehouseId()).isEqualTo(FROM_WAREHOUSE_ID);
                        assertThat(ex.getRequestedQuantity()).isEqualTo(20);
                        assertThat(ex.getAvailableQuantity()).isEqualTo(15); // 5 + 10 summed
                    });

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInsufficientStockExceptionWhenNoStockRowsExistAtAll() {
            givenValidActiveReferences();
            givenOriginStocks();

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10))
                    .satisfies(ex -> assertThat(ex.getAvailableQuantity()).isEqualTo(0));

            assertNothingWasSaved();
        }
    }

    // ---------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------

    @Nested
    class ValidationFailures {

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenSourceAndDestinationWarehouseAreIdentical() {
            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, FROM_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -5})
        void shouldThrowInvalidStockOperationExceptionWhenQuantityIsZeroOrNegative(int quantity) {
            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, quantity));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            givenValidActiveReferences();
            when(itemRepository.findById(ITEM_ID)).thenReturn(java.util.Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenOriginWarehouseDoesNotExist() {
            givenValidActiveReferences();
            when(warehouseRepository.findById(FROM_WAREHOUSE_ID)).thenReturn(java.util.Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenDestinationWarehouseDoesNotExist() {
            givenValidActiveReferences();
            when(warehouseRepository.findById(TO_WAREHOUSE_ID)).thenReturn(java.util.Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenItemIsInactive() {
            activeItem.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenOriginWarehouseIsInactive() {
            fromWarehouse.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenDestinationWarehouseIsInactive() {
            toWarehouse.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferWarehouseStock(
                            ITEM_ID, FROM_WAREHOUSE_ID, TO_WAREHOUSE_ID, 10));

            assertNothingWasSaved();
        }
    }
}
