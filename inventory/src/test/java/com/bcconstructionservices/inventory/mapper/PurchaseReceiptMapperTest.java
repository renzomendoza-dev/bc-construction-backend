package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseReceiptResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import com.bcconstructionservices.inventory.entity.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PurchaseReceiptMapperTest {

    private PurchaseReceiptMapper mapper;

    private Supplier supplier;
    private Item cement;
    private Item rebar;

    @BeforeEach
    void setUp() {
        mapper = new PurchaseReceiptMapperImpl();
        ReflectionTestUtils.setField(mapper, "purchaseReceiptLineMapper", new PurchaseReceiptLineMapperImpl());

        supplier = new Supplier();
        supplier.setId(7L);
        supplier.setName("Luzon Steel Trading");

        cement = new Item();
        cement.setId(42L);
        cement.setName("Portland Cement 40kg");

        rebar = new Item();
        rebar.setId(43L);
        rebar.setName("Deformed Rebar 10mm x 6m");
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private PurchaseReceipt buildReceipt() {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setId(300L);
        receipt.setSupplier(supplier);
        receipt.setReceiptNumber("OR-2026-004512");
        receipt.setPurchaseDate(LocalDate.of(2026, 7, 10));
        receipt.setTotalAmount(new BigDecimal("13712.50"));
        receipt.setImageUrl("https://cdn.example.com/receipts/or-2026-004512.jpg");
        receipt.setNotes("Bulk order for Phase 2 foundation work");
        receipt.setCreatedAt(Instant.parse("2026-07-10T05:25:00Z"));
        receipt.setLines(new ArrayList<>());
        return receipt;
    }

    private PurchaseReceiptLine buildLine(Long id, PurchaseReceipt receipt, Item item,
                                          Integer quantity, String unitCost, String lineTotal) {
        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setId(id);
        line.setPurchaseReceipt(receipt);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal(unitCost));
        line.setLineTotal(new BigDecimal(lineTotal));
        return line;
    }

    // ---------------------------------------------------------------
    // toResponse
    // ---------------------------------------------------------------

    @Nested
    class ToResponse {

        @Test
        void shouldMapReceiptToResponseWithAllFields() {
            PurchaseReceipt receipt = buildReceipt();
            receipt.getLines().add(
                    buildLine(1L, receipt, cement, 50, "245.00", "12250.00"));

            PurchaseReceiptResponse response = mapper.toResponse(receipt);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(300L);
            assertThat(response.getSupplierId()).isEqualTo(7L);
            assertThat(response.getReceiptNumber()).isEqualTo("OR-2026-004512");
            assertThat(response.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("13712.50"));
            assertThat(response.getImageUrl())
                    .isEqualTo("https://cdn.example.com/receipts/or-2026-004512.jpg");
            assertThat(response.getNotes()).isEqualTo("Bulk order for Phase 2 foundation work");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-10T05:25:00Z"));
        }

        @Test
        void shouldFlattenSupplierNameFromNestedSupplier() {
            PurchaseReceipt receipt = buildReceipt();

            PurchaseReceiptResponse response = mapper.toResponse(receipt);

            assertThat(response.getSupplierName()).isEqualTo("Luzon Steel Trading");
        }

        @Test
        void shouldMapReceiptWithMultipleLinesInOrder() {
            PurchaseReceipt receipt = buildReceipt();
            receipt.getLines().add(
                    buildLine(1L, receipt, cement, 50, "245.00", "12250.00"));
            receipt.getLines().add(
                    buildLine(2L, receipt, rebar, 8, "158.75", "1270.00"));

            PurchaseReceiptResponse response = mapper.toResponse(receipt);

            assertThat(response.getLines()).hasSize(2);

            assertThat(response.getLines().get(0).getId()).isEqualTo(1L);
            assertThat(response.getLines().get(0).getItemId()).isEqualTo(42L);
            assertThat(response.getLines().get(0).getItemName()).isEqualTo("Portland Cement 40kg");
            assertThat(response.getLines().get(0).getQuantity()).isEqualTo(50);
            assertThat(response.getLines().get(0).getUnitCost())
                    .isEqualByComparingTo(new BigDecimal("245.00"));
            assertThat(response.getLines().get(0).getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("12250.00"));

            assertThat(response.getLines().get(1).getId()).isEqualTo(2L);
            assertThat(response.getLines().get(1).getItemId()).isEqualTo(43L);
            assertThat(response.getLines().get(1).getItemName())
                    .isEqualTo("Deformed Rebar 10mm x 6m");
            assertThat(response.getLines().get(1).getQuantity()).isEqualTo(8);
            assertThat(response.getLines().get(1).getUnitCost())
                    .isEqualByComparingTo(new BigDecimal("158.75"));
            assertThat(response.getLines().get(1).getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("1270.00"));
        }

        @Test
        void shouldMapReceiptWithEmptyLinesListWithoutError() {
            PurchaseReceipt receipt = buildReceipt();

            assertThatCode(() -> mapper.toResponse(receipt))
                    .doesNotThrowAnyException();

            PurchaseReceiptResponse response = mapper.toResponse(receipt);

            assertThat(response.getLines()).isNotNull();
            assertThat(response.getLines()).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // toEntity
    // ---------------------------------------------------------------

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            PurchaseReceiptLineRequest lineRequest = new PurchaseReceiptLineRequest();
            lineRequest.setItemId(42L);
            lineRequest.setQuantity(50);
            lineRequest.setUnitCost(new BigDecimal("245.00"));

            PurchaseReceiptCreateRequest request = new PurchaseReceiptCreateRequest();
            request.setSupplierId(7L);
            request.setReceiptNumber("OR-2026-004513");
            request.setPurchaseDate(LocalDate.of(2026, 7, 15));
            request.setImageUrl("https://cdn.example.com/receipts/or-2026-004513.jpg");
            request.setNotes("Restock after Phase 2 pour");
            request.setLines(List.of(lineRequest));

            PurchaseReceipt entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getReceiptNumber()).isEqualTo("OR-2026-004513");
            assertThat(entity.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 7, 15));
            assertThat(entity.getImageUrl())
                    .isEqualTo("https://cdn.example.com/receipts/or-2026-004513.jpg");
            assertThat(entity.getNotes()).isEqualTo("Restock after Phase 2 pour");
        }

        @Test
        void shouldNotSetServerManagedFieldsFromCreateRequest() {
            PurchaseReceiptCreateRequest request = new PurchaseReceiptCreateRequest();
            request.setSupplierId(7L);
            request.setReceiptNumber("OR-2026-004513");
            request.setPurchaseDate(LocalDate.of(2026, 7, 15));
            request.setLines(List.of());

            PurchaseReceipt entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            // totalAmount is computed by the service from line totals,
            // not supplied by the client.
            assertThat(entity.getTotalAmount()).isNull();
        }

        @Test
        void shouldNotResolveSupplierAssociationFromRequestId() {
            // Resolving supplierId to a managed Supplier entity is the
            // service layer's job; the mapper must not fabricate it.
            PurchaseReceiptCreateRequest request = new PurchaseReceiptCreateRequest();
            request.setSupplierId(7L);
            request.setReceiptNumber("OR-2026-004513");
            request.setPurchaseDate(LocalDate.of(2026, 7, 15));
            request.setLines(List.of());

            PurchaseReceipt entity = mapper.toEntity(request);

            assertThat(entity.getSupplier()).isNull();
        }
    }
}