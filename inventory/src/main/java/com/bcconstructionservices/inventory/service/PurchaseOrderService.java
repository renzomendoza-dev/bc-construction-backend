package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionItem;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionSource;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionsResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.InventoryStock;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.PurchaseOrder;
import com.bcconstructionservices.inventory.entity.PurchaseOrderLine;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.entity.PurchaseReceiptLine;
import com.bcconstructionservices.inventory.entity.Supplier;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.exception.PurchaseOrderHasReceiptsException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotDeletableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotEditableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotOpenException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.PurchaseOrderMapper;
import com.bcconstructionservices.inventory.repository.InventoryStockRepository;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.ItemSupplierRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestLineItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.PurchaseOrderLineRepository;
import com.bcconstructionservices.inventory.repository.PurchaseOrderRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptLineRepository;
import com.bcconstructionservices.inventory.repository.PurchaseReceiptRepository;
import com.bcconstructionservices.inventory.repository.SupplierRepository;
import com.bcconstructionservices.inventory.repository.TransferBatchRepository;
import com.bcconstructionservices.inventory.repository.TransferLineItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer for purchase orders — placed with a supplier before anything
 * has physically arrived, an earlier stage than PurchaseReceipt. There is
 * deliberately no auto-chaining here: a PARTIALLY_RECEIVED order does not
 * automatically generate a new shortfall/TransferBatch for what's still
 * missing (see getSuggestions's javadoc for the intended manual follow-up
 * flow) — that's a deliberate scope boundary, not an oversight.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final ItemSupplierRepository itemSupplierRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final TransferBatchRepository transferBatchRepository;
    private final TransferLineItemRepository transferLineItemRepository;
    private final MaterialRequestRepository materialRequestRepository;
    private final MaterialRequestLineItemRepository materialRequestLineItemRepository;
    private final PurchaseReceiptLineRepository purchaseReceiptLineRepository;
    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    // Not called directly below: cascade=ALL + orphanRemoval on PurchaseOrder.lines
    // means saving the parent is enough. Kept as an injected field per this
    // codebase's convention of keeping the matching *LineRepository around
    // even when a service ends up not calling it directly (see
    // PurchaseReceiptService's own purchaseReceiptLineMapper field).
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;

    @Transactional
    public PurchaseOrderResponse createDraft(PurchaseOrderCreateRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.getSupplierId()));

        // purchaseOrderMapper.toEntity only covers notes — supplier, status,
        // initiatedBy, and lines are all ignore=true by design (see the
        // mapper's javadoc), so they're assembled here.
        PurchaseOrder order = purchaseOrderMapper.toEntity(request);
        order.setSupplier(supplier);
        order.setLines(buildLines(order, request.getLines()));

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return toResponseWithReceivedQuantities(saved);
    }

    /**
     * Full-replacement update, only while DRAFT (422 otherwise) — matches
     * MaterialRequestService.update's shape exactly: notes copied as given
     * (including null, clearing it), lines entirely cleared and rebuilt via
     * orphanRemoval.
     */
    @Transactional
    public PurchaseOrderResponse update(Long purchaseOrderId, PurchaseOrderUpdateRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithSupplier(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));

        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderNotEditableException(purchaseOrderId, order.getStatus());
        }

        purchaseOrderMapper.updateEntityFromRequest(request, order);

        order.getLines().clear();
        order.getLines().addAll(buildLines(order, request.getLines()));

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return toResponseWithReceivedQuantities(saved);
    }

    /**
     * DRAFT -&gt; SUBMITTED. Locks line items from this point on — once a
     * supplier has the order, changing quantities without telling them is
     * misleading, so update() rejects anything past DRAFT too.
     */
    @Transactional
    public PurchaseOrderResponse submit(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithSupplier(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));

        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderNotEditableException(purchaseOrderId, order.getStatus());
        }

        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return toResponseWithReceivedQuantities(saved);
    }

    /**
     * Manually terminates an order regardless of how much of it has been
     * received — for when the remaining shortfall isn't coming (supplier
     * discontinued an item, order was over-cautious, etc.). Allowed from any
     * status except RECEIVED/CLOSED (422), which are already terminal.
     * Deliberately a distinct terminal state from RECEIVED so "fully
     * delivered" and "abandoned short" aren't conflated.
     */
    @Transactional
    public PurchaseOrderResponse close(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithSupplier(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));

        if (order.getStatus() == PurchaseOrderStatus.RECEIVED || order.getStatus() == PurchaseOrderStatus.CLOSED) {
            throw new PurchaseOrderNotOpenException(purchaseOrderId, order.getStatus());
        }

        order.setStatus(PurchaseOrderStatus.CLOSED);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return toResponseWithReceivedQuantities(saved);
    }

    /**
     * Only a DRAFT order can be deleted (422 otherwise) — anything past that
     * has been submitted to the supplier. Independently, also rejected (409)
     * if any PurchaseReceipt already references this order via
     * purchaseOrderId: createPurchaseReceipt() allows linking to a DRAFT
     * order (see its own inline comment — only RECEIVED/CLOSED are
     * rejected), and purchase_receipt.purchase_order_id is a real DB-level
     * FK, so this check exists to turn what would otherwise be a raw
     * constraint-violation 500 into a clean, documented response. Line items
     * cascade-delete via PurchaseOrder.lines' orphanRemoval.
     */
    @Transactional
    public void delete(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));

        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new PurchaseOrderNotDeletableException(purchaseOrderId, order.getStatus());
        }

        if (purchaseReceiptRepository.existsByPurchaseOrderId(purchaseOrderId)) {
            throw new PurchaseOrderHasReceiptsException(purchaseOrderId);
        }

        purchaseOrderRepository.delete(order);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithSupplier(purchaseOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));
        return toResponseWithReceivedQuantities(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(Long supplierId, PurchaseOrderStatus status, Pageable pageable) {
        Page<PurchaseOrder> page = purchaseOrderRepository.search(supplierId, status, pageable);
        return PageResponse.of(page, this::toResponseWithReceivedQuantities);
    }

    /**
     * Recomputes and persists this order's status from the sum of every
     * CONFIRMED PurchaseReceipt's lines against it — called by
     * PurchaseReceiptService after confirming a receipt whose
     * purchaseOrderId points here. Cumulative across every confirmed receipt
     * ever created against this order (not just the one just confirmed), so
     * multiple partial deliveries over time are reflected correctly. Only
     * touches SUBMITTED/PARTIALLY_RECEIVED orders — a manually CLOSED order
     * stays CLOSED even if a receipt is later confirmed against it (that
     * combination shouldn't happen given createPurchaseReceipt's own guard,
     * but this is defensive rather than assuming it never will).
     */
    @Transactional
    public void updateStatusFromReceipts(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId).orElse(null);
        if (order == null || order.getStatus() == PurchaseOrderStatus.CLOSED
                || order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            return;
        }

        List<PurchaseOrderLine> lines = order.getLines();
        Map<Long, Integer> receivedByItemId = receivedQuantityByItemId(purchaseOrderId);

        boolean fullyReceived = lines.stream().allMatch(line ->
                receivedByItemId.getOrDefault(line.getItem().getId(), 0) >= line.getQuantity());
        boolean anyReceived = receivedByItemId.values().stream().anyMatch(qty -> qty > 0);

        if (fullyReceived) {
            order.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (anyReceived) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }
        purchaseOrderRepository.save(order);
    }

    private List<PurchaseOrderLine> buildLines(PurchaseOrder order, List<PurchaseOrderLineRequest> lineRequests) {
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (PurchaseOrderLineRequest lineRequest : lineRequests) {
            Item item = itemRepository.findById(lineRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lineRequest.getItemId()));

            lines.add(PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .item(item)
                    .quantity(lineRequest.getQuantity())
                    .notes(lineRequest.getNotes())
                    .build());
        }
        return lines;
    }

    private Map<Long, Integer> receivedQuantityByItemId(Long purchaseOrderId) {
        return purchaseReceiptLineRepository.findConfirmedByPurchaseOrderId(purchaseOrderId).stream()
                .collect(Collectors.toMap(
                        line -> line.getItem().getId(),
                        PurchaseReceiptLine::getQuantity,
                        Integer::sum));
    }

    /**
     * Maps the order, then overlays each line's receivedQuantity — computed
     * from confirmed PurchaseReceipts against this order, not mapper
     * territory (needs a repository query, see PurchaseOrderLineMapper's
     * javadoc).
     */
    private PurchaseOrderResponse toResponseWithReceivedQuantities(PurchaseOrder order) {
        PurchaseOrderResponse response = purchaseOrderMapper.toResponse(order);
        Map<Long, Integer> receivedByItemId = receivedQuantityByItemId(order.getId());

        for (PurchaseOrderLineResponse lineResponse : response.getLines()) {
            lineResponse.setReceivedQuantity(receivedByItemId.getOrDefault(lineResponse.getItemId(), 0));
        }

        return response;
    }

    // ---------------------------------------------------------------
    // Suggestions
    // ---------------------------------------------------------------

    /**
     * Suggests line items for a new purchase order against the given
     * supplier, from three independent sources:
     * <ol>
     *   <li>Shortfall items on TransferBatch lines currently AWAITING_PURCHASE
     *       — re-checked against CURRENT stock at each batch's origin
     *       warehouse (not the stale moment the batch failed), since stock
     *       may have arrived from elsewhere since then.</li>
     *   <li>Items at/below their reorder threshold (same query as
     *       GET /api/inventory/low-stock).</li>
     *   <li>Items on open (SUBMITTED/PARTIALLY_FULFILLED) MaterialRequest
     *       lines not yet fully dispatched — "dispatched" is the sum of
     *       every COMPLETED TransferBatch's lines sourced from that request.</li>
     * </ol>
     *
     * <p>Suggestions are NOT filtered to items linked to the supplier via
     * ItemSupplier — deliberately: erring toward suggesting too much,
     * rather than silently hiding a genuine shortfall because that link
     * happens to be unpopulated. Each suggestion instead carries
     * linkedToSupplier so the frontend can choose to de-emphasize (not hide)
     * unlinked items — suggestions are a starting point the user edits
     * before submitting, never a hard constraint.
     *
     * <p>The three sources are summed independently, not deduplicated — an
     * item can legitimately appear from more than one source (e.g. a
     * TransferBatch blocked while sourced from a MaterialRequest that's
     * therefore still open too), which can occasionally over-suggest in that
     * specific overlap. Accepted deliberately, for the same "err toward too
     * much" reasoning above, rather than the added complexity of
     * cross-referencing which source caused which for exact deduplication.
     *
     * <p>This is also the intended manual follow-up for a PARTIALLY_RECEIVED
     * order's remaining shortfall (no auto-chaining is built): re-running
     * this endpoint after the fact surfaces whatever's still short via
     * whichever of the three sources still applies.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderSuggestionsResponse getSuggestions(Long supplierId) {
        if (!supplierRepository.existsById(supplierId)) {
            throw new ResourceNotFoundException("Supplier", supplierId);
        }

        Map<Long, Integer> quantityByItemId = new LinkedHashMap<>();
        Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId = new LinkedHashMap<>();

        addAwaitingPurchaseShortfalls(quantityByItemId, sourcesByItemId);
        addLowStockItems(quantityByItemId, sourcesByItemId);
        addOpenMaterialRequestItems(quantityByItemId, sourcesByItemId);

        List<PurchaseOrderSuggestionItem> suggestions = quantityByItemId.entrySet().stream()
                .map(entry -> buildSuggestionItem(entry.getKey(), entry.getValue(), sourcesByItemId, supplierId))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PurchaseOrderSuggestionItem::getSuggestedQuantity).reversed())
                .toList();

        return PurchaseOrderSuggestionsResponse.builder()
                .supplierId(supplierId)
                .suggestions(suggestions)
                .build();
    }

    private PurchaseOrderSuggestionItem buildSuggestionItem(
            Long itemId, Integer quantity, Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId,
            Long supplierId) {
        // Skip rather than 404 the whole endpoint if an item referenced by
        // stale source data (e.g. a since-deleted item) no longer exists —
        // one missing item shouldn't break every other supplier's suggestions.
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return null;
        }

        boolean linkedToSupplier = itemSupplierRepository.findByItemIdAndSupplierId(itemId, supplierId).isPresent();

        return PurchaseOrderSuggestionItem.builder()
                .itemId(itemId)
                .itemName(item.getName())
                .itemSku(item.getSku())
                .suggestedQuantity(quantity)
                .linkedToSupplier(linkedToSupplier)
                .sources(sourcesByItemId.get(itemId).stream().toList())
                .build();
    }

    private void addAwaitingPurchaseShortfalls(Map<Long, Integer> quantityByItemId,
                                                Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId) {
        List<TransferBatch> blockedBatches =
                transferBatchRepository.findByStatus(TransferBatchStatus.AWAITING_PURCHASE);

        for (TransferBatch batch : blockedBatches) {
            Long originWarehouseId = batch.getOriginWarehouse().getId();
            for (TransferLineItem line : transferLineItemRepository.findByTransferBatchId(batch.getId())) {
                Long itemId = line.getItem().getId();
                int available = inventoryStockRepository
                        .findByItemAndWarehouseAndLocation(itemId, originWarehouseId, null)
                        .map(InventoryStock::getQuantity)
                        .orElse(0);
                int shortfall = line.getQuantity() - available;
                if (shortfall > 0) {
                    addSuggestion(quantityByItemId, sourcesByItemId, itemId, shortfall,
                            PurchaseOrderSuggestionSource.AWAITING_PURCHASE_TRANSFER);
                }
            }
        }
    }

    private void addLowStockItems(Map<Long, Integer> quantityByItemId,
                                   Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId) {
        for (InventoryStock stock : inventoryStockRepository.findLowStock()) {
            int deficit = Math.max(stock.getReorderThreshold() - stock.getQuantity(), 1);
            addSuggestion(quantityByItemId, sourcesByItemId, stock.getItem().getId(), deficit,
                    PurchaseOrderSuggestionSource.LOW_STOCK);
        }
    }

    private void addOpenMaterialRequestItems(Map<Long, Integer> quantityByItemId,
                                              Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId) {
        List<MaterialRequest> openRequests = materialRequestRepository.findByStatusIn(
                List.of(MaterialRequestStatus.SUBMITTED, MaterialRequestStatus.PARTIALLY_FULFILLED));

        for (MaterialRequest request : openRequests) {
            Map<Long, Integer> dispatchedByItemId = transferBatchRepository
                    .findBySourceMaterialRequestIdAndStatus(request.getId(), TransferBatchStatus.COMPLETED)
                    .stream()
                    .flatMap(batch -> transferLineItemRepository.findByTransferBatchId(batch.getId()).stream())
                    .collect(Collectors.toMap(
                            line -> line.getItem().getId(),
                            TransferLineItem::getQuantity,
                            Integer::sum));

            for (MaterialRequestLineItem line
                    : materialRequestLineItemRepository.findByMaterialRequestId(request.getId())) {
                Long itemId = line.getItem().getId();
                int dispatched = dispatchedByItemId.getOrDefault(itemId, 0);
                int notYetDispatched = line.getQuantityRequested() - dispatched;
                if (notYetDispatched > 0) {
                    addSuggestion(quantityByItemId, sourcesByItemId, itemId, notYetDispatched,
                            PurchaseOrderSuggestionSource.OPEN_MATERIAL_REQUEST);
                }
            }
        }
    }

    private void addSuggestion(Map<Long, Integer> quantityByItemId,
                                Map<Long, Set<PurchaseOrderSuggestionSource>> sourcesByItemId,
                                Long itemId, int quantity, PurchaseOrderSuggestionSource source) {
        quantityByItemId.merge(itemId, quantity, Integer::sum);
        sourcesByItemId.computeIfAbsent(itemId, key -> EnumSet.noneOf(PurchaseOrderSuggestionSource.class))
                .add(source);
    }
}
