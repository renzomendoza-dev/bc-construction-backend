package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.dto.StorageLocationRequest;
import com.bcconstructionservices.inventory.dto.WarehouseCreateRequest;
import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.exception.DuplicateResourceException;
import com.bcconstructionservices.inventory.mapper.StorageLocationMapper;
import com.bcconstructionservices.inventory.mapper.WarehouseMapper;
import com.bcconstructionservices.inventory.repository.StorageLocationRepository;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StorageLocationRepository storageLocationRepository;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private StorageLocationMapper storageLocationMapper;

    @InjectMocks
    private WarehouseService warehouseService;

    /** What Spring throws when an insert violates the named DB constraint. */
    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("constraint violated",
                new ConstraintViolationException("constraint violated",
                        new SQLException("duplicate key"), constraintName));
    }

    /** Two concurrent creates can both pass the pre-check; the DB constraint rejects the second. */
    @Nested
    class ConcurrentDuplicateCode {

        @Test
        void warehouseCreateThatHitsTheCodeConstraintThrowsDuplicateResourceException() {
            WarehouseCreateRequest request = WarehouseCreateRequest.builder().code("WH-1").build();
            when(warehouseRepository.existsByCode("WH-1")).thenReturn(false);
            when(warehouseMapper.toEntity(request)).thenReturn(new Warehouse());
            when(warehouseRepository.save(any(Warehouse.class)))
                    .thenThrow(violationOf(WarehouseService.CODE_CONSTRAINT));

            assertThatExceptionOfType(DuplicateResourceException.class)
                    .isThrownBy(() -> warehouseService.createWarehouse(request));
        }

        @Test
        void storageLocationCreateThatHitsTheCodeConstraintThrowsDuplicateResourceException() {
            Warehouse warehouse = new Warehouse();
            warehouse.setId(3L);
            StorageLocationRequest request = StorageLocationRequest.builder().warehouseId(3L).code("A-1").build();
            when(warehouseRepository.findById(3L)).thenReturn(Optional.of(warehouse));
            when(storageLocationRepository.existsByWarehouseIdAndCode(3L, "A-1")).thenReturn(false);
            when(storageLocationMapper.toEntity(request)).thenReturn(new StorageLocation());
            when(storageLocationRepository.save(any(StorageLocation.class)))
                    .thenThrow(violationOf(WarehouseService.LOCATION_CODE_CONSTRAINT));

            assertThatExceptionOfType(DuplicateResourceException.class)
                    .isThrownBy(() -> warehouseService.addStorageLocation(request));
        }
    }
}
