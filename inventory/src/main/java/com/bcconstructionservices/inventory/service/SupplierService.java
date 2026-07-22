package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.Item;
import com.bcconstructionservices.inventory.entity.ItemSupplier;
import com.bcconstructionservices.inventory.entity.Supplier;
import com.bcconstructionservices.inventory.exception.InactiveResourceException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.ItemSupplierMapper;
import com.bcconstructionservices.inventory.mapper.SupplierMapper;
import com.bcconstructionservices.inventory.repository.ItemRepository;
import com.bcconstructionservices.inventory.repository.ItemSupplierRepository;
import com.bcconstructionservices.inventory.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Supplier management and the Item-Supplier linking
 * relationship. All entity&lt;-&gt;DTO conversion is delegated to
 * SupplierMapper/ItemSupplierMapper — this class holds only business logic
 * (existence/active validation, upsert decision) that a mapper can't own.
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ItemSupplierRepository itemSupplierRepository;
    private final ItemRepository itemRepository;
    private final SupplierMapper supplierMapper;
    private final ItemSupplierMapper itemSupplierMapper;

    @Transactional
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        Supplier supplier = supplierMapper.toEntity(request);
        Supplier saved = supplierRepository.save(supplier);
        return supplierMapper.toResponse(saved);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long supplierId, SupplierUpdateRequest request) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));

        supplierMapper.updateEntityFromRequest(request, supplier);

        Supplier saved = supplierRepository.save(supplier);
        return supplierMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        return supplierMapper.toResponse(supplier);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> listSuppliers(Boolean active, Pageable pageable) {
        Page<Supplier> page = supplierRepository.search(active, pageable);
        return PageResponse.of(page, supplierMapper::toResponse);
    }

    @Transactional
    public void deactivateSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    /**
     * Creates the item-supplier link if it doesn't yet exist for this
     * item+supplier pair, otherwise updates the existing row's supplierSku
     * and unitCost in place rather than creating a duplicate. Either way,
     * the already-loaded item/supplier are (re)attached before saving: for a
     * new row that's what itemSupplierMapper.toEntity() left unmapped, and
     * for an existing row it avoids itemSupplierMapper.toResponse() forcing
     * a lazy-load of item/supplier afterward.
     */
    @Transactional
    public ItemSupplierResponse linkItemToSupplier(ItemSupplierRequest request) {
        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.getSupplierId()));

        if (!item.isActive()) {
            throw new InactiveResourceException("Item", item.getId());
        }
        if (!supplier.isActive()) {
            throw new InactiveResourceException("Supplier", supplier.getId());
        }

        ItemSupplier itemSupplier = itemSupplierRepository
                .findByItemIdAndSupplierId(item.getId(), supplier.getId())
                .map(existing -> {
                    existing.setSupplierSku(request.getSupplierSku());
                    existing.setUnitCost(request.getUnitCost());
                    return existing;
                })
                .orElseGet(() -> itemSupplierMapper.toEntity(request));

        itemSupplier.setItem(item);
        itemSupplier.setSupplier(supplier);

        ItemSupplier saved = itemSupplierRepository.save(itemSupplier);
        return itemSupplierMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ItemSupplierResponse> getSuppliersForItem(Long itemId) {
        if (!itemRepository.existsById(itemId)) {
            throw new ResourceNotFoundException("Item", itemId);
        }

        return itemSupplierRepository.findByItemIdWithSupplier(itemId).stream()
                .map(itemSupplierMapper::toResponse)
                .toList();
    }
}
