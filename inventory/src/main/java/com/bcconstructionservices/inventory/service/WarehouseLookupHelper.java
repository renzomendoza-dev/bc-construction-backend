package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Lets other modules (e.g. equipment) resolve a display name for a
 * warehouse id they hold as a plain Long — mirrors
 * {@code com.bcconstructionservices.user.service.UserLookupHelper}'s role
 * for cross-module user-id lookups inside a MapStruct mapper.
 */
@Component
@RequiredArgsConstructor
public class WarehouseLookupHelper {

    private final WarehouseRepository warehouseRepository;

    @Named("resolveWarehouseName")
    public String resolveWarehouseName(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        return warehouseRepository.findById(warehouseId)
                .map(Warehouse::getName)
                .orElse(null);
    }
}
