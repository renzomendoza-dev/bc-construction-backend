package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.PurchaseReceiptLineResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReceiptLineMapperTest {

    private PurchaseReceiptLineMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PurchaseReceiptLineMapperImpl();
    }

    private PurchaseReceiptLine buildLine() {
        Item item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");

        PurchaseReceipt parentReceipt = new PurchaseReceipt();
        parentReceipt.setId(300L);

        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setId(1L);
        line.setPurchaseReceipt(parentReceipt);
        line.setItem(item);
        line.setQuantity(50);
        line.setUnitCost(new BigDecimal("245.00"));
        line.setLineTotal(new BigDecimal("12250.00"));
        return line;
    }

    @Test
    void shouldMapLineToResponseWithAllFields() {
        PurchaseReceiptLine line = buildLine();

        PurchaseReceiptLineResponse response = mapper.toResponse(line);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getItemId()).isEqualTo(42L);
        assertThat(response.getQuantity()).isEqualTo(50);
        assertThat(response.getUnitCost()).isEqualByComparingTo(new BigDecimal("245.00"));
        assertThat(response.getLineTotal()).isEqualByComparingTo(new BigDecimal("12250.00"));
    }

    @Test
    void shouldFlattenItemNameFromNestedItem() {
        PurchaseReceiptLine line = buildLine();

        PurchaseReceiptLineResponse response = mapper.toResponse(line);

        assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
    }
}