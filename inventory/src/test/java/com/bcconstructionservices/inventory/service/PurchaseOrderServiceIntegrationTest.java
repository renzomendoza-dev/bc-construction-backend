package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.entity.PurchaseReceipt;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import com.bcconstructionservices.inventory.entity.Supplier;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.mapper.PurchaseOrderLineMapperImpl;
import com.bcconstructionservices.inventory.mapper.PurchaseOrderMapperImpl;
import com.bcconstructionservices.inventory.repository.SupplierRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @DataJpaTest slice tests for PurchaseOrderService.submit/close against a
 * REAL H2-backed Hibernate session (not Mockito mocks) — the previous unit
 * tests in PurchaseOrderServiceTest mock every repository, so they can't
 * catch actual Hibernate session-consistency bugs. PurchaseOrderService and
 * its mapper are pulled in as real beans via @Import, since a plain
 * @DataJpaTest slice only auto-configures repositories.
 *
 * <p>These tests exercise submit()/close() end-to-end against a DRAFT order
 * that already has a CONFIRMED PurchaseReceipt against it (createPurchaseReceipt
 * allows linking to a DRAFT order — see PurchaseReceiptService), which is
 * exactly the shape of the reported "Found shared references to a
 * collection: PurchaseOrder.lines" 500. That specific exception could not be
 * reproduced locally even before the fix (see submit()'s javadoc for the
 * root-cause theory this fix addresses: an explicit, redundant
 * repository.save() on an already-managed entity, called before its
 * cascade=ALL `lines` collection was ever touched this transaction) — kept
 * here as real-Hibernate regression coverage for this code path either way,
 * since none existed before.
 */
@DataJpaTest
@Import({PurchaseOrderService.class, PurchaseOrderMapperImpl.class, PurchaseOrderLineMapperImpl.class,
        PurchaseOrderServiceIntegrationTest.ReentrantAuditingConfig.class})
class PurchaseOrderServiceIntegrationTest {

    /**
     * Replicates the ONE real difference between this test's context and
     * production that turned out to matter: app/JpaAuditingConfig enables
     * JPA auditing there, backed by AuditorAwareImpl, whose own javadoc
     * documents that resolving the auditor "queries UserRepository, which
     * can make Hibernate auto-flush other still-dirty entities before
     * running that query." That's a REENTRANT auto-flush — triggered from
     * inside the @PreUpdate callback of a flush that's already in progress
     * for the very same dirty PurchaseOrder — which is what actually
     * produces "Found shared references to a collection: PurchaseOrder.lines".
     * InventoryTestConfig has no @EnableJpaAuditing at all, which is exactly
     * why every earlier attempt at this test could not reproduce the bug.
     */
    @org.springframework.boot.test.context.TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "reentrantTestAuditorAware")
    static class ReentrantAuditingConfig {

        @Bean
        AuditorAware<Long> reentrantTestAuditorAware(SupplierRepository supplierRepository) {
            // Mirrors AuditorAwareImpl exactly: the RESOLVING reentrancy guard
            // (see its javadoc — without it, this recurses until
            // StackOverflowError instead of surfacing the one-level-deep
            // "shared references to a collection" bug the guard doesn't
            // prevent), AND a cache of the resolved value (AuditorAwareImpl's
            // is RequestAttributes-scoped; a plain field is the @DataJpaTest
            // equivalent, since there's no real HTTP request here to scope
            // it to) so a caller can pre-warm it and have later, nested
            // @PreUpdate-triggered calls hit the cache instead of querying
            // again.
            ThreadLocal<Boolean> resolving = ThreadLocal.withInitial(() -> false);
            java.util.concurrent.atomic.AtomicBoolean cached = new java.util.concurrent.atomic.AtomicBoolean(false);
            return () -> {
                if (resolving.get()) {
                    return Optional.empty();
                }
                if (cached.get()) {
                    return Optional.empty();
                }
                resolving.set(true);
                try {
                    supplierRepository.count();
                    cached.set(true);
                    return Optional.empty();
                } finally {
                    resolving.remove();
                }
            };
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @MockitoBean
    private UserLookupHelper userLookupHelper;

    private Supplier persistSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setActive(true);
        entityManager.persist(supplier);
        return supplier;
    }

    private Item persistItem(String sku, String name) {
        Item item = new Item();
        item.setSku(sku);
        item.setName(name);
        item.setActive(true);
        item.setImages(new ArrayList<>());
        entityManager.persist(item);
        return item;
    }

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        warehouse.setType(WarehouseType.MAIN);
        entityManager.persist(warehouse);
        return warehouse;
    }

    private PurchaseOrder persistDraftOrderWithLine(Supplier supplier, Item item, int quantity) {
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setLines(new ArrayList<>());
        entityManager.persist(order);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setPurchaseOrder(order);
        line.setItem(item);
        line.setQuantity(quantity);
        entityManager.persist(line);
        order.getLines().add(line);

        return order;
    }

    private void persistConfirmedReceiptAgainstOrder(Supplier supplier, Warehouse warehouse, Item item,
                                                       Long purchaseOrderId, int quantity) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);
        receipt.setPurchaseDate(LocalDate.of(2026, 8, 1));
        receipt.setPurchaseOrderId(purchaseOrderId);
        receipt.setConfirmed(true);
        entityManager.persist(receipt);

        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.setPurchaseReceipt(receipt);
        line.setItem(item);
        line.setQuantity(quantity);
        line.setUnitCost(new BigDecimal("10.00"));
        entityManager.persist(line);
    }

    @Test
    void shouldCreateDraftOrderWithoutHibernateErrorEvenThoughItNeverPreWarmsTheAuditorCache() {
        // Unlike update()/submit()/close(), createDraft() does NOT call
        // auditorAware.getCurrentAuditor() early — and doesn't need to.
        // PurchaseOrder uses GenerationType.IDENTITY, so its INSERT (and the
        // @PrePersist auditing callback that comes with it) runs
        // synchronously inside save(), not deferred to a later flush — by
        // the time receivedQuantityByItemId's query runs, the order is
        // already fully persisted and clean, so there's no still-in-progress
        // flush for a reentrant query to collide with.
        Supplier supplier = persistSupplier("Acme Distribution Co.");
        Item item = persistItem("CEM-004", "Portland Cement 40kg");

        PurchaseOrderLineRequest lineRequest = new PurchaseOrderLineRequest();
        lineRequest.setItemId(item.getId());
        lineRequest.setQuantity(100);
        PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest();
        request.setSupplierId(supplier.getId());
        request.setLines(java.util.List.of(lineRequest));

        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);

        assertThat(response.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(response.getLines()).hasSize(1);
    }

    @Test
    void shouldSubmitDraftOrderThatAlreadyHasAConfirmedReceiptAgainstItWithoutHibernateError() {
        Supplier supplier = persistSupplier("Acme Distribution Co.");
        Item item = persistItem("CEM-001", "Portland Cement 40kg");
        Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Warehouse");
        PurchaseOrder order = persistDraftOrderWithLine(supplier, item, 100);
        persistConfirmedReceiptAgainstOrder(supplier, warehouse, item, order.getId(), 40);

        entityManager.flush();
        entityManager.clear();

        PurchaseOrderResponse response = purchaseOrderService.submit(order.getId());

        assertThat(response.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getReceivedQuantity()).isEqualTo(40);
    }

    @Test
    void shouldSubmitDraftOrderWithMultipleLinesAndMultipleConfirmedReceiptsWithoutHibernateError() {
        Supplier supplier = persistSupplier("Acme Distribution Co.");
        Item cement = persistItem("CEM-002", "Portland Cement 40kg");
        Item rebar = persistItem("RBR-011", "Deformed Rebar 10mm");
        Warehouse warehouse = persistWarehouse("WH-MAIN3", "Main Warehouse 3");

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setLines(new ArrayList<>());
        entityManager.persist(order);

        PurchaseOrderLine cementLine = new PurchaseOrderLine();
        cementLine.setPurchaseOrder(order);
        cementLine.setItem(cement);
        cementLine.setQuantity(100);
        entityManager.persist(cementLine);
        order.getLines().add(cementLine);

        PurchaseOrderLine rebarLine = new PurchaseOrderLine();
        rebarLine.setPurchaseOrder(order);
        rebarLine.setItem(rebar);
        rebarLine.setQuantity(50);
        entityManager.persist(rebarLine);
        order.getLines().add(rebarLine);

        persistConfirmedReceiptAgainstOrder(supplier, warehouse, cement, order.getId(), 40);
        persistConfirmedReceiptAgainstOrder(supplier, warehouse, rebar, order.getId(), 10);
        persistConfirmedReceiptAgainstOrder(supplier, warehouse, cement, order.getId(), 20);

        entityManager.flush();
        entityManager.clear();

        assertThatCode(() -> purchaseOrderService.submit(order.getId())).doesNotThrowAnyException();
    }

    @Test
    void shouldUpdateDraftOrderThatAlreadyHasAConfirmedReceiptAgainstItWithoutHibernateError() {
        Supplier supplier = persistSupplier("Acme Distribution Co.");
        Item item = persistItem("CEM-003", "Portland Cement 40kg");
        Warehouse warehouse = persistWarehouse("WH-MAIN4", "Main Warehouse 4");
        PurchaseOrder order = persistDraftOrderWithLine(supplier, item, 100);
        persistConfirmedReceiptAgainstOrder(supplier, warehouse, item, order.getId(), 40);

        entityManager.flush();
        entityManager.clear();

        PurchaseOrderLineRequest lineRequest = new PurchaseOrderLineRequest();
        lineRequest.setItemId(item.getId());
        lineRequest.setQuantity(75);
        PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
        request.setNotes("Updated notes");
        request.setLines(java.util.List.of(lineRequest));

        PurchaseOrderResponse response = purchaseOrderService.update(order.getId(), request);

        assertThat(response.getNotes()).isEqualTo("Updated notes");
        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getQuantity()).isEqualTo(75);
    }

    @Test
    void shouldCloseDraftOrderThatAlreadyHasAConfirmedReceiptAgainstItWithoutHibernateError() {
        Supplier supplier = persistSupplier("Acme Distribution Co.");
        Item item = persistItem("RBR-010", "Deformed Rebar 10mm");
        Warehouse warehouse = persistWarehouse("WH-MAIN2", "Main Warehouse 2");
        PurchaseOrder order = persistDraftOrderWithLine(supplier, item, 50);
        persistConfirmedReceiptAgainstOrder(supplier, warehouse, item, order.getId(), 20);

        entityManager.flush();
        entityManager.clear();

        assertThatCode(() -> purchaseOrderService.close(order.getId())).doesNotThrowAnyException();
    }
}
