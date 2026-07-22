package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Warehouse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest slice tests for WarehouseRepository.
 *
 * <p>Requires an embedded test database (e.g. H2, test scope) on the
 * classpath, since @DataJpaTest replaces the configured DataSource with an
 * embedded one by default.
 *
 * <p>ASSUMPTION: WarehouseRepository.findByCode(String) is assumed to return
 * Optional&lt;Warehouse&gt;, matching the Optional-returning convention used
 * elsewhere in this codebase.
 */
@DataJpaTest
class WarehouseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Warehouse validWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        return warehouse;
    }

    @Nested
    class CodeUniquenessConstraint {

        @Test
        void shouldSaveWarehouseWithUniqueCodeSuccessfully() {
            Warehouse warehouse = validWarehouse("WH-MAIN", "Main Yard Warehouse");

            Warehouse saved = warehouseRepository.saveAndFlush(warehouse);

            assertThat(saved.getId()).isNotNull();

            entityManager.clear();
            Warehouse reloaded = warehouseRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCode()).isEqualTo("WH-MAIN");
            assertThat(reloaded.getName()).isEqualTo("Main Yard Warehouse");
        }

        @Test
        void shouldThrowDataIntegrityViolationExceptionWhenSavingDuplicateCode() {
            warehouseRepository.saveAndFlush(validWarehouse("WH-NORTH", "North Satellite Warehouse"));
            entityManager.clear();

            Warehouse duplicate = validWarehouse("WH-NORTH", "A Completely Different Name");

            assertThatThrownBy(() -> warehouseRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class ExistsByCodeTests {

        @Test
        void shouldReturnTrueWhenCodeExists() {
            warehouseRepository.saveAndFlush(validWarehouse("WH-SOUTH", "South Depot Warehouse"));
            entityManager.clear();

            boolean result = warehouseRepository.existsByCode("WH-SOUTH");

            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenCodeDoesNotExist() {
            boolean result = warehouseRepository.existsByCode("WH-DOES-NOT-EXIST");

            assertThat(result).isFalse();
        }
    }
}