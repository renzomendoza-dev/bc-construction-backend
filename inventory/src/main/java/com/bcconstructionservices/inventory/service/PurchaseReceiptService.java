package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.ReceiptProcessingException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.exception.TransferBatchNotAwaitingPurchaseException;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptLineMapper;
import com.bcconstructionservices.inventory.mapper.PurchaseReceiptMapper;
import com.bcconstructionservices.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for recording purchase receipts and, on confirmation,
 * applying them to inventory. DTO conversion is delegated to
 * PurchaseReceiptMapper where its coverage allows (see its javadoc: it only
 * maps receiptNumber/purchaseDate/imageUrl/notes on the way in, everything
 * on the way out). Supplier/warehouse resolution, line construction, and
 * cost computation stay here because none of that is mapper territory —
 * see the inline notes at each point.
 */
@Service
@RequiredArgsConstructor
public class PurchaseReceiptService {

    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final PurchaseReceiptLineRepository purchaseReceiptLineRepository;
    private final ItemSupplierRepository itemSupplierRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final TransferBatchRepository transferBatchRepository;
    private final InventoryService inventoryService;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    // Not called directly below: PurchaseReceiptMapper already composes with this
    // (uses = PurchaseReceiptLineMapper.class) to map the `lines` collection inside
    // purchaseReceiptMapper.toResponse(...), and getPurchaseHistoryForItem builds
    // PurchaseHistoryEntry directly since its shape (receiptId, purchaseDate,
    // supplierName) doesn't overlap with what PurchaseReceiptLineMapper produces
    // (itemId, itemName, lineTotal). Kept as an injected field per the requested
    // constructor signature; remove it if that unused-field warning bothers you.
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Transactional
    public PurchaseReceiptResponse createPurchaseReceipt(PurchaseReceiptCreateRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.getSupplierId()));

        // Not explicitly called out in the spec as a ResourceNotFoundException trigger,
        // but warehouseId is a required FK just like supplierId, so it gets the same check.
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ReceiptProcessingException("Purchase receipt must contain at least one line");
        }

        // A receipt can only be linked to a batch that's actually blocked on
        // insufficient stock — fulfillsTransferBatchId isn't an ignore=true
        // mapper field, so this check runs before toEntity() rather than
        // re-reading it off the built entity.
        if (request.getFulfillsTransferBatchId() != null) {
            TransferBatch fulfillsTransferBatch = transferBatchRepository.findById(request.getFulfillsTransferBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("TransferBatch", request.getFulfillsTransferBatchId()));
            if (fulfillsTransferBatch.getStatus() != TransferBatchStatus.AWAITING_PURCHASE) {
                throw new TransferBatchNotAwaitingPurchaseException(
                        request.getFulfillsTransferBatchId(), fulfillsTransferBatch.getStatus());
            }
        }

        // purchaseReceiptMapper.toEntity only covers receiptNumber/purchaseDate/
        // imageUrl/notes — supplier, warehouse, lines, totalAmount, and confirmed(At)
        // are all ignore=true by design (see PurchaseReceiptMapper's javadoc), so
        // they're still assembled here. fulfillsTransferBatchId IS covered by the
        // mapper (plain 1:1 copy, validated above) since it needs no repository
        // lookup to attach — unlike supplier/warehouse it's stored as a plain id,
        // not a managed association.
        PurchaseReceipt receipt = purchaseReceiptMapper.toEntity(request);
        receipt.setSupplier(supplier);
        receipt.setWarehouse(warehouse);

        List<PurchaseReceiptLine> lines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // PurchaseReceiptLineMapper has no toEntity — an unknown itemId needs a
        // ReceiptProcessingException and lineTotal is computed, neither of which
        // belongs in a mapper — so lines are still built by hand here too.
        // PurchaseReceiptLineRequest/PurchaseReceiptCreateRequest carry no lineTotal
        // or totalAmount fields to check for a caller-provided value, so both are
        // always computed rather than conditionally.
        for (PurchaseReceiptLineRequest lineRequest : request.getLines()) {
            Item item = itemRepository.findById(lineRequest.getItemId())
                    .orElseThrow(() -> new ReceiptProcessingException(
                            "Purchase receipt line references unknown item id: " + lineRequest.getItemId()));

            BigDecimal lineTotal = BigDecimal.valueOf(lineRequest.getQuantity())
                    .multiply(lineRequest.getUnitCost())
                    .setScale(2, RoundingMode.HALF_UP);

            PurchaseReceiptLine line = PurchaseReceiptLine.builder()
                    .purchaseReceipt(receipt)
                    .item(item)
                    .quantity(lineRequest.getQuantity())
                    .unitCost(lineRequest.getUnitCost())
                    .lineTotal(lineTotal)
                    .build();

            lines.add(line);
            totalAmount = totalAmount.add(lineTotal);
        }

        receipt.setLines(lines);
        receipt.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));

        PurchaseReceipt saved = purchaseReceiptRepository.save(receipt);
        return purchaseReceiptMapper.toResponse(saved);
    }

    /**
     * Applies a draft receipt to inventory: one IN-type stock adjustment per line
     * (via InventoryService, the sole owner of InventoryStock mutations), followed
     * by refreshing that item+supplier's ItemSupplier.unitCost. The whole method is
     * one transaction, so a failure on any line (e.g. an inactive item) rolls back
     * every adjustment and cost update already applied earlier in the loop.
     */
    @Transactional
    public PurchaseReceiptResponse confirmPurchaseReceipt(Long receiptId) {
        PurchaseReceipt receipt = purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseReceipt", receiptId));

        if (receipt.isConfirmed()) {
            throw new ReceiptProcessingException(receiptId, "receipt has already been confirmed");
        }
        if (receipt.getLines() == null || receipt.getLines().isEmpty()) {
            throw new ReceiptProcessingException(receiptId, "receipt has no lines to confirm");
        }

        for (PurchaseReceiptLine line : receipt.getLines()) {
            StockAdjustmentRequest adjustment = StockAdjustmentRequest.builder()
                    .itemId(line.getItem().getId())
                    .warehouseId(receipt.getWarehouse().getId())
                    .quantity(line.getQuantity())
                    .type(MovementType.IN)
                    .reason(buildReceiptReason(receipt))
                    .build();

            // The only call in this codebase permitted to change InventoryStock.quantity.
            inventoryService.adjustStock(adjustment);

            ItemSupplier itemSupplier = itemSupplierRepository
                    .findByItemIdAndSupplierId(line.getItem().getId(), receipt.getSupplier().getId())
                    .orElseGet(() -> ItemSupplier.builder()
                            .item(line.getItem())
                            .supplier(receipt.getSupplier())
                            .build());
            itemSupplier.setUnitCost(line.getUnitCost());
            itemSupplierRepository.save(itemSupplier);
        }

        Long confirmedById = currentUserService.getCurrentUserId();
        receipt.setConfirmedBy(confirmedById);
        receipt.setConfirmed(true);
        receipt.setConfirmedAt(Instant.now());
        PurchaseReceipt saved = purchaseReceiptRepository.save(receipt);

        // Read off receipt, not saved — save()'s return value isn't
        // guaranteed to echo the argument (only a stubbed test double does),
        // and receipt already carries this field regardless.
        if (receipt.getFulfillsTransferBatchId() != null) {
            unblockLinkedTransferBatch(receipt.getFulfillsTransferBatchId());
        }

        return purchaseReceiptMapper.toResponse(saved);
    }

    /**
     * Semi-automatic unblock (per the chosen design): confirming a receipt
     * that fulfills a blocked batch flips that batch back to DRAFT so it's
     * ready to resubmit, but actually resubmitting (POST /{id}/submit) stays
     * a manual step for a person to trigger. Only flips a batch that's still
     * AWAITING_PURCHASE — if it's since moved on (e.g. resubmitted and
     * completed some other way), this silently no-ops rather than clobbering
     * that state, same pattern as MaterialRequestService's no-op-if-missing
     * handling of a stale cross-reference.
     */
    private void unblockLinkedTransferBatch(Long transferBatchId) {
        transferBatchRepository.findById(transferBatchId).ifPresent(batch -> {
            if (batch.getStatus() == TransferBatchStatus.AWAITING_PURCHASE) {
                batch.setStatus(TransferBatchStatus.DRAFT);
                transferBatchRepository.save(batch);
            }
        });
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getPurchaseReceiptById(Long receiptId) {
        PurchaseReceipt receipt = purchaseReceiptRepository.findByIdWithSupplierAndWarehouse(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseReceipt", receiptId));
        return purchaseReceiptMapper.toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReceiptResponse> listPurchaseReceipts(Long supplierId, LocalDate fromDate,
                                                                      LocalDate toDate, Long fulfillsTransferBatchId,
                                                                      Pageable pageable) {
        Page<PurchaseReceipt> page =
                purchaseReceiptRepository.search(supplierId, fromDate, toDate, fulfillsTransferBatchId, pageable);
        return PageResponse.of(page, purchaseReceiptMapper::toResponse);
    }

    /**
     * PurchaseHistoryEntry (receiptId, purchaseDate, supplierName, quantity, unitCost)
     * doesn't overlap with what PurchaseReceiptLineMapper.toResponse produces
     * (id, itemId, itemName, quantity, unitCost, lineTotal) — no receiptId,
     * purchaseDate, or supplierName on that side, and itemName/lineTotal aren't
     * wanted here since the response is already scoped to one item. Building it
     * directly is the cleaner of the two options offered.
     */
    @Transactional(readOnly = true)
    public PurchaseHistoryResponse getPurchaseHistoryForItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        List<PurchaseHistoryEntry> entries = purchaseReceiptLineRepository
                .findByItemIdOrderByReceiptPurchaseDateDesc(itemId)
                .stream()
                .map(line -> PurchaseHistoryEntry.builder()
                        .receiptId(line.getPurchaseReceipt().getId())
                        .purchaseDate(line.getPurchaseReceipt().getPurchaseDate())
                        .supplierName(line.getPurchaseReceipt().getSupplier().getName())
                        .quantity(line.getQuantity())
                        .unitCost(line.getUnitCost())
                        .build())
                .toList();

        return PurchaseHistoryResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .purchases(entries)
                .build();
    }

    private String buildReceiptReason(PurchaseReceipt receipt) {
        String suffix = receipt.getReceiptNumber() != null ? " (" + receipt.getReceiptNumber() + ")" : "";
        return "Purchase receipt #" + receipt.getId() + suffix;
    }

    /**
     * Sets (or replaces) the scanned-image URL on an existing purchase receipt.
     * If the receipt already had an image, the previous file is deleted from
     * disk AFTER the new URL is persisted, so replacing an image doesn't leave
     * the old file orphaned.
     *
     * @param receiptId id of the receipt to update
     * @param imageUrl  the new stored image URL (from FileStorageService.storeFile)
     * @return the updated receipt as a PurchaseReceiptResponse
     * @throws ResourceNotFoundException if no receipt with that id exists
     */
    @Transactional
    public PurchaseReceiptResponse updateReceiptImage(Long receiptId, String imageUrl) {
        PurchaseReceipt receipt = purchaseReceiptRepository
                .findByIdWithSupplierAndWarehouse(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase receipt not found: " + receiptId));

        // Capture the previous URL (if any) before overwriting it.
        String previousImageUrl = receipt.getImageUrl();

        receipt.setImageUrl(imageUrl);
        // If the entity is managed within this transaction, the change flushes
        // on commit via dirty checking; an explicit save() is harmless if your
        // service style uses one, e.g.:
        // purchaseReceiptRepository.save(receipt);

        PurchaseReceiptResponse response = purchaseReceiptMapper.toResponse(receipt);

        // ORDERING NOTE: delete the OLD file only after the new URL is set on
        // the entity (i.e. it will be committed). Rationale mirrors the image
        // delete flow elsewhere in this project - the failure modes are
        // asymmetric:
        //
        //   - update-first, old-file-delete fails -> one orphaned old file on
        //     disk. Harmless; deleteFile logs + no-ops on a missing file, and a
        //     later cleanup/retry is safe.
        //
        //   - delete-old-file-first, then the update rolls back -> the row still
        //     points at a file we already destroyed -> broken image link.
        //
        // deleteFile never throws, so a failure cleaning up the old file won't
        // turn a successful replacement into an error response.
        //
        // Only delete if there was a previous image AND it actually changed
        // (guards the odd case of re-uploading to the same URL, though the
        // UUID-based naming in FileStorageService makes identical URLs unlikely).
        if (previousImageUrl != null && !previousImageUrl.isBlank()
                && !previousImageUrl.equals(imageUrl)) {
            fileStorageService.deleteFile(previousImageUrl);
        }

        return response;
    }
}
