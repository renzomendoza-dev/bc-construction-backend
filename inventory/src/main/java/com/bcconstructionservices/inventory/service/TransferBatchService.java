package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.dto.StockTransferRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.dto.TransferLineItemRequest;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.MaterialRequest;
import com.bcconstructionservices.inventory.entity.MaterialRequestLineItem;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.entity.TransferBatch;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.entity.TransferLineItem;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.InactiveResourceException;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.TransferBatchMapper;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestLineItemRepository;
import com.bcconstructionservices.inventory.repository.MaterialRequestRepository;
import com.bcconstructionservices.inventory.repository.TransferBatchRepository;
import com.bcconstructionservices.inventory.repository.TransferLineItemRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service layer for creating transfer batches (a plan/count of items to move
 * from one warehouse to another) and submitting them (the step that actually
 * moves stock). A "site" is just a Warehouse with type SITE — this service
 * never needs to know that; origin/destination are plain Warehouse rows
 * either way.
 *
 * <p>submit() never touches InventoryStock or StockMovement directly — per
 * InventoryService's class-level invariant, all stock mutations route through
 * {@link InventoryService#transferStock}, once per line item.
 */
@Service
@RequiredArgsConstructor
public class TransferBatchService {

    private final TransferBatchRepository transferBatchRepository;
    private final TransferLineItemRepository transferLineItemRepository;
    private final MaterialRequestRepository materialRequestRepository;
    private final MaterialRequestLineItemRepository materialRequestLineItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final TransferBatchMapper transferBatchMapper;
    private final CurrentUserService currentUserService;

    @Transactional
    public TransferBatchResponse createDraft(TransferBatchCreateRequest request) {
        Warehouse origin = warehouseRepository.findById(request.getOriginWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getOriginWarehouseId()));
        if (!origin.isActive()) {
            throw new InactiveResourceException("Warehouse", origin.getId());
        }

        Warehouse destination = warehouseRepository.findById(request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getDestinationWarehouseId()));
        if (!destination.isActive()) {
            throw new InactiveResourceException("Warehouse", destination.getId());
        }

        if (origin.getId().equals(destination.getId())) {
            throw new InvalidStockOperationException(
                    "Transfer batch origin and destination warehouse cannot be the same (warehouseId: "
                            + origin.getId() + ")");
        }

        // transferBatchMapper.toEntity only covers sourceMaterialRequestId/notes —
        // origin/destination, initiatedBy, status, and lineItems are all ignore=true
        // by design (see TransferBatchMapper's javadoc), so they're assembled here.
        TransferBatch batch = transferBatchMapper.toEntity(request);
        batch.setOriginWarehouse(origin);
        batch.setDestinationWarehouse(destination);
        batch.setInitiatedBy(currentUserService.getCurrentUserId());

        List<TransferLineItem> lineItems = new ArrayList<>();
        for (TransferLineItemRequest lineRequest : request.getLines()) {
            Item item = itemRepository.findById(lineRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lineRequest.getItemId()));

            lineItems.add(TransferLineItem.builder()
                    .transferBatch(batch)
                    .item(item)
                    .expectedQuantity(lineRequest.getExpectedQuantity())
                    .quantity(lineRequest.getQuantity())
                    .notes(lineRequest.getNotes())
                    .build());
        }
        batch.setLineItems(lineItems);

        TransferBatch saved = transferBatchRepository.save(batch);
        return transferBatchMapper.toResponse(saved);
    }

    /**
     * Transitions a batch DRAFT -&gt; SUBMITTED -&gt; COMPLETED in one call (Phase 1
     * has no separate approval step). For each line item, delegates to
     * {@link InventoryService#transferStock} — the sole owner of InventoryStock/
     * StockMovement mutations — rather than reimplementing stock math. The whole
     * method is one transaction, so a failure on any line (e.g. insufficient
     * stock) rolls back every transfer already applied earlier in the loop and
     * the batch is left exactly as it was before submit was called.
     */
    @Transactional
    public TransferBatchResponse submit(Long transferBatchId) {
        TransferBatch batch = transferBatchRepository.findByIdWithWarehouses(transferBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferBatch", transferBatchId));

        List<TransferLineItem> lineItems = transferLineItemRepository.findByTransferBatchId(transferBatchId);
        if (lineItems.isEmpty()) {
            throw new InvalidStockOperationException(
                    "Transfer batch " + transferBatchId + " has no line items to submit");
        }

        batch.setStatus(TransferBatchStatus.SUBMITTED);

        for (TransferLineItem line : lineItems) {
            StockTransferRequest transferRequest = StockTransferRequest.builder()
                    .itemId(line.getItem().getId())
                    .fromWarehouseId(batch.getOriginWarehouse().getId())
                    .toWarehouseId(batch.getDestinationWarehouse().getId())
                    .quantity(line.getQuantity())
                    .build();

            // The only call in this codebase permitted to change InventoryStock.quantity.
            inventoryService.transferStock(transferRequest);
        }

        batch.setStatus(TransferBatchStatus.COMPLETED);
        TransferBatch saved = transferBatchRepository.save(batch);

        if (saved.getSourceMaterialRequestId() != null) {
            updateLinkedMaterialRequestStatus(saved, lineItems);
        }

        return transferBatchMapper.toResponse(saved);
    }

    /**
     * Marks the MaterialRequest this batch fulfills as FULFILLED if every
     * requested line's quantity was fully covered by this batch's transferred
     * quantities, or PARTIALLY_FULFILLED otherwise. Silently no-ops if the
     * referenced request no longer exists.
     */
    private void updateLinkedMaterialRequestStatus(TransferBatch batch, List<TransferLineItem> transferredLines) {
        MaterialRequest materialRequest = materialRequestRepository
                .findByIdWithSite(batch.getSourceMaterialRequestId())
                .orElse(null);
        if (materialRequest == null) {
            return;
        }

        List<MaterialRequestLineItem> requestLines =
                materialRequestLineItemRepository.findByMaterialRequestId(materialRequest.getId());

        Map<Long, Integer> transferredByItemId = transferredLines.stream()
                .collect(Collectors.toMap(
                        line -> line.getItem().getId(),
                        TransferLineItem::getQuantity,
                        Integer::sum));

        boolean fullyFulfilled = requestLines.stream().allMatch(reqLine -> {
            Integer transferred = transferredByItemId.getOrDefault(reqLine.getItem().getId(), 0);
            return transferred >= reqLine.getQuantityRequested();
        });

        materialRequest.setStatus(fullyFulfilled
                ? MaterialRequestStatus.FULFILLED
                : MaterialRequestStatus.PARTIALLY_FULFILLED);
        materialRequestRepository.save(materialRequest);
    }

    @Transactional(readOnly = true)
    public TransferBatchResponse getById(Long transferBatchId) {
        TransferBatch batch = transferBatchRepository.findByIdWithWarehouses(transferBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("TransferBatch", transferBatchId));
        return transferBatchMapper.toResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferBatchResponse> search(Long originWarehouseId, Long destinationWarehouseId,
                                                        TransferBatchStatus status, Pageable pageable) {
        Page<TransferBatch> page =
                transferBatchRepository.search(originWarehouseId, destinationWarehouseId, status, pageable);
        return PageResponse.of(page, transferBatchMapper::toResponse);
    }
}
