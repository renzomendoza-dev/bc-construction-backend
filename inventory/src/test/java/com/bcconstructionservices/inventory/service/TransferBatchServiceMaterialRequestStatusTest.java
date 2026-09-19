package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.mapper.TransferBatchMapper;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransferBatchService.submit's MaterialRequest status update, against real
 * repositories on Postgres. A request can be fulfilled by several batches over
 * time, so its status must reflect every COMPLETED batch against it, not just
 * the one being submitted. Stock movement, the mapper, and project expenses
 * are mocked — none of them affect the status decision.
 */
@DataJpaTest
@Import(TransferBatchService.class)
class TransferBatchServiceMaterialRequestStatusTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransferBatchService transferBatchService;

    @MockitoBean
    private InventoryService inventoryService;
    @MockitoBean
    private TransferBatchMapper transferBatchMapper;
    @MockitoBean
    private CurrentUserService currentUserService;
    @MockitoBean
    private TransferBatchStatusUpdater transferBatchStatusUpdater;
    @MockitoBean
    private ProjectExpenseService projectExpenseService;

    private Warehouse main;
    private Warehouse site;
    private Item cement;
    private Item rebar;

    @BeforeEach
    void setUp() {
        main = persistWarehouse("WH-MAIN", WarehouseType.MAIN);
        site = persistWarehouse("WH-SITE", WarehouseType.SITE);
        cement = persistItem("CEM-001", "Portland Cement 40kg");
        rebar = persistItem("RBR-010", "Deformed Rebar 10mm");
    }

    @Test
    void twoPartialBatchesThatTogetherCoverTheRequestMarkItFulfilled() {
        MaterialRequest request = persistRequest(Map.of(cement, 100));
        TransferBatch first = persistBatch(request, Map.of(cement, 60));
        TransferBatch second = persistBatch(request, Map.of(cement, 40));

        transferBatchService.submit(first.getId());
        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.PARTIALLY_FULFILLED);

        transferBatchService.submit(second.getId());
        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.FULFILLED);
    }

    @Test
    void aTopUpBatchAfterFulfillmentDoesNotDowngradeTheRequest() {
        MaterialRequest request = persistRequest(Map.of(cement, 100));
        TransferBatch full = persistBatch(request, Map.of(cement, 100));
        TransferBatch topUp = persistBatch(request, Map.of(cement, 10));

        transferBatchService.submit(full.getId());
        transferBatchService.submit(topUp.getId());

        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.FULFILLED);
    }

    @Test
    void aBatchThatIsStillDraftDoesNotCountTowardFulfillment() {
        MaterialRequest request = persistRequest(Map.of(cement, 100));
        TransferBatch submitted = persistBatch(request, Map.of(cement, 60));
        persistBatch(request, Map.of(cement, 40));

        transferBatchService.submit(submitted.getId());

        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void eachItemIsSummedSeparatelyAcrossBatches() {
        MaterialRequest request = persistRequest(Map.of(cement, 100, rebar, 50));
        TransferBatch cementBatch = persistBatch(request, Map.of(cement, 100));
        TransferBatch rebarBatch = persistBatch(request, Map.of(rebar, 50));

        transferBatchService.submit(cementBatch.getId());
        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.PARTIALLY_FULFILLED);

        transferBatchService.submit(rebarBatch.getId());
        assertThat(statusOf(request)).isEqualTo(MaterialRequestStatus.FULFILLED);
    }

    private MaterialRequestStatus statusOf(MaterialRequest request) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(MaterialRequest.class, request.getId()).getStatus();
    }

    private Warehouse persistWarehouse(String code, WarehouseType type) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(code);
        warehouse.setActive(true);
        warehouse.setType(type);
        entityManager.persist(warehouse);
        return warehouse;
    }

    private Item persistItem(String sku, String name) {
        Item item = new Item();
        item.setSku(sku);
        item.setName(name);
        item.setCategory("Materials");
        item.setUnitOfMeasure("pc");
        item.setSellingPrice(new BigDecimal("100.00"));
        item.setDefaultCostPrice(new BigDecimal("80.00"));
        item.setActive(true);
        item.setImages(new ArrayList<>());
        entityManager.persist(item);
        return item;
    }

    private MaterialRequest persistRequest(Map<Item, Integer> quantities) {
        MaterialRequest request = MaterialRequest.builder()
                .site(site)
                .status(MaterialRequestStatus.SUBMITTED)
                .build();
        quantities.forEach((item, quantity) -> request.getLineItems().add(MaterialRequestLineItem.builder()
                .materialRequest(request)
                .item(item)
                .quantityRequested(quantity)
                .build()));
        entityManager.persist(request);
        return request;
    }

    private TransferBatch persistBatch(MaterialRequest request, Map<Item, Integer> quantities) {
        TransferBatch batch = TransferBatch.builder()
                .originWarehouse(main)
                .destinationWarehouse(site)
                .sourceMaterialRequestId(request.getId())
                .build();
        quantities.forEach((item, quantity) -> batch.getLineItems().add(TransferLineItem.builder()
                .transferBatch(batch)
                .item(item)
                .quantity(quantity)
                .build()));
        entityManager.persist(batch);
        entityManager.flush();
        return batch;
    }
}
