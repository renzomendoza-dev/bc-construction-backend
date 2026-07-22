package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.entity.InventoryStock;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InventoryStockMapperTest {

    private InventoryStockMapper mapper;

    private Item item;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        mapper = new InventoryStockMapperImpl();

        item = new Item();
        item.setId(42L);
        item.setSku("SKU-001");
        item.setName("Portland Cement 40kg");

        warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setCode("WH-MAIN");
        warehouse.setName("Main Yard Warehouse");
    }

    private InventoryStock buildStock(StorageLocation location) {
        InventoryStock stock = new InventoryStock();
        stock.setId(500L);
        stock.setItem(item);
        stock.setWarehouse(warehouse);
        stock.setLocation(location);
        stock.setQuantity(120);
        stock.setReorderThreshold(30);
        stock.setUpdatedAt(Instant.parse("2026-06-30T09:15:00Z"));
        return stock;
    }

    @Test
    void shouldMapStockToStockLevelResponseWithAllFlattenedFields() {
        StorageLocation location = new StorageLocation();
        location.setId(21L);
        location.setWarehouse(warehouse);
        location.setCode("A-01-02");

        InventoryStock stock = buildStock(location);

        StockLevelResponse response = mapper.toStockLevelResponse(stock);

        assertThat(response).isNotNull();
        assertThat(response.getItemId()).isEqualTo(42L);
        assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
        assertThat(response.getSku()).isEqualTo("SKU-001");
        assertThat(response.getWarehouseId()).isEqualTo(3L);
        assertThat(response.getWarehouseName()).isEqualTo("Main Yard Warehouse");
        assertThat(response.getLocationId()).isEqualTo(21L);
        assertThat(response.getLocationCode()).isEqualTo("A-01-02");
        assertThat(response.getQuantity()).isEqualTo(120);
        assertThat(response.getReorderThreshold()).isEqualTo(30);
    }

    @Test
    void shouldMapNullLocationToNullLocationFieldsWithoutThrowing() {
        InventoryStock stock = buildStock(null);

        assertThatCode(() -> mapper.toStockLevelResponse(stock))
                .doesNotThrowAnyException();

        StockLevelResponse response = mapper.toStockLevelResponse(stock);

        assertThat(response.getLocationId()).isNull();
        assertThat(response.getLocationCode()).isNull();
    }

    @Test
    void shouldStillMapOtherFieldsWhenLocationIsNull() {
        InventoryStock stock = buildStock(null);

        StockLevelResponse response = mapper.toStockLevelResponse(stock);

        assertThat(response.getItemId()).isEqualTo(42L);
        assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
        assertThat(response.getSku()).isEqualTo("SKU-001");
        assertThat(response.getWarehouseId()).isEqualTo(3L);
        assertThat(response.getWarehouseName()).isEqualTo("Main Yard Warehouse");
        assertThat(response.getQuantity()).isEqualTo(120);
        assertThat(response.getReorderThreshold()).isEqualTo(30);
    }
}