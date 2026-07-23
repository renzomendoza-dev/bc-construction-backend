package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.ReorderThresholdRequest;
import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.entity.InventoryStock;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.InventoryStockMapper;
import com.bcconstructionservices.inventory.repository.InventoryStockRepository;
import com.bcconstructionservices.inventory.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for InventoryService.updateReorderThreshold.
 *
 * <p>Only the three dependencies this method actually uses are mocked
 * (InventoryStockRepository, StockMovementRepository, InventoryStockMapper);
 * @InjectMocks injects those and leaves any other InventoryService
 * dependencies null, which is fine since this method never touches them.
 *
 * <p>The finder name (findByItemAndWarehouseAndLocation) and mapper method
 * (toStockLevelResponse) match the real repository/mapper sources in this
 * project - a null locationId is passed straight through to the finder, whose
 * JPQL matches it against a null stock location.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceReorderThresholdTest {

    private static final Long ITEM_ID = 42L;
    private static final Long WAREHOUSE_ID = 3L;
    private static final Long LOCATION_ID = 21L;

    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private InventoryStockMapper inventoryStockMapper;

    @InjectMocks
    private InventoryService inventoryService;

    private Item item;
    private Warehouse warehouse;
    private StorageLocation location;

    @BeforeEach
    void setUp() {
        item = new Item();
        item.setId(ITEM_ID);
        item.setSku("SKU-001");
        item.setName("Portland Cement 40kg");

        warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE_ID);
        warehouse.setName("Main Yard Warehouse");

        location = new StorageLocation();
        location.setId(LOCATION_ID);
        location.setWarehouse(warehouse);
        location.setCode("A-01-02");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ReorderThresholdRequest request(Long locationId, Integer reorderThreshold) {
        ReorderThresholdRequest req = new ReorderThresholdRequest();
        req.setItemId(ITEM_ID);
        req.setWarehouseId(WAREHOUSE_ID);
        req.setLocationId(locationId);
        req.setReorderThreshold(reorderThreshold);
        return req;
    }

    private InventoryStock existingStock(StorageLocation stockLocation, int quantity, Integer currentThreshold) {
        InventoryStock stock = new InventoryStock();
        stock.setId(500L);
        stock.setItem(item);
        stock.setWarehouse(warehouse);
        stock.setLocation(stockLocation);
        stock.setQuantity(quantity);
        stock.setReorderThreshold(currentThreshold);
        return stock;
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    void shouldUpdateAndSaveReorderThresholdLeavingQuantityUnchanged() {
        InventoryStock stock = existingStock(location, 120, 30);
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, LOCATION_ID))
                .thenReturn(Optional.of(stock));
        when(inventoryStockRepository.save(any(InventoryStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockLevelResponse mapped = new StockLevelResponse();
        when(inventoryStockMapper.toStockLevelResponse(any(InventoryStock.class))).thenReturn(mapped);

        StockLevelResponse result = inventoryService.updateReorderThreshold(request(LOCATION_ID, 75));

        // Returns exactly what the mapper produced.
        assertThat(result).isSameAs(mapped);

        // The saved entity has the new threshold and the SAME quantity as before.
        ArgumentCaptor<InventoryStock> captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(inventoryStockRepository).save(captor.capture());
        assertThat(captor.getValue().getReorderThreshold()).isEqualTo(75);
        assertThat(captor.getValue().getQuantity()).isEqualTo(120);

        // Threshold-only change: never writes a movement audit row.
        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenNoMatchingStockRowExists() {
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, LOCATION_ID))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> inventoryService.updateReorderThreshold(request(LOCATION_ID, 75)));

        // No row found -> nothing saved, no movement row.
        verify(inventoryStockRepository, never()).save(any());
        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void shouldUpdateWarehouseLevelStockWhenLocationIdIsNull() {
        // Warehouse-level stock: null location on both the request and the row.
        InventoryStock warehouseLevelStock = existingStock(null, 80, 20);
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, null))
                .thenReturn(Optional.of(warehouseLevelStock));
        when(inventoryStockRepository.save(any(InventoryStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockLevelResponse mapped = new StockLevelResponse();
        when(inventoryStockMapper.toStockLevelResponse(any(InventoryStock.class))).thenReturn(mapped);

        StockLevelResponse result = inventoryService.updateReorderThreshold(request(null, 50));

        assertThat(result).isSameAs(mapped);

        // Confirm the finder was called with a null locationId (not some default).
        verify(inventoryStockRepository).findByItemAndWarehouseAndLocation(
                eq(ITEM_ID), eq(WAREHOUSE_ID), isNull());

        ArgumentCaptor<InventoryStock> captor = ArgumentCaptor.forClass(InventoryStock.class);
        verify(inventoryStockRepository).save(captor.capture());
        assertThat(captor.getValue().getReorderThreshold()).isEqualTo(50);
        assertThat(captor.getValue().getQuantity()).isEqualTo(80);

        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void shouldNeverCreateAStockMovementRowForAThresholdUpdate() {
        InventoryStock stock = existingStock(location, 120, 30);
        when(inventoryStockRepository.findByItemAndWarehouseAndLocation(ITEM_ID, WAREHOUSE_ID, LOCATION_ID))
                .thenReturn(Optional.of(stock));
        when(inventoryStockRepository.save(any(InventoryStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryStockMapper.toStockLevelResponse(any(InventoryStock.class)))
                .thenReturn(new StockLevelResponse());

        inventoryService.updateReorderThreshold(request(LOCATION_ID, 75));

        // Dedicated, explicit assertion of the core invariant: a threshold
        // change is not a stock movement and must leave no audit trail.
        verify(stockMovementRepository, never()).save(any());
        verifyNoInteractions(stockMovementRepository);
    }
}