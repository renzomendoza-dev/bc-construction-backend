package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
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
class TransferLineItemRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransferLineItemRepository transferLineItemRepository;

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
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

    private TransferBatch persistBatch(Warehouse origin, Warehouse destination) {
        TransferBatch batch = new TransferBatch();
        batch.setOriginWarehouse(origin);
        batch.setDestinationWarehouse(destination);
        batch.setLineItems(new ArrayList<>());
        entityManager.persist(batch);
        entityManager.flush();
        return batch;
    }

    @Nested
    class FindByTransferBatchIdTests {

        @Test
        void shouldReturnOnlyLineItemsBelongingToTheGivenBatch() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");
            Item cement = persistItem("CEM-001", "Portland Cement 40kg");
            Item rebar = persistItem("RBR-010", "Deformed Rebar 10mm");

            TransferBatch batchA = persistBatch(main, site1);
            TransferBatch batchB = persistBatch(main, site1);

            TransferLineItem lineA1 = new TransferLineItem();
            lineA1.setTransferBatch(batchA);
            lineA1.setItem(cement);
            lineA1.setQuantity(50);
            entityManager.persist(lineA1);

            TransferLineItem lineA2 = new TransferLineItem();
            lineA2.setTransferBatch(batchA);
            lineA2.setItem(rebar);
            lineA2.setQuantity(8);
            entityManager.persist(lineA2);

            TransferLineItem lineB1 = new TransferLineItem();
            lineB1.setTransferBatch(batchB);
            lineB1.setItem(cement);
            lineB1.setQuantity(20);
            entityManager.persist(lineB1);
            entityManager.flush();
            entityManager.clear();

            List<TransferLineItem> result = transferLineItemRepository.findByTransferBatchId(batchA.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TransferLineItem::getQuantity).containsExactlyInAnyOrder(50, 8);
        }

        @Test
        void shouldReturnEmptyListWhenBatchHasNoLineItems() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");
            TransferBatch batch = persistBatch(main, site1);
            entityManager.clear();

            assertThat(transferLineItemRepository.findByTransferBatchId(batch.getId())).isEmpty();
        }
    }

    @Nested
    class QuantityPositiveConstraint {

        @Test
        void shouldRejectLineItemWithZeroOrNegativeQuantity() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");
            TransferBatch batch = persistBatch(main, site1);

            TransferLineItem line = new TransferLineItem();
            line.setTransferBatch(batch);
            line.setItem(item);
            line.setQuantity(0);

            assertThatThrownBy(() -> transferLineItemRepository.saveAndFlush(line))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
