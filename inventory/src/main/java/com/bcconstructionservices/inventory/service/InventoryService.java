package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.*;
import com.bcconstructionservices.inventory.exception.InactiveResourceException;
import com.bcconstructionservices.inventory.exception.InsufficientStockException;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.InventoryStockMapper;
import com.bcconstructionservices.inventory.mapper.StockMovementMapper;
import com.bcconstructionservices.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Service layer owning all InventoryStock quantity changes and the
 * StockMovement audit trail that accompanies them. StockLevelResponse and
 * StockMovementResponse conversion is delegated to InventoryStockMapper/
 * StockMovementMapper; LowStockItemResponse has no generated mapper, so
 * {@link #toLowStockResponse} stays as a private method — see the note there.
 *
 * <p>Design note on the "single mutation point" business rule: InventoryStock.quantity
 * is only ever changed inside {@link #mutateQuantity}, a private helper used by both
 * {@link #adjustStock} and {@link #transferStock}. Other services must call one of
 * this class's public methods rather than touching InventoryStock or StockMovement
 * repositories directly — this class is the single owner of that invariant, not one
 * specific method.
 *
 * <p>Why transferStock doesn't literally call adjustStock(): StockMovement has a single
 * `warehouse` foreign key, so one row can only describe a move within one warehouse
 * (fromLocation + toLocation both set). A cross-warehouse transfer can't be expressed
 * as one row and instead produces two TRANSFER-type rows, one per warehouse. Routing
 * that through adjustStock's single-location, single-type DTO shape isn't possible
 * without changing its contract, so transferStock shares the underlying mutation
 * helper instead.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryStockRepository inventoryStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final InventoryStockMapper inventoryStockMapper;
    private final StockMovementMapper stockMovementMapper;

    @Transactional(readOnly = true)
    public StockLevelResponse getStockLevel(Long itemId, Long warehouseId, Long locationId) {
        InventoryStock stock = inventoryStockRepository
                .findByItemAndWarehouseAndLocation(itemId, warehouseId, locationId)
                .orElseThrow(() -> new ResourceNotFoundException(noStockMessage(itemId, warehouseId, locationId)));
        return inventoryStockMapper.toStockLevelResponse(stock);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockLevelResponse> listStock(Long itemId, Long warehouseId, Pageable pageable) {
        Page<InventoryStock> page = inventoryStockRepository.search(itemId, warehouseId, pageable);
        return PageResponse.of(page, inventoryStockMapper::toStockLevelResponse);
    }

    @Transactional(readOnly = true)
    public List<LowStockItemResponse> getLowStockItems() {
        return inventoryStockRepository.findLowStock().stream()
                .map(this::toLowStockResponse)
                .toList();
    }

    @Transactional
    public StockMovementResponse adjustStock(StockAdjustmentRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidStockOperationException(
                    "Adjustment quantity must be greater than zero, received: " + request.getQuantity());
        }

        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        if (!item.isActive()) {
            throw new InactiveResourceException("Item", item.getId());
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));
        if (!warehouse.isActive()) {
            throw new InactiveResourceException("Warehouse", warehouse.getId());
        }

        StorageLocation location = resolveLocation(request.getLocationId(), warehouse);

        MovementType type = request.getType();
        boolean increase = (type == MovementType.IN || type == MovementType.ADJUSTMENT);
        boolean allowAutoCreate = (type == MovementType.IN);

        mutateQuantity(item, warehouse, location, increase, allowAutoCreate, request.getQuantity());

        boolean outgoing = (type == MovementType.OUT || type == MovementType.TRANSFER);
        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .fromLocation(outgoing ? location : null)
                .toLocation(increase ? location : null)
                .type(type)
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .build();

        StockMovement saved = stockMovementRepository.save(movement);
        return stockMovementMapper.toResponse(saved);
    }

    /**
     * Moves stock from one item+warehouse+location to another. Writes a single
     * TRANSFER-type StockMovement (fromLocation + toLocation on one row) when the
     * source and destination share a warehouse; writes two TRANSFER-type rows,
     * one per warehouse, when the transfer crosses warehouses (a single row can't
     * reference two different warehouses).
     */
    @Transactional
    public List<StockMovementResponse> transferStock(StockTransferRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidStockOperationException(
                    "Transfer quantity must be greater than zero, received: " + request.getQuantity());
        }

        boolean sameWarehouse = Objects.equals(request.getFromWarehouseId(), request.getToWarehouseId());
        boolean sameLocation = Objects.equals(request.getFromLocationId(), request.getToLocationId());
        if (sameWarehouse && sameLocation) {
            throw new InvalidStockOperationException(
                    "Transfer source and destination must be different (warehouseId: "
                            + request.getFromWarehouseId()
                            + (request.getFromLocationId() != null ? ", locationId: " + request.getFromLocationId() : "")
                            + ")");
        }

        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        if (!item.isActive()) {
            throw new InactiveResourceException("Item", item.getId());
        }

        Warehouse fromWarehouse = warehouseRepository.findById(request.getFromWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getFromWarehouseId()));
        if (!fromWarehouse.isActive()) {
            throw new InactiveResourceException("Warehouse", fromWarehouse.getId());
        }

        Warehouse toWarehouse = sameWarehouse
                ? fromWarehouse
                : warehouseRepository.findById(request.getToWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getToWarehouseId()));
        if (!toWarehouse.isActive()) {
            throw new InactiveResourceException("Warehouse", toWarehouse.getId());
        }

        StorageLocation fromLocation = resolveLocation(request.getFromLocationId(), fromWarehouse);
        StorageLocation toLocation = resolveLocation(request.getToLocationId(), toWarehouse);

        // Decrement source: must already have a tracked, sufficient balance.
        mutateQuantity(item, fromWarehouse, fromLocation, false, false, request.getQuantity());
        // Increment destination: first stock at a new location starts from zero.
        mutateQuantity(item, toWarehouse, toLocation, true, true, request.getQuantity());

        if (sameWarehouse) {
            StockMovement movement = StockMovement.builder()
                    .item(item)
                    .warehouse(fromWarehouse)
                    .fromLocation(fromLocation)
                    .toLocation(toLocation)
                    .type(MovementType.TRANSFER)
                    .quantity(request.getQuantity())
                    .reason("Transfer within warehouse " + fromWarehouse.getId())
                    .build();
            StockMovement saved = stockMovementRepository.save(movement);
            return List.of(stockMovementMapper.toResponse(saved));
        }

        StockMovement outMovement = StockMovement.builder()
                .item(item)
                .warehouse(fromWarehouse)
                .fromLocation(fromLocation)
                .toLocation(null)
                .type(MovementType.TRANSFER)
                .quantity(request.getQuantity())
                .reason("Transfer to warehouse " + toWarehouse.getId())
                .build();
        StockMovement inMovement = StockMovement.builder()
                .item(item)
                .warehouse(toWarehouse)
                .fromLocation(null)
                .toLocation(toLocation)
                .type(MovementType.TRANSFER)
                .quantity(request.getQuantity())
                .reason("Transfer from warehouse " + fromWarehouse.getId())
                .build();

        StockMovement savedOut = stockMovementRepository.save(outMovement);
        StockMovement savedIn = stockMovementRepository.save(inMovement);

        return List.of(stockMovementMapper.toResponse(savedOut), stockMovementMapper.toResponse(savedIn));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovementHistory(Long itemId, Long warehouseId,
                                                                  LocalDate fromDate, LocalDate toDate,
                                                                  Pageable pageable) {
        Instant from = fromDate != null ? fromDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        // Upper bound is exclusive, so add a day to make toDate inclusive of its full 24 hours.
        Instant to = toDate != null ? toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Page<StockMovement> page = stockMovementRepository.search(itemId, warehouseId, from, to, pageable);
        return PageResponse.of(page, stockMovementMapper::toResponse);
    }

    // --- Shared mutation logic ---------------------------------------------------

    /**
     * The single place InventoryStock.quantity is read-modified-written. Both
     * adjustStock and transferStock funnel their actual balance changes through here.
     *
     * @param increase                 whether this call adds to the balance (true) or subtracts (false)
     * @param allowAutoCreateIfMissing whether a missing row should be created at quantity 0
     *                                 before applying the change, or treated as not-found
     */
    private void mutateQuantity(Item item, Warehouse warehouse, StorageLocation location,
                                boolean increase, boolean allowAutoCreateIfMissing, Integer quantity) {
        Long locationId = location != null ? location.getId() : null;
        InventoryStock stock = inventoryStockRepository
                .findByItemAndWarehouseAndLocation(item.getId(), warehouse.getId(), locationId)
                .orElse(null);

        if (stock == null) {
            if (!allowAutoCreateIfMissing) {
                throw new ResourceNotFoundException(noStockMessage(item.getId(), warehouse.getId(), locationId));
            }
            stock = InventoryStock.builder()
                    .item(item)
                    .warehouse(warehouse)
                    .location(location)
                    .quantity(0)
                    .build();
        }

        if (increase) {
            stock.setQuantity(stock.getQuantity() + quantity);
        } else {
            int available = stock.getQuantity();
            if (available - quantity < 0) {
                throw new InsufficientStockException(item.getId(), warehouse.getId(), quantity, available);
            }
            stock.setQuantity(available - quantity);
        }

        inventoryStockRepository.save(stock);
    }

    private StorageLocation resolveLocation(Long locationId, Warehouse warehouse) {
        if (locationId == null) {
            return null;
        }
        StorageLocation location = storageLocationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("StorageLocation", locationId));
        if (!location.getWarehouse().getId().equals(warehouse.getId())) {
            throw new InvalidStockOperationException(
                    "StorageLocation " + locationId + " does not belong to warehouse " + warehouse.getId());
        }
        return location;
    }

    private String noStockMessage(Long itemId, Long warehouseId, Long locationId) {
        return "No inventory stock record found for item " + itemId + " at warehouse " + warehouseId
                + (locationId != null ? " location " + locationId : "");
    }

    // --- Entity <-> DTO mapping -------------------------------------------------

    // No InventoryStockMapper.toLowStockResponse was generated (LowStockItemResponse
    // wasn't covered when that mapper was built), so this is the one deliberate
    // exception to "always delegate to the injected mapper" in this class. Add a
    // toLowStockResponse(InventoryStock) method to InventoryStockMapper and swap
    // this out if full delegation is wanted here too.
    private LowStockItemResponse toLowStockResponse(InventoryStock stock) {
        return LowStockItemResponse.builder()
                .itemId(stock.getItem().getId())
                .itemName(stock.getItem().getName())
                .sku(stock.getItem().getSku())
                .warehouseId(stock.getWarehouse().getId())
                .warehouseName(stock.getWarehouse().getName())
                .quantity(stock.getQuantity())
                .reorderThreshold(stock.getReorderThreshold())
                .build();
    }
}
