package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MaterialRequestRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MaterialRequestRepository materialRequestRepository;

    private Warehouse persistWarehouse(String code, String name, WarehouseType type) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        warehouse.setType(type);
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

    private MaterialRequest buildRequest(Warehouse site) {
        MaterialRequest request = new MaterialRequest();
        request.setSite(site);
        request.setLineItems(new ArrayList<>());
        return request;
    }

    @Nested
    class FindByIdWithSiteTests {

        @Test
        void shouldReturnRequestWithSiteEagerlyLoaded() {
            Warehouse site = persistWarehouse("WH-SITE1", "Site Warehouse", WarehouseType.SITE);
            MaterialRequest request = buildRequest(site);

            MaterialRequest saved = materialRequestRepository.saveAndFlush(request);
            Long requestId = saved.getId();
            entityManager.clear();

            MaterialRequest reloaded = materialRequestRepository.findByIdWithSite(requestId).orElseThrow();

            assertThat(reloaded.getSite().getCode()).isEqualTo("WH-SITE1");
        }

        @Test
        void shouldReturnEmptyWhenRequestDoesNotExist() {
            assertThat(materialRequestRepository.findByIdWithSite(999999L)).isEmpty();
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterBySiteWarehouseId() {
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1", WarehouseType.SITE);
            Warehouse site2 = persistWarehouse("WH-SITE2", "Site 2", WarehouseType.SITE);

            materialRequestRepository.saveAndFlush(buildRequest(site1));
            materialRequestRepository.saveAndFlush(buildRequest(site2));
            entityManager.clear();

            List<MaterialRequest> result = materialRequestRepository
                    .search(site1.getId(), null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSite().getId()).isEqualTo(site1.getId());
        }

        @Test
        void shouldFilterByStatus() {
            Warehouse site = persistWarehouse("WH-SITE1", "Site 1", WarehouseType.SITE);

            MaterialRequest submitted = buildRequest(site);
            submitted.setStatus(MaterialRequestStatus.SUBMITTED);
            materialRequestRepository.saveAndFlush(submitted);

            MaterialRequest fulfilled = buildRequest(site);
            fulfilled.setStatus(MaterialRequestStatus.FULFILLED);
            materialRequestRepository.saveAndFlush(fulfilled);
            entityManager.clear();

            List<MaterialRequest> result = materialRequestRepository
                    .search(null, MaterialRequestStatus.FULFILLED, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(MaterialRequestStatus.FULFILLED);
        }

        @Test
        void shouldReturnAllRequestsWhenNoFiltersProvided() {
            Warehouse site1 = persistWarehouse("WH-SITE1", "Site 1", WarehouseType.SITE);
            Warehouse site2 = persistWarehouse("WH-SITE2", "Site 2", WarehouseType.SITE);

            materialRequestRepository.saveAndFlush(buildRequest(site1));
            materialRequestRepository.saveAndFlush(buildRequest(site2));
            entityManager.clear();

            List<MaterialRequest> result = materialRequestRepository
                    .search(null, null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class RequiredSiteConstraint {

        @Test
        void shouldThrowConstraintViolationWhenSiteIsMissing() {
            MaterialRequest request = new MaterialRequest();
            request.setLineItems(new ArrayList<>());
            // site intentionally left unset.

            assertThatThrownBy(() -> materialRequestRepository.saveAndFlush(request))
                    .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
        }
    }

    @Nested
    class LineItemCascadeBehavior {

        @Test
        void shouldCascadeSaveLineItemsWithCorrectRequestForeignKey() {
            Warehouse site = persistWarehouse("WH-SITE1", "Site Warehouse", WarehouseType.SITE);
            Item cement = persistItem("CEM-001", "Portland Cement 40kg");

            MaterialRequest request = buildRequest(site);
            MaterialRequestLineItem line = new MaterialRequestLineItem();
            line.setMaterialRequest(request);
            line.setItem(cement);
            line.setQuantityRequested(50);
            request.getLineItems().add(line);

            MaterialRequest saved = materialRequestRepository.saveAndFlush(request);
            Long requestId = saved.getId();
            entityManager.clear();

            MaterialRequest reloaded = materialRequestRepository.findByIdWithSite(requestId).orElseThrow();
            assertThat(reloaded.getLineItems()).hasSize(1);
        }
    }
}
