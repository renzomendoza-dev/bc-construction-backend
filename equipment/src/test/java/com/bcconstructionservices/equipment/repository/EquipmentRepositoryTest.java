package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.JpaAuditingTestConfig;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Repository slice test for {@link EquipmentRepository}.
 *
 * IMPORTANT:
 * - Uses @DataJpaTest (not @SpringBootTest) so Spring Boot supplies the
 *   TestEntityManager bean automatically.
 * - No hardcoded PostgreSQLDialect anywhere in this test's config — @DataJpaTest
 *   auto-substitutes the embedded H2 dialect.
 * - No DATABASE_TO_LOWER=TRUE on the H2 datasource. That flag previously caused
 *   CHECK constraint case-mismatch failures (H2 error 23514) on StockMovement's
 *   movement_type constraint; equipment.status carries the same risk if reintroduced.
 * - ASSUMPTION: the `status` CHECK constraint on the equipment table is written as
 *   plain `status IN ('AVAILABLE','IN_USE','MAINTENANCE','RETIRED', ...)`, not
 *   `= ANY (ARRAY[...])`. Verify this against the actual Flyway migration — if it
 *   uses ANY(ARRAY[...]) syntax, the invalid-status test below will behave
 *   differently (or not at all) on H2 vs PostgreSQL, and the migration should be
 *   rewritten to plain IN syntax for portability.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class EquipmentRepositoryTest {

    @Autowired
    private org.springframework.boot.jpa.test.autoconfigure.TestEntityManager entityManager;

    @Autowired
    private EquipmentRepository equipmentRepository;

    private Equipment available1;
    private Equipment available2;
    private Equipment inUse;

    @BeforeEach
    void setUp() {
        available1 = Equipment.builder()
                .assetTag("EQ-001")
                .name("Excavator")
                .status(EquipmentStatus.AVAILABLE)
                .build();

        available2 = Equipment.builder()
                .assetTag("EQ-002")
                .name("Bulldozer")
                .status(EquipmentStatus.AVAILABLE)
                .build();

        inUse = Equipment.builder()
                .assetTag("EQ-003")
                .name("Crane")
                .status(EquipmentStatus.IN_USE)
                .build();
    }

    @Test
    void findByAssetTag_returnsMatchingEquipment_whenExists() {
        entityManager.persistAndFlush(available1);

        Optional<Equipment> result = equipmentRepository.findByAssetTag("EQ-001");

        assertThat(result).isPresent();
        assertThat(result.get().getAssetTag()).isEqualTo("EQ-001");
        assertThat(result.get().getName()).isEqualTo("Excavator");
        assertThat(result.get().getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void findByAssetTag_returnsEmpty_whenNotExists() {
        entityManager.persistAndFlush(available1);

        Optional<Equipment> result = equipmentRepository.findByAssetTag("NONEXISTENT-TAG");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStatus_returnsOnlyMatchingStatus() {
        entityManager.persistAndFlush(available1);
        entityManager.persistAndFlush(available2);
        entityManager.persistAndFlush(inUse);

        List<Equipment> result = equipmentRepository.findByStatus(EquipmentStatus.AVAILABLE);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(Equipment::getAssetTag)
                .containsExactlyInAnyOrder("EQ-001", "EQ-002");
        assertThat(result)
                .allMatch(equipment -> equipment.getStatus() == EquipmentStatus.AVAILABLE);
    }

    @Test
    void save_duplicateAssetTag_throwsDataIntegrityViolationException() {
        entityManager.persistAndFlush(available1);

        Equipment duplicate = Equipment.builder()
                .assetTag("EQ-001") // same asset tag as available1
                .name("Different Name")
                .status(EquipmentStatus.MAINTENANCE)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            equipmentRepository.save(duplicate);
            entityManager.flush(); // force the unique constraint check now
        });
    }

    @Test
    void save_invalidStatusValue_violatesCheckConstraint() {
        // Bypasses the Java enum's type safety on purpose to exercise the DB-level
        // CHECK constraint directly, since EquipmentStatus won't let us compile an
        // invalid value. This goes through the raw EntityManager via a native query,
        // so it does NOT get Spring's exception translation — expect a
        // jakarta.persistence.PersistenceException (wrapping a JDBC
        // ConstraintViolationException), not DataIntegrityViolationException.
        // Do not "fix" this to DataIntegrityViolationException without re-checking
        // that assumption.
        assertThrows(PersistenceException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery(
                            "INSERT INTO equipment (asset_tag, name, status) "
                                    + "VALUES ('EQ-999', 'Bad Status Equipment', 'NOT_A_REAL_STATUS')")
                    .executeUpdate();
            entityManager.flush();
        });
    }
}