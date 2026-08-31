package com.bcconstructionservices.inventory.mapper;

import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemResponse;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialRequestLineItemMapperTest {

    private MaterialRequestLineItemMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MaterialRequestLineItemMapperImpl();
    }

    private MaterialRequestLineItem buildLine() {
        Item item = new Item();
        item.setId(42L);
        item.setName("Portland Cement 40kg");

        MaterialRequest parentRequest = new MaterialRequest();
        parentRequest.setId(14L);

        MaterialRequestLineItem line = new MaterialRequestLineItem();
        line.setId(88L);
        line.setMaterialRequest(parentRequest);
        line.setItem(item);
        line.setQuantityRequested(50);
        line.setNotes("Needed before Friday's pour");
        return line;
    }

    @Test
    void shouldMapLineToResponseWithAllFields() {
        MaterialRequestLineItem line = buildLine();

        MaterialRequestLineItemResponse response = mapper.toResponse(line);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(88L);
        assertThat(response.getItemId()).isEqualTo(42L);
        assertThat(response.getQuantityRequested()).isEqualTo(50);
        assertThat(response.getNotes()).isEqualTo("Needed before Friday's pour");
    }

    @Test
    void shouldFlattenItemNameFromNestedItem() {
        MaterialRequestLineItem line = buildLine();

        MaterialRequestLineItemResponse response = mapper.toResponse(line);

        assertThat(response.getItemName()).isEqualTo("Portland Cement 40kg");
    }
}
