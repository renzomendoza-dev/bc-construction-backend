package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.StockMovementResponse;
import com.bcconstructionservices.inventory.dto.StockTransferRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTransferStockTest {

    private static final Long ITEM_ID = 42L;
    private static final Long FROM_WAREHOUSE_ID = 3L;
    private static final Long TO_WAREHOUSE_ID = 4L;
    private static final Long FROM_LOCATION_ID = 21L;
    private static final Long TO_LOCATION_ID = 35L;

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
    private StorageLocation fromLocation;
    private StorageLocation toLocation;

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

        fromLocation = new StorageLocation();
        fromLocation.setId(FROM_LOCATION_ID);
        fromLocation.setWarehouse(fromWarehouse);
        fromLocation.setCode("A-01-02");

        toLocation = new StorageLocation();
        toLocation.setId(TO_LOCATION_ID);
        toLocation.setWarehouse(toWarehouse);
        toLocation.setCode("N-03-01");
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private StockTransferRequest request(Long fromWarehouseId, Long fromLocationId,
                                         Long toWarehouseId, Long toLocationId,
                                         Integer quantity) {
        StockTransferRequest req = new StockTransferRequest();
        req.setItemId(ITEM_ID);
        req.setFromWarehouseId(fromWarehouseId);
        req.setFromLocationId(fromLocationId);
        req.setToWarehouseId(toWarehouseId);
        req.setToLocationId(toLocationId);
        req.setQuantity(quantity);
        return req;
    }

    private StockTransferRequest defaultCrossWarehouseRequest(Integer quantity) {
        return request(FROM_WAREHOUSE_ID, FROM_LOCATION_ID, TO_WAREHOUSE_ID, TO_LOCATION_ID, quantity);
    }

    private InventoryStock stock(Long id, Warehouse warehouse, StorageLocation location, int quantity) {
        InventoryStock s = new InventoryStock();
        s.setId(id);
        s.setItem(activeItem);
        s.setWarehouse(warehouse);
        s.setLocation(location);
        s.setQuantity(quantity);
        s.setReorderThreshold(30);
        return s;
    }

    /**
     * Lenient so tests remain valid regardless of the exact order the
     * service performs its lookups/validations in.
     */
    private void givenValidActiveReferences() {
        lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
        lenient().when(warehouseRepository.findById(FROM_WAREHOUSE_ID)).thenReturn(Optional.of(fromWarehouse));
        lenient().when(warehouseRepository.findById(TO_WAREHOUSE_ID)).thenReturn(Optional.of(toWarehouse));
        lenient().when(storageLocationRepository.findById(FROM_LOCATION_ID)).thenReturn(Optional.of(fromLocation));
        lenient().when(storageLocationRepository.findById(TO_LOCATION_ID)).thenReturn(Optional.of(toLocation));
    }

    private void givenSourceStock(Long warehouseId, Long locationId, InventoryStock sourceStock) {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, warehouseId, locationId))
                .thenReturn(Optional.of(sourceStock));
    }

    private void givenDestinationStock(Long warehouseId, Long locationId, InventoryStock destinationStock) {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, warehouseId, locationId))
                .thenReturn(Optional.of(destinationStock));
    }

    private void givenNoDestinationStock(Long warehouseId, Long locationId) {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, warehouseId, locationId))
                .thenReturn(Optional.empty());
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

    private List<InventoryStock> captureSavedStocks(int expectedSaveCount) {
        ArgumentCaptor<InventoryStock> captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(inventoryStockRepository, times(expectedSaveCount)).save(captor.capture());
        return captor.getAllValues();
    }

    // ---------------------------------------------------------------
    // Successful transfers
    // ---------------------------------------------------------------

    @Nested
    class SuccessfulTransfers {

        @Test
        void shouldDecrementSourceAndIncrementDestinationAcrossWarehouses() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 100));
            givenDestinationStock(TO_WAREHOUSE_ID, TO_LOCATION_ID,
                    stock(501L, toWarehouse, toLocation, 15));
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(defaultCrossWarehouseRequest(40));

            List<InventoryStock> savedStocks = captureSavedStocks(2);
            InventoryStock savedSource = savedStocks.stream()
                    .filter(s -> s.getWarehouse().getId().equals(FROM_WAREHOUSE_ID))
                    .findFirst().orElseThrow();
            InventoryStock savedDestination = savedStocks.stream()
                    .filter(s -> s.getWarehouse().getId().equals(TO_WAREHOUSE_ID))
                    .findFirst().orElseThrow();

            assertThat(savedSource.getQuantity()).isEqualTo(60);
            assertThat(savedDestination.getQuantity()).isEqualTo(55);
        }

        @Test
        void shouldRecordTwoTransferMovementsWithOneLocationEachAcrossWarehouses() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 100));
            givenDestinationStock(TO_WAREHOUSE_ID, TO_LOCATION_ID,
                    stock(501L, toWarehouse, toLocation, 15));
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(defaultCrossWarehouseRequest(40));

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
            List<StockMovement> movements = movementCaptor.getAllValues();

            StockMovement out = movements.get(0);
            assertThat(out.getType()).isEqualTo(MovementType.TRANSFER);
            assertThat(out.getDirection()).isEqualTo(MovementDirection.OUT);
            assertThat(out.getItem()).isEqualTo(activeItem);
            assertThat(out.getWarehouse()).isEqualTo(fromWarehouse);
            assertThat(out.getQuantity()).isEqualTo(40);
            assertThat(out.getFromLocation()).isEqualTo(fromLocation);
            assertThat(out.getToLocation()).isNull();
            assertThat(out.getReason()).isEqualTo("Transfer to " + toWarehouse.getName());

            StockMovement in = movements.get(1);
            assertThat(in.getType()).isEqualTo(MovementType.TRANSFER);
            assertThat(in.getDirection()).isEqualTo(MovementDirection.IN);
            assertThat(in.getItem()).isEqualTo(activeItem);
            assertThat(in.getWarehouse()).isEqualTo(toWarehouse);
            assertThat(in.getQuantity()).isEqualTo(40);
            assertThat(in.getFromLocation()).isNull();
            assertThat(in.getToLocation()).isEqualTo(toLocation);
            assertThat(in.getReason()).isEqualTo("Transfer from " + fromWarehouse.getName());
        }

        @Test
        void shouldRecordSingleTransferMovementWithBothLocationsPopulatedWithinSameWarehouse() {
            StorageLocation otherLocationSameWarehouse = new StorageLocation();
            otherLocationSameWarehouse.setId(22L);
            otherLocationSameWarehouse.setWarehouse(fromWarehouse);
            otherLocationSameWarehouse.setCode("A-02-04");

            givenValidActiveReferences();
            lenient().when(storageLocationRepository.findById(22L))
                    .thenReturn(Optional.of(otherLocationSameWarehouse));
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 80));
            givenDestinationStock(FROM_WAREHOUSE_ID, 22L,
                    stock(502L, fromWarehouse, otherLocationSameWarehouse, 5));
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(
                    request(FROM_WAREHOUSE_ID, FROM_LOCATION_ID, FROM_WAREHOUSE_ID, 22L, 30));

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository, times(1)).save(movementCaptor.capture());
            StockMovement movement = movementCaptor.getValue();

            assertThat(movement.getType()).isEqualTo(MovementType.TRANSFER);
            assertThat(movement.getDirection()).isEqualTo(MovementDirection.WITHIN);
            assertThat(movement.getItem()).isEqualTo(activeItem);
            assertThat(movement.getQuantity()).isEqualTo(30);
            assertThat(movement.getFromLocation()).isEqualTo(fromLocation);
            assertThat(movement.getToLocation()).isEqualTo(otherLocationSameWarehouse);
            assertThat(movement.getReason()).isEqualTo("Transfer within " + fromWarehouse.getName());
        }

        @Test
        void shouldReturnResponsesMappedFromTheSavedMovements() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 100));
            givenDestinationStock(TO_WAREHOUSE_ID, TO_LOCATION_ID,
                    stock(501L, toWarehouse, toLocation, 15));
            givenSavesEchoTheirArgument();

            StockMovementResponse outResponse = new StockMovementResponse();
            outResponse.setItemId(ITEM_ID);
            outResponse.setType(MovementType.TRANSFER);
            outResponse.setQuantity(40);

            StockMovementResponse inResponse = new StockMovementResponse();
            inResponse.setItemId(ITEM_ID);
            inResponse.setType(MovementType.TRANSFER);
            inResponse.setQuantity(40);

            when(stockMovementMapper.toResponse(any(StockMovement.class)))
                    .thenReturn(outResponse, inResponse);

            List<StockMovementResponse> result =
                    inventoryService.transferStock(defaultCrossWarehouseRequest(40));

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(outResponse, inResponse);
        }

        @Test
        void shouldAllowTransferBetweenDifferentLocationsInTheSameWarehouse() {
            StorageLocation otherLocationSameWarehouse = new StorageLocation();
            otherLocationSameWarehouse.setId(22L);
            otherLocationSameWarehouse.setWarehouse(fromWarehouse);
            otherLocationSameWarehouse.setCode("A-02-04");

            givenValidActiveReferences();
            lenient().when(storageLocationRepository.findById(22L))
                    .thenReturn(Optional.of(otherLocationSameWarehouse));
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 80));
            givenDestinationStock(FROM_WAREHOUSE_ID, 22L,
                    stock(502L, fromWarehouse, otherLocationSameWarehouse, 5));
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(
                    request(FROM_WAREHOUSE_ID, FROM_LOCATION_ID, FROM_WAREHOUSE_ID, 22L, 30));

            List<InventoryStock> savedStocks = captureSavedStocks(2);
            InventoryStock savedSource = savedStocks.stream()
                    .filter(s -> s.getLocation() != null && s.getLocation().getId().equals(FROM_LOCATION_ID))
                    .findFirst().orElseThrow();
            InventoryStock savedDestination = savedStocks.stream()
                    .filter(s -> s.getLocation() != null && s.getLocation().getId().equals(22L))
                    .findFirst().orElseThrow();

            assertThat(savedSource.getQuantity()).isEqualTo(50);
            assertThat(savedDestination.getQuantity()).isEqualTo(35);
        }

        @Test
        void shouldCreateDestinationStockRowStartingAtZeroWhenNoneExists() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 100));
            givenNoDestinationStock(TO_WAREHOUSE_ID, TO_LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(defaultCrossWarehouseRequest(40));

            List<InventoryStock> savedStocks = captureSavedStocks(2);
            InventoryStock createdDestination = savedStocks.stream()
                    .filter(s -> s.getWarehouse().getId().equals(TO_WAREHOUSE_ID))
                    .findFirst().orElseThrow();

            // Brand-new row: no id, correct associations, 0 + 40 = 40.
            assertThat(createdDestination.getId()).isNull();
            assertThat(createdDestination.getItem()).isEqualTo(activeItem);
            assertThat(createdDestination.getQuantity()).isEqualTo(40);
        }

        @Test
        void shouldAllowWarehouseLevelTransferWhenBothLocationIdsAreNull() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, null,
                    stock(500L, fromWarehouse, null, 100));
            givenDestinationStock(TO_WAREHOUSE_ID, null,
                    stock(501L, toWarehouse, null, 10));
            givenSavesEchoTheirArgument();

            inventoryService.transferStock(
                    request(FROM_WAREHOUSE_ID, null, TO_WAREHOUSE_ID, null, 25));

            List<InventoryStock> savedStocks = captureSavedStocks(2);
            InventoryStock savedSource = savedStocks.stream()
                    .filter(s -> s.getWarehouse().getId().equals(FROM_WAREHOUSE_ID))
                    .findFirst().orElseThrow();
            InventoryStock savedDestination = savedStocks.stream()
                    .filter(s -> s.getWarehouse().getId().equals(TO_WAREHOUSE_ID))
                    .findFirst().orElseThrow();

            assertThat(savedSource.getQuantity()).isEqualTo(75);
            assertThat(savedDestination.getQuantity()).isEqualTo(35);

            // No locationIds in the request, so no location lookups should occur.
            verify(storageLocationRepository, never()).findById(any());
        }
    }

    // ---------------------------------------------------------------
    // Insufficient stock
    // ---------------------------------------------------------------

    @Nested
    class InsufficientStockScenarios {

        @Test
        void shouldThrowInsufficientStockExceptionWhenSourceStockIsLessThanRequested() {
            givenValidActiveReferences();
            givenSourceStock(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                    stock(500L, fromWarehouse, fromLocation, 10));

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(25)))
                    .satisfies(ex -> {
                        assertThat(ex.getItemId()).isEqualTo(ITEM_ID);
                        assertThat(ex.getWarehouseId()).isEqualTo(FROM_WAREHOUSE_ID);
                        assertThat(ex.getRequestedQuantity()).isEqualTo(25);
                        assertThat(ex.getAvailableQuantity()).isEqualTo(10);
                    });

            assertNothingWasSaved();
        }
    }

    // ---------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------

    @Nested
    class ValidationFailures {

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenSourceAndDestinationAreIdentical() {
            givenValidActiveReferences();

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.transferStock(
                            request(FROM_WAREHOUSE_ID, FROM_LOCATION_ID,
                                    FROM_WAREHOUSE_ID, FROM_LOCATION_ID, 10)));

            assertNothingWasSaved();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -5})
        void shouldThrowInvalidStockOperationExceptionWhenQuantityIsZeroOrNegative(int quantity) {
            givenValidActiveReferences();

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.transferStock(
                            defaultCrossWarehouseRequest(quantity)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            givenValidActiveReferences();
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSourceWarehouseDoesNotExist() {
            givenValidActiveReferences();
            when(warehouseRepository.findById(FROM_WAREHOUSE_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenDestinationWarehouseDoesNotExist() {
            givenValidActiveReferences();
            when(warehouseRepository.findById(TO_WAREHOUSE_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenSourceLocationDoesNotExist() {
            givenValidActiveReferences();
            when(storageLocationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.transferStock(
                            request(FROM_WAREHOUSE_ID, 999L, TO_WAREHOUSE_ID, TO_LOCATION_ID, 10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenItemIsInactive() {
            activeItem.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenSourceWarehouseIsInactive() {
            fromWarehouse.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenDestinationWarehouseIsInactive() {
            toWarehouse.setActive(false);
            givenValidActiveReferences();

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.transferStock(defaultCrossWarehouseRequest(10)));

            assertNothingWasSaved();
        }
    }
}