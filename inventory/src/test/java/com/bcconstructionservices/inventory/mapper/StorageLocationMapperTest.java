package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.StorageLocationRequest;
import com.bcconstructionservices.inventory.dto.StorageLocationResponse;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageLocationMapperTest {

    private StorageLocationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new StorageLocationMapperImpl();
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapStorageLocationToResponseWithAllFields() {
            Warehouse warehouse = new Warehouse();
            warehouse.setId(3L);
            warehouse.setCode("WH-MAIN");
            warehouse.setName("Main Yard Warehouse");

            StorageLocation location = new StorageLocation();
            location.setId(21L);
            location.setWarehouse(warehouse);
            location.setCode("A-01-02");

            StorageLocationResponse response = mapper.toResponse(location);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(21L);
            assertThat(response.getCode()).isEqualTo("A-01-02");
        }

        @Test
        void shouldFlattenWarehouseIdFromNestedWarehouse() {
            Warehouse warehouse = new Warehouse();
            warehouse.setId(3L);

            StorageLocation location = new StorageLocation();
            location.setId(21L);
            location.setWarehouse(warehouse);
            location.setCode("A-01-02");

            StorageLocationResponse response = mapper.toResponse(location);

            assertThat(response.getWarehouseId()).isEqualTo(3L);
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapRequestCodeToEntity() {
            StorageLocationRequest request = new StorageLocationRequest();
            request.setWarehouseId(3L);
            request.setCode("B-02-05");

            StorageLocation entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getCode()).isEqualTo("B-02-05");
        }

        @Test
        void shouldNotResolveWarehouseAssociationFromRequestId() {
            // Resolving warehouseId to a managed Warehouse entity is the
            // service layer's job; the mapper must not fabricate it.
            StorageLocationRequest request = new StorageLocationRequest();
            request.setWarehouseId(3L);
            request.setCode("B-02-05");

            StorageLocation entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getWarehouse()).isNull();
        }
    }
}