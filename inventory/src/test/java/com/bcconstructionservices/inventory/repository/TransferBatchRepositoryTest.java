package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TransferBatchRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransferBatchRepository transferBatchRepository;

    @Autowired
    private TransferLineItemRepository transferLineItemRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

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

    private TransferBatch buildBatch(Warehouse origin, Warehouse destination) {
        TransferBatch batch = new TransferBatch();
        batch.setOriginWarehouse(origin);
        batch.setDestinationWarehouse(destination);
        batch.setLineItems(new ArrayList<>());
        return batch;
    }

    private TransferLineItem buildLine(TransferBatch batch, Item item, int quantity) {
        TransferLineItem line = new TransferLineItem();
        line.setTransferBatch(batch);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    // ---------------------------------------------------------------
    // findByIdWithWarehouses
    // ---------------------------------------------------------------

    @Nested
    class FindByIdWithWarehousesTests {

        @Test
        void shouldReturnBatchWithOriginAndDestinationWarehousesEagerlyLoaded() {
            Warehouse origin = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse destination = persistWarehouse("WH-SITE1", "Site Warehouse");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");

            TransferBatch batch = buildBatch(origin, destination);
            batch.getLineItems().add(buildLine(batch, item, 50));

            TransferBatch saved = transferBatchRepository.saveAndFlush(batch);
            Long batchId = saved.getId();
            entityManager.clear();

            TransferBatch reloaded = transferBatchRepository.findByIdWithWarehouses(batchId).orElseThrow();

            assertThat(reloaded.getOriginWarehouse().getCode()).isEqualTo("WH-MAIN");
            assertThat(reloaded.getDestinationWarehouse().getCode()).isEqualTo("WH-SITE1");
        }

        @Test
        void shouldReturnEmptyWhenBatchDoesNotExist() {
            assertThat(transferBatchRepository.findByIdWithWarehouses(999999L)).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // search
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByOriginWarehouseId() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");
            Warehouse site2 = persistWarehouse("WH-SITE2", "Site 2");

            transferBatchRepository.saveAndFlush(buildBatch(main, site1));
            transferBatchRepository.saveAndFlush(buildBatch(site2, main));
            entityManager.clear();

            List<TransferBatch> result =
                    transferBatchRepository.search(main.getId(), null, null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOriginWarehouse().getId()).isEqualTo(main.getId());
        }

        @Test
        void shouldFilterByDestinationWarehouseId() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");
            Warehouse site2 = persistWarehouse("WH-SITE2", "Site 2");

            transferBatchRepository.saveAndFlush(buildBatch(main, site1));
            transferBatchRepository.saveAndFlush(buildBatch(main, site2));
            entityManager.clear();

            List<TransferBatch> result = transferBatchRepository
                    .search(null, site2.getId(), null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDestinationWarehouse().getId()).isEqualTo(site2.getId());
        }

        @Test
        void shouldFilterByStatus() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");

            TransferBatch draft = buildBatch(main, site1);
            draft.setStatus(TransferBatchStatus.DRAFT);
            transferBatchRepository.saveAndFlush(draft);

            TransferBatch completed = buildBatch(main, site1);
            completed.setStatus(TransferBatchStatus.COMPLETED);
            transferBatchRepository.saveAndFlush(completed);
            entityManager.clear();

            List<TransferBatch> result = transferBatchRepository
                    .search(null, null, TransferBatchStatus.COMPLETED, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(TransferBatchStatus.COMPLETED);
        }

        @Test
        void shouldReturnAllBatchesWhenNoFiltersProvided() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");

            transferBatchRepository.saveAndFlush(buildBatch(main, site1));
            transferBatchRepository.saveAndFlush(buildBatch(site1, main));
            entityManager.clear();

            List<TransferBatch> result = transferBatchRepository
                    .search(null, null, null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(2);
        }
    }

    // ---------------------------------------------------------------
    // CHECK constraint: origin <> destination
    // ---------------------------------------------------------------

    @Nested
    class OriginDestinationDistinctConstraint {

        @Test
        void shouldRejectBatchWithIdenticalOriginAndDestination() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Warehouse");

            TransferBatch batch = buildBatch(warehouse, warehouse);

            assertThatThrownBy(() -> transferBatchRepository.saveAndFlush(batch))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ---------------------------------------------------------------
    // CHECK constraint: status (V23 widened this to allow AWAITING_PURCHASE)
    // ---------------------------------------------------------------

    @Nested
    class StatusConstraint {

        @Test
        void shouldAcceptAwaitingPurchaseStatus() {
            Warehouse origin = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse destination = persistWarehouse("WH-SITE1", "Site Warehouse");

            TransferBatch batch = buildBatch(origin, destination);
            batch.setStatus(TransferBatchStatus.AWAITING_PURCHASE);

            TransferBatch saved = transferBatchRepository.saveAndFlush(batch);
            entityManager.clear();

            TransferBatch reloaded = transferBatchRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(TransferBatchStatus.AWAITING_PURCHASE);
        }

        @Test
        void shouldFilterSearchByAwaitingPurchaseStatus() {
            Warehouse main = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1");

            TransferBatch blocked = buildBatch(main, site1);
            blocked.setStatus(TransferBatchStatus.AWAITING_PURCHASE);
            transferBatchRepository.saveAndFlush(blocked);

            TransferBatch draft = buildBatch(main, site1);
            draft.setStatus(TransferBatchStatus.DRAFT);
            transferBatchRepository.saveAndFlush(draft);
            entityManager.clear();

            List<TransferBatch> result = transferBatchRepository
                    .search(null, null, TransferBatchStatus.AWAITING_PURCHASE, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(TransferBatchStatus.AWAITING_PURCHASE);
        }
    }

    // ---------------------------------------------------------------
    // Line item cascade (owned by TransferBatch)
    // ---------------------------------------------------------------

    @Nested
    class LineItemCascadeBehavior {

        @Test
        void shouldCascadeSaveLineItemsWithCorrectBatchForeignKey() {
            Warehouse origin = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse destination = persistWarehouse("WH-SITE1", "Site Warehouse");
            Item cement = persistItem("CEM-001", "Portland Cement 40kg");
            Item rebar = persistItem("RBR-010", "Deformed Rebar 10mm");

            TransferBatch batch = buildBatch(origin, destination);
            batch.getLineItems().add(buildLine(batch, cement, 50));
            batch.getLineItems().add(buildLine(batch, rebar, 8));

            TransferBatch saved = transferBatchRepository.saveAndFlush(batch);
            Long batchId = saved.getId();
            entityManager.clear();

            TransferBatch reloaded = transferBatchRepository.findByIdWithWarehouses(batchId).orElseThrow();
            assertThat(reloaded.getLineItems()).hasSize(2);
        }

        @Test
        void shouldDeleteAssociatedLineItemsWhenBatchIsDeleted() {
            Warehouse origin = persistWarehouse("WH-MAIN", "Main Warehouse");
            Warehouse destination = persistWarehouse("WH-SITE1", "Site Warehouse");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");

            TransferBatch batch = buildBatch(origin, destination);
            batch.getLineItems().add(buildLine(batch, item, 50));

            TransferBatch saved = transferBatchRepository.saveAndFlush(batch);
            Long batchId = saved.getId();
            Long lineId = saved.getLineItems().get(0).getId();
            entityManager.clear();

            assertThat(transferLineItemRepository.findById(lineId)).isPresent();

            transferBatchRepository.deleteById(batchId);
            entityManager.flush();
            entityManager.clear();

            assertThat(transferBatchRepository.findById(batchId)).isEmpty();
            // No orphaned TransferLineItem row left behind.
            assertThat(transferLineItemRepository.findById(lineId)).isEmpty();
        }
    }
}
