package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.*;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.DuplicateResourceException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.mapper.StorageLocationMapper;
import com.bcconstructionservices.inventory.mapper.WarehouseMapper;
import com.bcconstructionservices.inventory.repository.StorageLocationRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Warehouse management and the StorageLocations that live
 * within a warehouse. All entity&lt;-&gt;DTO conversion is delegated to
 * WarehouseMapper/StorageLocationMapper — this class holds only business
 * logic (uniqueness checks, existence validation) that a mapper can't own.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseCreateRequest request) {
        if (warehouseRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Warehouse", "code", request.getCode());
        }

        Warehouse warehouse = warehouseMapper.toEntity(request);
        Warehouse saved = warehouseRepository.save(warehouse);
        return warehouseMapper.toResponse(saved);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(Long warehouseId, WarehouseUpdateRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));

        // No code-uniqueness re-check needed here: WarehouseUpdateRequest has no
        // code field, and warehouseMapper.updateEntityFromRequest ignores it too.
        warehouseMapper.updateEntityFromRequest(request, warehouse);

        Warehouse saved = warehouseRepository.save(warehouse);
        return warehouseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> listWarehouses(Boolean active, Pageable pageable) {
        Page<Warehouse> page = warehouseRepository.search(active, pageable);
        return PageResponse.of(page, warehouseMapper::toResponse);
    }

    @Transactional
    public void deactivateWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));
        warehouse.setActive(false);
        warehouseRepository.save(warehouse);
    }

    @Transactional
    public StorageLocationResponse addStorageLocation(StorageLocationRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.getWarehouseId()));

        if (storageLocationRepository.existsByWarehouseIdAndCode(warehouse.getId(), request.getCode())) {
            throw new DuplicateResourceException(
                    "StorageLocation with code '" + request.getCode()
                            + "' already exists in warehouse " + warehouse.getId());
        }

        // storageLocationMapper.toEntity leaves `warehouse` unmapped (it only has
        // warehouseId, not the entity), so it's attached here after validation.
        StorageLocation location = storageLocationMapper.toEntity(request);
        location.setWarehouse(warehouse);

        StorageLocation saved = storageLocationRepository.save(location);
        return storageLocationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StorageLocationResponse> listStorageLocations(Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse", warehouseId);
        }

        return storageLocationRepository.findByWarehouseId(warehouseId).stream()
                .map(storageLocationMapper::toResponse)
                .toList();
    }

    /**
     * Unlike listItems/listWarehouses/listSuppliers (where a null active means
     * "no filter, show everything"), a null here defaults to showing only
     * active locations — storage locations are primarily consumed by "pick a
     * location" pickers, where an inactive one showing up by default would be
     * a UX bug, not a feature. Pass active=false explicitly to see only
     * inactive locations. There is no single call that returns both together.
     */
    @Transactional(readOnly = true)
    public List<StorageLocationResponse> listStorageLocations(Long warehouseId, Boolean active) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse", warehouseId);
        }

        boolean effectiveActive = active == null || active;

        return storageLocationRepository.findByWarehouseIdAndActive(warehouseId, effectiveActive).stream()
                .map(storageLocationMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateStorageLocation(Long locationId) {
        StorageLocation location = storageLocationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("StorageLocation", locationId));
        // Deliberately does not touch InventoryStock/StockMovement — a
        // deactivated location can still have historical rows referencing it;
        // this only affects whether it's offered going forward.
        location.setActive(false);
        storageLocationRepository.save(location);
    }
}
