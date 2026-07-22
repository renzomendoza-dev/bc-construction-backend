package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.StockAdjustmentRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceAdjustStockTest {

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

    private Item activeItem;
    private Warehouse activeWarehouse;
    private StorageLocation location;

    @BeforeEach
    void setUp() {
        activeItem = new Item();
        activeItem.setId(ITEM_ID);
        activeItem.setSku("SKU-001");
        activeItem.setName("Portland Cement 40kg");
        activeItem.setActive(true);

        activeWarehouse = new Warehouse();
        activeWarehouse.setId(WAREHOUSE_ID);
        activeWarehouse.setCode("WH-MAIN");
        activeWarehouse.setName("Main Yard Warehouse");
        activeWarehouse.setActive(true);

        location = new StorageLocation();
        location.setId(LOCATION_ID);
        location.setWarehouse(activeWarehouse);
        location.setCode("A-01-02");
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private StockAdjustmentRequest request(MovementType type, Integer quantity, Long locationId) {
        StockAdjustmentRequest req = new StockAdjustmentRequest();
        req.setItemId(ITEM_ID);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setLocationId(locationId);
        req.setQuantity(quantity);
        req.setType(type);
        req.setReason("Cycle count correction");
        return req;
    }

    private InventoryStock existingStock(int quantity, StorageLocation stockLocation) {
        InventoryStock stock = new InventoryStock();
        stock.setId(500L);
        stock.setItem(activeItem);
        stock.setWarehouse(activeWarehouse);
        stock.setLocation(stockLocation);
        stock.setQuantity(quantity);
        stock.setReorderThreshold(30);
        return stock;
    }

    /**
     * Stubs the item/warehouse/location lookups for the happy path.
     * Lenient so individual tests stay valid regardless of the exact
     * order the service performs its validations in.
     */
    private void givenValidActiveReferences() {
        lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
        lenient().when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(activeWarehouse));
        lenient().when(storageLocationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
    }

    private void givenExistingStock(InventoryStock stock, Long locationId) {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, locationId))
                .thenReturn(Optional.of(stock));
    }

    private void givenNoExistingStock(Long locationId) {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, locationId))
                .thenReturn(Optional.empty());
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(inventoryStockRepository.save(any(InventoryStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertNothingWasSaved() {
        verify(inventoryStockRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // type = IN
    // ---------------------------------------------------------------

    @Nested
    class WhenTypeIsIn {

        @Test
        void shouldIncreaseExistingStockQuantityAndRecordInMovement() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(100, location), LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.IN, 50, LOCATION_ID));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(150);

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(movementCaptor.capture());
            StockMovement savedMovement = movementCaptor.getValue();
            assertThat(savedMovement.getType()).isEqualTo(MovementType.IN);
            assertThat(savedMovement.getQuantity()).isEqualTo(50);
            assertThat(savedMovement.getItem()).isEqualTo(activeItem);
            assertThat(savedMovement.getWarehouse()).isEqualTo(activeWarehouse);
            assertThat(savedMovement.getReason()).isEqualTo("Cycle count correction");
        }

        @Test
        void shouldCreateNewStockRowStartingAtZeroWhenNoRowExists() {
            givenValidActiveReferences();
            givenNoExistingStock(LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.IN, 50, LOCATION_ID));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            InventoryStock createdStock = stockCaptor.getValue();

            // New row: no id yet, associations wired up, 0 + 50 = 50.
            assertThat(createdStock.getId()).isNull();
            assertThat(createdStock.getItem()).isEqualTo(activeItem);
            assertThat(createdStock.getWarehouse()).isEqualTo(activeWarehouse);
            assertThat(createdStock.getQuantity()).isEqualTo(50);

            verify(stockMovementRepository).save(any(StockMovement.class));
        }

        @Test
        void shouldTreatNullLocationAsWarehouseLevelStock() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(100, null), null);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.IN, 25, null));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(125);

            // No locationId in the request, so no location lookup should occur.
            verify(storageLocationRepository, never()).findById(anyLong());
        }
    }

    // ---------------------------------------------------------------
    // type = OUT
    // ---------------------------------------------------------------

    @Nested
    class WhenTypeIsOut {

        @Test
        void shouldDecreaseStockQuantityAndRecordOutMovementWhenStockIsSufficient() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(100, location), LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.OUT, 40, LOCATION_ID));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(60);

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(movementCaptor.capture());
            assertThat(movementCaptor.getValue().getType()).isEqualTo(MovementType.OUT);
            assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(40);
        }

        @Test
        void shouldSucceedWithResultingQuantityZeroWhenOutEqualsAvailable() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(40, location), LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.OUT, 40, LOCATION_ID));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getQuantity()).isZero();

            verify(stockMovementRepository).save(any(StockMovement.class));
        }

        @Test
        void shouldThrowInsufficientStockExceptionWhenOutExceedsAvailableQuantity() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(10, location), LOCATION_ID);

            assertThatExceptionOfType(InsufficientStockException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.OUT, 25, LOCATION_ID)))
                    .satisfies(ex -> {
                        assertThat(ex.getItemId()).isEqualTo(ITEM_ID);
                        assertThat(ex.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
                        assertThat(ex.getRequestedQuantity()).isEqualTo(25);
                        assertThat(ex.getAvailableQuantity()).isEqualTo(10);
                    });

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundWhenNoStockRowExistsForOutMovement() {
            givenValidActiveReferences();
            givenNoExistingStock(LOCATION_ID);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.OUT, 5, LOCATION_ID)))
                    .withMessageContaining("No inventory stock record found");

            assertNothingWasSaved();
        }

    }

    // ---------------------------------------------------------------
    // type = ADJUSTMENT
    // ---------------------------------------------------------------
    // NOTE: These tests assume ADJUSTMENT applies the request quantity as a
    // signed delta (positive = increase, negative = decrease), since the DTO
    // has no separate direction field. If your service instead interprets
    // ADJUSTMENT as "set to absolute quantity", adjust these two tests to
    // match that contract.

    @Nested
    class WhenTypeIsAdjustment {

        @Test
        void shouldIncreaseStockQuantityLikeInWhenAdjustmentIsPositive() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(100, location), LOCATION_ID);
            givenSavesEchoTheirArgument();

            inventoryService.adjustStock(request(MovementType.ADJUSTMENT, 30, LOCATION_ID));

            ArgumentCaptor<InventoryStock> stockCaptor = ArgumentCaptor.forClass(InventoryStock.class);
            verify(inventoryStockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(130);

            ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(movementCaptor.capture());
            assertThat(movementCaptor.getValue().getType()).isEqualTo(MovementType.ADJUSTMENT);
        }

//        @Test
//        void shouldThrowInsufficientStockExceptionWhenAdjustmentDecreaseExceedsAvailable() {
//            givenValidActiveReferences();
//            givenExistingStock(existingStock(20, location), LOCATION_ID);
//
//            assertThatExceptionOfType(InsufficientStockException.class)
//                    .isThrownBy(() -> inventoryService.adjustStock(
//                            request(MovementType.ADJUSTMENT, -50, LOCATION_ID)))
//                    .satisfies(ex -> {
//                        assertThat(ex.getAvailableQuantity()).isEqualTo(20);
//                        assertThat(ex.getRequestedQuantity()).isEqualTo(50);
//                    });
//
//            assertNothingWasSaved();
//        }
    @Test
    void shouldRejectNegativeQuantityRegardlessOfMovementType() {
        givenValidActiveReferences();

        assertThatExceptionOfType(InvalidStockOperationException.class)
                .isThrownBy(() -> inventoryService.adjustStock(
                        request(MovementType.ADJUSTMENT, -50, LOCATION_ID)))
                .withMessageContaining("must be greater than zero");

        assertNothingWasSaved();
    }
    }

    // ---------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------

    @Nested
    class ValidationFailures {

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenQuantityIsZero() {
            givenValidActiveReferences();

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.IN, 0, LOCATION_ID)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInvalidStockOperationExceptionWhenQuantityIsNegative() {
            givenValidActiveReferences();

            assertThatExceptionOfType(InvalidStockOperationException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.OUT, -10, LOCATION_ID)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenItemDoesNotExist() {
            when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());
            lenient().when(warehouseRepository.findById(WAREHOUSE_ID))
                    .thenReturn(Optional.of(activeWarehouse));
            lenient().when(storageLocationRepository.findById(LOCATION_ID))
                    .thenReturn(Optional.of(location));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.IN, 10, LOCATION_ID)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenWarehouseDoesNotExist() {
            lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
            when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());
            lenient().when(storageLocationRepository.findById(LOCATION_ID))
                    .thenReturn(Optional.of(location));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.IN, 10, LOCATION_ID)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenProvidedLocationDoesNotExist() {
            lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
            lenient().when(warehouseRepository.findById(WAREHOUSE_ID))
                    .thenReturn(Optional.of(activeWarehouse));
            when(storageLocationRepository.findById(999L)).thenReturn(Optional.empty());

            StockAdjustmentRequest req = request(MovementType.IN, 10, 999L);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(req));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenItemIsInactive() {
            activeItem.setActive(false);
            lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
            lenient().when(warehouseRepository.findById(WAREHOUSE_ID))
                    .thenReturn(Optional.of(activeWarehouse));
            lenient().when(storageLocationRepository.findById(LOCATION_ID))
                    .thenReturn(Optional.of(location));

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.IN, 10, LOCATION_ID)));

            assertNothingWasSaved();
        }

        @Test
        void shouldThrowInactiveResourceExceptionWhenWarehouseIsInactive() {
            activeWarehouse.setActive(false);
            lenient().when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(activeItem));
            lenient().when(warehouseRepository.findById(WAREHOUSE_ID))
                    .thenReturn(Optional.of(activeWarehouse));
            lenient().when(storageLocationRepository.findById(LOCATION_ID))
                    .thenReturn(Optional.of(location));

            assertThatExceptionOfType(InactiveResourceException.class)
                    .isThrownBy(() -> inventoryService.adjustStock(
                            request(MovementType.IN, 10, LOCATION_ID)));

            assertNothingWasSaved();
        }
    }

    // ---------------------------------------------------------------
    // Response mapping
    // ---------------------------------------------------------------

    @Nested
    class ResponseMapping {

        @Test
        void shouldReturnResponseMappedFromTheSavedStockMovement() {
            givenValidActiveReferences();
            givenExistingStock(existingStock(100, location), LOCATION_ID);
            givenSavesEchoTheirArgument();

            StockMovementResponse mappedResponse = new StockMovementResponse();
            mappedResponse.setItemId(ITEM_ID);
            mappedResponse.setItemName("Portland Cement 40kg");
            mappedResponse.setWarehouseId(WAREHOUSE_ID);
            mappedResponse.setType(MovementType.IN);
            mappedResponse.setQuantity(50);
            mappedResponse.setReason("Cycle count correction");
            when(stockMovementMapper.toResponse(any(StockMovement.class))).thenReturn(mappedResponse);

            StockMovementResponse result =
                    inventoryService.adjustStock(request(MovementType.IN, 50, LOCATION_ID));

            // The service must return exactly what the mapper produced...
            assertThat(result).isSameAs(mappedResponse);

            // ...and the mapper must have been fed the movement that was saved.
            ArgumentCaptor<StockMovement> savedCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementRepository).save(savedCaptor.capture());
            ArgumentCaptor<StockMovement> mappedCaptor = ArgumentCaptor.forClass(StockMovement.class);
            verify(stockMovementMapper).toResponse(mappedCaptor.capture());
            assertThat(mappedCaptor.getValue()).isSameAs(savedCaptor.getValue());
        }
    }
}