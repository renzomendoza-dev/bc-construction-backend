package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.SupplierCreateRequest;
import com.bcconstructionservices.inventory.dto.SupplierResponse;
import com.bcconstructionservices.inventory.dto.SupplierUpdateRequest;
import com.bcconstructionservices.inventory.entity.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierMapperTest {

    private SupplierMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SupplierMapperImpl();
    }

    private Supplier buildSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(7L);
        supplier.setName("Luzon Steel Trading");
        supplier.setContactInfo("sales@luzonsteel.ph / +63 917 555 0142");
        supplier.setActive(true);
        supplier.setCreatedAt(Instant.parse("2025-11-05T02:00:00Z"));
        supplier.setUpdatedAt(Instant.parse("2026-04-12T06:30:00Z"));
        return supplier;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapSupplierToResponseWithAllFields() {
            Supplier supplier = buildSupplier();

            SupplierResponse response = mapper.toResponse(supplier);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(7L);
            assertThat(response.getName()).isEqualTo("Luzon Steel Trading");
            assertThat(response.getContactInfo()).isEqualTo("sales@luzonsteel.ph / +63 917 555 0142");
            assertThat(response.isActive()).isTrue();
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2025-11-05T02:00:00Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-04-12T06:30:00Z"));
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            SupplierCreateRequest request = new SupplierCreateRequest();
            request.setName("Bulacan Hardware Supply");
            request.setContactInfo("orders@bulacanhardware.ph");

            Supplier entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getName()).isEqualTo("Bulacan Hardware Supply");
            assertThat(entity.getContactInfo()).isEqualTo("orders@bulacanhardware.ph");
        }

        @Test
        void shouldNotSetServerManagedFieldsFromCreateRequest() {
            SupplierCreateRequest request = new SupplierCreateRequest();
            request.setName("Bulacan Hardware Supply");
            request.setContactInfo("orders@bulacanhardware.ph");

            Supplier entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteEntityFieldsWithRequestValues() {
            Supplier existing = buildSupplier();

            SupplierUpdateRequest request = new SupplierUpdateRequest();
            request.setName("Luzon Steel Trading Corp.");
            request.setContactInfo("procurement@luzonsteel.ph");
            request.setActive(false);

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getName()).isEqualTo("Luzon Steel Trading Corp.");
            assertThat(existing.getContactInfo()).isEqualTo("procurement@luzonsteel.ph");
            assertThat(existing.isActive()).isFalse();
        }

        @Test
        void shouldNotChangeFieldsAbsentFromUpdateRequest() {
            Supplier existing = buildSupplier();
            Long originalId = existing.getId();
            Instant originalCreatedAt = existing.getCreatedAt();

            SupplierUpdateRequest request = new SupplierUpdateRequest();
            request.setName("Luzon Steel Trading Corp.");
            request.setContactInfo("procurement@luzonsteel.ph");
            request.setActive(true);

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getId()).isEqualTo(originalId);
            assertThat(existing.getCreatedAt()).isEqualTo(originalCreatedAt);
        }
    }
}