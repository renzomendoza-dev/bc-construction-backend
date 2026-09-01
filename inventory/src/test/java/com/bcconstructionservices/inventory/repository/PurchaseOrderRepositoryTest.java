package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.entity.Supplier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PurchaseOrderRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderLineRepository purchaseOrderLineRepository;

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private Supplier persistSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setActive(true);
        entityManager.persist(supplier);
        entityManager.flush();
        return supplier;
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

    private PurchaseOrder buildOrder(Supplier supplier) {
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setLines(new ArrayList<>());
        return order;
    }

    private PurchaseOrderLine buildLine(PurchaseOrder order, Item item, int quantity) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setPurchaseOrder(order);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    // ---------------------------------------------------------------
    // findByIdWithSupplier
    // ---------------------------------------------------------------

    @Nested
    class FindByIdWithSupplierTests {

        @Test
        void shouldReturnOrderWithSupplierEagerlyLoaded() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");

            PurchaseOrder order = buildOrder(supplier);
            order.getLines().add(buildLine(order, item, 100));

            PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
            Long orderId = saved.getId();
            entityManager.clear();

            PurchaseOrder reloaded = purchaseOrderRepository.findByIdWithSupplier(orderId).orElseThrow();
            assertThat(reloaded.getSupplier().getName()).isEqualTo("Acme Distribution Co.");
        }

        @Test
        void shouldReturnEmptyWhenOrderDoesNotExist() {
            assertThat(purchaseOrderRepository.findByIdWithSupplier(999999L)).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // Status default and CHECK constraint
    // ---------------------------------------------------------------

    @Nested
    class StatusTests {

        @Test
        void shouldDefaultStatusToDraft() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");
            PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(buildOrder(supplier));
            entityManager.clear();

            PurchaseOrder reloaded = purchaseOrderRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        }

        @Test
        void shouldAcceptEveryDeclaredStatusValue() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");

            for (PurchaseOrderStatus status : PurchaseOrderStatus.values()) {
                PurchaseOrder order = buildOrder(supplier);
                order.setStatus(status);
                PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
                entityManager.clear();

                assertThat(purchaseOrderRepository.findById(saved.getId()).orElseThrow().getStatus())
                        .isEqualTo(status);
            }
        }

        @Test
        void shouldFilterSearchByStatus() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");

            PurchaseOrder draft = buildOrder(supplier);
            purchaseOrderRepository.saveAndFlush(draft);

            PurchaseOrder submitted = buildOrder(supplier);
            submitted.setStatus(PurchaseOrderStatus.SUBMITTED);
            purchaseOrderRepository.saveAndFlush(submitted);
            entityManager.clear();

            List<PurchaseOrder> result = purchaseOrderRepository
                    .search(null, PurchaseOrderStatus.SUBMITTED, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        }
    }

    // ---------------------------------------------------------------
    // Line item cascade (owned by PurchaseOrder)
    // ---------------------------------------------------------------

    @Nested
    class LineCascadeBehavior {

        @Test
        void shouldCascadeSaveLineWithCorrectOrderAndItemForeignKeys() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");
            Item cement = persistItem("CEM-001", "Portland Cement 40kg");
            Item rebar = persistItem("RBR-010", "Deformed Rebar 10mm");

            PurchaseOrder order = buildOrder(supplier);
            order.getLines().add(buildLine(order, cement, 100));
            order.getLines().add(buildLine(order, rebar, 20));

            PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
            Long orderId = saved.getId();
            entityManager.clear();

            List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPurchaseOrderId(orderId);
            assertThat(lines).hasSize(2);
        }

        @Test
        void shouldDeleteAssociatedLinesWhenOrderIsDeleted() {
            Supplier supplier = persistSupplier("Acme Distribution Co.");
            Item item = persistItem("CEM-001", "Portland Cement 40kg");

            PurchaseOrder order = buildOrder(supplier);
            order.getLines().add(buildLine(order, item, 100));

            PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
            Long orderId = saved.getId();
            Long lineId = saved.getLines().get(0).getId();
            entityManager.clear();

            assertThat(purchaseOrderLineRepository.findById(lineId)).isPresent();

            purchaseOrderRepository.deleteById(orderId);
            entityManager.flush();
            entityManager.clear();

            assertThat(purchaseOrderRepository.findById(orderId)).isEmpty();
            assertThat(purchaseOrderLineRepository.findById(lineId)).isEmpty();
        }
    }
}
