package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.WarehouseCreateRequest;
import com.bcconstructionservices.inventory.dto.WarehouseResponse;
import com.bcconstructionservices.inventory.dto.WarehouseUpdateRequest;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseMapperTest {

    private WarehouseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WarehouseMapperImpl();
    }

    private Warehouse buildWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(3L);
        warehouse.setCode("WH-MAIN");
        warehouse.setName("Main Yard Warehouse");
        warehouse.setActive(true);
        warehouse.setType(WarehouseType.MAIN);
        warehouse.setCreatedAt(Instant.parse("2025-10-01T01:00:00Z"));
        warehouse.setUpdatedAt(Instant.parse("2026-05-14T07:20:00Z"));
        return warehouse;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapWarehouseToResponseWithAllFields() {
            Warehouse warehouse = buildWarehouse();

            WarehouseResponse response = mapper.toResponse(warehouse);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(3L);
            assertThat(response.getCode()).isEqualTo("WH-MAIN");
            assertThat(response.getName()).isEqualTo("Main Yard Warehouse");
            assertThat(response.isActive()).isTrue();
            assertThat(response.getType()).isEqualTo(WarehouseType.MAIN);
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2025-10-01T01:00:00Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-05-14T07:20:00Z"));
        }

        @Test
        void shouldMapSiteTypeWarehouseToResponse() {
            Warehouse warehouse = buildWarehouse();
            warehouse.setType(WarehouseType.SITE);

            WarehouseResponse response = mapper.toResponse(warehouse);

            assertThat(response.getType()).isEqualTo(WarehouseType.SITE);
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            WarehouseCreateRequest request = new WarehouseCreateRequest();
            request.setCode("WH-NORTH");
            request.setName("North Satellite Warehouse");

            Warehouse entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getCode()).isEqualTo("WH-NORTH");
            assertThat(entity.getName()).isEqualTo("North Satellite Warehouse");
        }

        @Test
        void shouldNotSetServerManagedFieldsFromCreateRequest() {
            WarehouseCreateRequest request = new WarehouseCreateRequest();
            request.setCode("WH-NORTH");
            request.setName("North Satellite Warehouse");

            Warehouse entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }

        @Test
        void shouldDefaultTypeToMainWhenOmittedFromCreateRequest() {
            WarehouseCreateRequest request = new WarehouseCreateRequest();
            request.setCode("WH-NORTH");
            request.setName("North Satellite Warehouse");
            // type intentionally left unset.

            Warehouse entity = mapper.toEntity(request);

            assertThat(entity.getType()).isEqualTo(WarehouseType.MAIN);
        }

        @Test
        void shouldMapExplicitSiteTypeFromCreateRequest() {
            WarehouseCreateRequest request = new WarehouseCreateRequest();
            request.setCode("WH-SITE1");
            request.setName("Site Warehouse - Sta. Maria Project");
            request.setType(WarehouseType.SITE);

            Warehouse entity = mapper.toEntity(request);

            assertThat(entity.getType()).isEqualTo(WarehouseType.SITE);
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteEntityFieldsWithRequestValues() {
            Warehouse existing = buildWarehouse();

            WarehouseUpdateRequest request = new WarehouseUpdateRequest();
            request.setName("Main Yard Warehouse (Bldg A)");
            request.setActive(false);

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getName()).isEqualTo("Main Yard Warehouse (Bldg A)");
            assertThat(existing.isActive()).isFalse();
        }

        @Test
        void shouldNotChangeFieldsAbsentFromUpdateRequest() {
            Warehouse existing = buildWarehouse();
            Long originalId = existing.getId();
            String originalCode = existing.getCode();
            Instant originalCreatedAt = existing.getCreatedAt();

            WarehouseUpdateRequest request = new WarehouseUpdateRequest();
            request.setName("Main Yard Warehouse (Bldg A)");
            request.setActive(true);

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getId()).isEqualTo(originalId);
            assertThat(existing.getCode()).isEqualTo(originalCode);
            assertThat(existing.getCreatedAt()).isEqualTo(originalCreatedAt);
        }

        @Test
        void shouldNeverChangeTypeSinceUpdateRequestHasNoTypeField() {
            // A site shouldn't silently become a warehouse (or vice versa) -
            // WarehouseUpdateRequest has no type field at all, and the mapper
            // explicitly ignores it, so it must stay exactly as it was.
            Warehouse existing = buildWarehouse();
            existing.setType(WarehouseType.SITE);

            WarehouseUpdateRequest request = new WarehouseUpdateRequest();
            request.setName("Renamed Site");
            request.setActive(false);

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getType()).isEqualTo(WarehouseType.SITE);
        }
    }
}