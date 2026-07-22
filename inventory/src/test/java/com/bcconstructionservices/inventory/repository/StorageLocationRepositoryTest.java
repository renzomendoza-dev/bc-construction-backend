package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.StorageLocation;
import com.bcconstructionservices.inventory.entity.Warehouse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest slice tests for StorageLocationRepository.
 *
 * <p>Requires an embedded test database (e.g. H2, test scope) on the
 * classpath, since @DataJpaTest replaces the configured DataSource with an
 * embedded one by default.
 *
 * <p>Covers: the (warehouse_id, code) uniqueness constraint,
 * existsByWarehouseIdAndCode(Long, String), and the single-arg
 * findByWarehouseId(Long) list finder — matching StorageLocationRepository's
 * actual declared methods.
 */
@DataJpaTest
class StorageLocationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(true);
        entityManager.persistAndFlush(warehouse);
        return warehouse;
    }

    private StorageLocation validLocation(Warehouse warehouse, String code) {
        StorageLocation location = new StorageLocation();
        location.setWarehouse(warehouse);
        location.setCode(code);
        return location;
    }

    @Nested
    class CodeUniquenessScopedPerWarehouse {

        @Test
        void shouldSaveStorageLocationWithCodeUniqueWithinItsWarehouseSuccessfully() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            StorageLocation location = validLocation(warehouse, "A-01-02");

            StorageLocation saved = storageLocationRepository.saveAndFlush(location);

            assertThat(saved.getId()).isNotNull();

            entityManager.clear();
            StorageLocation reloaded = storageLocationRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCode()).isEqualTo("A-01-02");
            assertThat(reloaded.getWarehouse().getId()).isEqualTo(warehouse.getId());
        }

        @Test
        void shouldThrowDataIntegrityViolationExceptionForDuplicateCodeWithinSameWarehouse() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouse, "A-01-02"));
            entityManager.clear();

            // Same warehouse object instance is safe to reuse here purely as
            // an FK reference for a brand-new StorageLocation - it still
            // carries its generated id even though it's now detached.
            StorageLocation duplicate = validLocation(warehouse, "A-01-02");

            assertThatThrownBy(() -> storageLocationRepository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldAllowSameCodeInDifferentWarehouses() {
            Warehouse warehouseA = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Warehouse warehouseB = persistWarehouse("WH-NORTH", "North Satellite Warehouse");

            storageLocationRepository.saveAndFlush(validLocation(warehouseA, "A-01-02"));

            // Constraint is scoped to (warehouse_id, code), not global on code
            // alone, so the same code in a different warehouse must succeed.
            assertThatCode(() -> storageLocationRepository.saveAndFlush(validLocation(warehouseB, "A-01-02")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class ExistsByWarehouseIdAndCodeTests {

        @Test
        void shouldReturnTrueWhenFound() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouse, "A-01-02"));
            entityManager.clear();

            boolean result =
                    storageLocationRepository.existsByWarehouseIdAndCode(warehouse.getId(), "A-01-02");

            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenNotFound() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");

            boolean result =
                    storageLocationRepository.existsByWarehouseIdAndCode(warehouse.getId(), "Z-99-99");

            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseWhenWarehouseMatchesButCodeDoesNot() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouse, "A-01-02"));
            entityManager.clear();

            boolean result =
                    storageLocationRepository.existsByWarehouseIdAndCode(warehouse.getId(), "B-02-03");

            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseWhenCodeMatchesButWarehouseDoesNot() {
            Warehouse warehouseA = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Warehouse warehouseB = persistWarehouse("WH-NORTH", "North Satellite Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouseA, "A-01-02"));
            entityManager.clear();

            boolean result =
                    storageLocationRepository.existsByWarehouseIdAndCode(warehouseB.getId(), "A-01-02");

            assertThat(result).isFalse();
        }
    }

    @Nested
    class FindByWarehouseIdTests {

        @Test
        void shouldReturnAllLocationsForTheGivenWarehouse() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouse, "A-01-02"));
            storageLocationRepository.saveAndFlush(validLocation(warehouse, "A-01-03"));
            entityManager.clear();

            List<StorageLocation> result = storageLocationRepository.findByWarehouseId(warehouse.getId());

            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(StorageLocation::getCode)
                    .containsExactlyInAnyOrder("A-01-02", "A-01-03");
        }

        @Test
        void shouldReturnEmptyListWhenWarehouseHasNoLocations() {
            Warehouse warehouse = persistWarehouse("WH-MAIN", "Main Yard Warehouse");

            List<StorageLocation> result = storageLocationRepository.findByWarehouseId(warehouse.getId());

            assertThat(result).isEmpty();
        }

        @Test
        void shouldNotReturnLocationsBelongingToOtherWarehouses() {
            Warehouse warehouseA = persistWarehouse("WH-MAIN", "Main Yard Warehouse");
            Warehouse warehouseB = persistWarehouse("WH-NORTH", "North Satellite Warehouse");
            storageLocationRepository.saveAndFlush(validLocation(warehouseA, "A-01-02"));
            storageLocationRepository.saveAndFlush(validLocation(warehouseB, "B-01-02"));
            entityManager.clear();

            List<StorageLocation> result = storageLocationRepository.findByWarehouseId(warehouseA.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("A-01-02");
        }
    }
}