package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.TransferLineItemResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferLineItemMapperTest {

    private TransferLineItemMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TransferLineItemMapperImpl();
    }

    private TransferLineItem buildLine() {
        Item item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");

        TransferBatch parentBatch = new TransferBatch();
        parentBatch.setId(15L);

        TransferLineItem line = new TransferLineItem();
        line.setId(301L);
        line.setTransferBatch(parentBatch);
        line.setItem(item);
        line.setExpectedQuantity(52);
        line.setQuantity(50);
        line.setNotes("2 bags damaged, excluded from count");
        return line;
    }

    @Test
    void shouldMapLineToResponseWithAllFields() {
        TransferLineItem line = buildLine();

        TransferLineItemResponse response = mapper.toResponse(line);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(301L);
        assertThat(response.getItemId()).isEqualTo(42L);
        assertThat(response.getExpectedQuantity()).isEqualTo(52);
        assertThat(response.getQuantity()).isEqualTo(50);
        assertThat(response.getNotes()).isEqualTo("2 bags damaged, excluded from count");
    }

    @Test
    void shouldFlattenItemNameFromNestedItem() {
        TransferLineItem line = buildLine();

        TransferLineItemResponse response = mapper.toResponse(line);

        assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
    }

    @Test
    void shouldMapNullExpectedQuantityForADispatchLine() {
        TransferLineItem line = buildLine();
        line.setExpectedQuantity(null);

        TransferLineItemResponse response = mapper.toResponse(line);

        assertThat(response.getExpectedQuantity()).isNull();
    }
}
