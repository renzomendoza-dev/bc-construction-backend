package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.ItemSupplierRequest;
import com.bcconstructionservices.inventory.dto.ItemSupplierResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemSupplier;
import com.bcconstructionservices.inventory.entity.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSupplierMapperTest {

    private ItemSupplierMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ItemSupplierMapperImpl();
    }

    private ItemSupplier buildItemSupplier() {
        Item item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");

        Supplier supplier = new Supplier();
        supplier.setId(7L);
        supplier.setName("Luzon Steel Trading");

        ItemSupplier itemSupplier = new ItemSupplier();
        itemSupplier.setId(101L);
        itemSupplier.setItem(item);
        itemSupplier.setSupplier(supplier);
        itemSupplier.setSupplierSku("LST-CEM-40");
        itemSupplier.setUnitCost(new BigDecimal("238.25"));
        return itemSupplier;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapItemSupplierToResponseWithAllFields() {
            ItemSupplier itemSupplier = buildItemSupplier();

            ItemSupplierResponse response = mapper.toResponse(itemSupplier);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(101L);
            assertThat(response.getItemId()).isEqualTo(42L);
            assertThat(response.getSupplierId()).isEqualTo(7L);
            assertThat(response.getSupplierSku()).isEqualTo("LST-CEM-40");
            assertThat(response.getUnitCost()).isEqualByComparingTo(new BigDecimal("238.25"));
        }

        @Test
        void shouldFlattenItemNameFromNestedItem() {
            ItemSupplier itemSupplier = buildItemSupplier();

            ItemSupplierResponse response = mapper.toResponse(itemSupplier);

            assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
        }

        @Test
        void shouldFlattenSupplierNameFromNestedSupplier() {
            ItemSupplier itemSupplier = buildItemSupplier();

            ItemSupplierResponse response = mapper.toResponse(itemSupplier);

            assertThat(response.getSupplierName()).isEqualTo("Luzon Steel Trading");
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapRequestScalarFieldsToEntity() {
            ItemSupplierRequest request = new ItemSupplierRequest();
            request.setItemId(42L);
            request.setSupplierId(7L);
            request.setSupplierSku("LST-CEM-40");
            request.setUnitCost(new BigDecimal("238.25"));

            ItemSupplier entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getSupplierSku()).isEqualTo("LST-CEM-40");
            assertThat(entity.getUnitCost()).isEqualByComparingTo(new BigDecimal("238.25"));
        }

        @Test
        void shouldNotResolveItemAndSupplierAssociationsFromRequestIds() {
            // Resolving itemId/supplierId to managed entities is the service
            // layer's job; the mapper must not fabricate them.
            ItemSupplierRequest request = new ItemSupplierRequest();
            request.setItemId(42L);
            request.setSupplierId(7L);
            request.setSupplierSku("LST-CEM-40");
            request.setUnitCost(new BigDecimal("238.25"));

            ItemSupplier entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getItem()).isNull();
            assertThat(entity.getSupplier()).isNull();
        }
    }
}