package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MaterialRequestLineItemRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MaterialRequestLineItemRepository materialRequestLineItemRepository;

    private Warehouse persistSite(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        warehouse.setType(WarehouseType.SITE);
        entityManager.persist(warehouse);
        entityManager.flush();
        return warehouse;
    }

    private Item persistItem(String sku, String name) {
        Item item = new Item();
        item.setSku(sku);
        item.setName(name);
        item.setActive(true);
        item.setImages(new ArrayList<>());
        entityManager.persist(item);
        entityManager.flush();
        return item;
    }

    private MaterialRequest persistRequest(Warehouse site) {
        MaterialRequest request = new MaterialRequest();
        request.setSite(site);
        request.setLineItems(new ArrayList<>());
        entityManager.persist(request);
        entityManager.flush();
        return request;
    }

    @Nested
    class FindByMaterialRequestIdTests {

        @Test
        void shouldReturnOnlyLineItemsBelongingToTheGivenRequest() {
            Warehouse site = persistSite("WH-SITE1", "Site 1");
            Item cement = persistItem("CEM-001", "Portland Cement 40kg");
            Item rebar = persistItem("RBR-010", "Deformed Rebar 10mm");

            MaterialRequest requestA = persistRequest(site);
            MaterialRequest requestB = persistRequest(site);

            MaterialRequestLineItem lineA1 = new MaterialRequestLineItem();
            lineA1.setMaterialRequest(requestA);
            lineA1.setItem(cement);
            lineA1.setQuantityRequested(50);
            entityManager.persist(lineA1);

            MaterialRequestLineItem lineA2 = new MaterialRequestLineItem();
            lineA2.setMaterialRequest(requestA);
            lineA2.setItem(rebar);
            lineA2.setQuantityRequested(8);
            entityManager.persist(lineA2);

            MaterialRequestLineItem lineB1 = new MaterialRequestLineItem();
            lineB1.setMaterialRequest(requestB);
            lineB1.setItem(cement);
            lineB1.setQuantityRequested(20);
            entityManager.persist(lineB1);
            entityManager.flush();
            entityManager.clear();

            List<MaterialRequestLineItem> result =
                    materialRequestLineItemRepository.findByMaterialRequestId(requestA.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(MaterialRequestLineItem::getQuantityRequested)
                    .containsExactlyInAnyOrder(50, 8);
        }

        @Test
        void shouldReturnEmptyListWhenRequestHasNoLineItems() {
            Warehouse site = persistSite("WH-SITE1", "Site 1");
            MaterialRequest request = persistRequest(site);
            entityManager.clear();

            assertThat(materialRequestLineItemRepository.findByMaterialRequestId(request.getId())).isEmpty();
        }
    }

    @Nested
    class QuantityRequestedPositiveConstraint {

        @Test
        void shouldRejectLineItemWithZeroOrNegativeQuantityRequested() {
            Warehouse site = persistSite("WH-SITE1", "Site 1");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");
            MaterialRequest request = persistRequest(site);

            MaterialRequestLineItem line = new MaterialRequestLineItem();
            line.setMaterialRequest(request);
            line.setItem(item);
            line.setQuantityRequested(0);

            assertThatThrownBy(() -> materialRequestLineItemRepository.saveAndFlush(line))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
