package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.JpaAuditingTestConfig;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test for {@link EquipmentAssignmentRepository}.
 *
 * Follows the same H2/dialect rules established in EquipmentRepositoryTest:
 * - @AutoConfigureTestDatabase(replace = NONE) so this module's application.yaml
 *   datasource (jdbc:h2:mem:equipment_test;MODE=PostgreSQL) is honored instead of
 *   @DataJpaTest silently substituting its own embedded database (the root cause
 *   of the earlier "database has been closed" false failure).
 * - No DATABASE_TO_LOWER=TRUE, no hardcoded PostgreSQLDialect — dialect is
 *   auto-detected against the H2 datasource.
 * - Only status values actually present in V15's CHECK constraint
 *   ('AVAILABLE','CHECKED_OUT','IN_USE','IN_REPAIR','RETIRED','LOST') are used
 *   below, to avoid re-triggering the CONSTRAINT_E63-style mismatch.
 *
 * ASSUMPTIONS — verify against the real classes and correct if they differ:
 * - EquipmentAssignment has a @ManyToOne Equipment `equipment` field (backing the
 *   equipment_id FK in V15), plus assignedToId (Long), warehouseId/returnWarehouseId
 *   (Long, FK to Warehouse — added by V24, replacing the original free-text site),
 *   checkedOutAt / checkedInAt (Instant), conditionOut / conditionIn
 *   (String), createdBy (Long), createdAt (DB-defaulted, not set here), and a
 *   Lombok @Builder like Equipment uses.
 * - EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, Long>
 *   and exposes:
 *     Optional<EquipmentAssignment> findByEquipmentIdAndCheckedInAtIsNull(Long equipmentId)
 *     List<EquipmentAssignment> findByAssignedToIdAndCheckedInAtIsNull(Long assignedToId)
 *   findByEquipmentIdAndCheckedInAtIsNull returns Optional (not List) since only
 *   one open assignment per equipment is expected — if the real method returns a
 *   List instead, swap the Optional assertions below for .hasSize(1)/.isEmpty().
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class EquipmentAssignmentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EquipmentAssignmentRepository equipmentAssignmentRepository;

    private Equipment excavator;
    private Equipment bulldozer;
    private AppUser worker;
    private AppUser otherWorker;
    private Warehouse siteWarehouse;

    @BeforeEach
    void setUp() {
        // assigned_to_id is now FK-constrained to app_user, so real rows must
        // exist before any assignment references them.
        worker = AppUser.builder()
                .keycloakId(UUID.randomUUID())
                .fullName("Field Worker")
                .build();
        entityManager.persistAndFlush(worker);

        otherWorker = AppUser.builder()
                .keycloakId(UUID.randomUUID())
                .fullName("Other Field Worker")
                .build();
        entityManager.persistAndFlush(otherWorker);

        // warehouse_id is now FK-constrained to warehouse (inventory module,
        // pulled onto this module's test classpath/schema by equipment's new
        // dependency on inventory) — same reasoning as assigned_to_id above.
        siteWarehouse = Warehouse.builder()
                .code("WH-SITE1")
                .name("Site A")
                .type(WarehouseType.SITE)
                .active(true)
                .build();
        entityManager.persistAndFlush(siteWarehouse);

        excavator = Equipment.builder()
                .assetTag("EQ-101")
                .name("Excavator")
                .status(EquipmentStatus.CHECKED_OUT)
                .build();
        entityManager.persistAndFlush(excavator);

        bulldozer = Equipment.builder()
                .assetTag("EQ-102")
                .name("Bulldozer")
                .status(EquipmentStatus.CHECKED_OUT)
                .build();
        entityManager.persistAndFlush(bulldozer);
    }

    @Test
    void findByEquipmentIdAndCheckedInAtIsNull_returnsOpenAssignment_whenOneExists() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .equipment(excavator)
                .assignedToId(worker.getId())
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();
        entityManager.persistAndFlush(openAssignment);

        Optional<EquipmentAssignment> result =
                equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(excavator.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getEquipment().getId()).isEqualTo(excavator.getId());
        assertThat(result.get().getAssignedToId()).isEqualTo(worker.getId());
        assertThat(result.get().getCheckedInAt()).isNull();
    }

    @Test
    void findByEquipmentIdAndCheckedInAtIsNull_returnsEmpty_onceCheckedInAtIsSet() {
        EquipmentAssignment closedAssignment = EquipmentAssignment.builder()
                .equipment(excavator)
                .assignedToId(worker.getId())
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .checkedInAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        entityManager.persistAndFlush(closedAssignment);

        Optional<EquipmentAssignment> result =
                equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(excavator.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByEquipmentIdAndCheckedInAtIsNull_returnsEmpty_whenEquipmentHasNoAssignments() {
        Optional<EquipmentAssignment> result =
                equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(bulldozer.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByAssignedToIdAndCheckedInAtIsNull_returnsAllOpenAssignments_acrossMultipleEquipment() {
        Long userId = worker.getId();

        EquipmentAssignment openOnExcavator = EquipmentAssignment.builder()
                .equipment(excavator)
                .assignedToId(userId)
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();
        entityManager.persistAndFlush(openOnExcavator);

        EquipmentAssignment openOnBulldozer = EquipmentAssignment.builder()
                .equipment(bulldozer)
                .assignedToId(userId)
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        entityManager.persistAndFlush(openOnBulldozer);

        Equipment crane = Equipment.builder()
                .assetTag("EQ-103")
                .name("Crane")
                .status(EquipmentStatus.AVAILABLE)
                .build();
        entityManager.persistAndFlush(crane);

        // Closed assignment for the same user must not appear in the open list.
        EquipmentAssignment closedForSameUser = EquipmentAssignment.builder()
                .equipment(crane)
                .assignedToId(userId)
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .checkedInAt(Instant.now().minus(9, ChronoUnit.DAYS))
                .build();
        entityManager.persistAndFlush(closedForSameUser);

        // Open assignment for a different user must not appear either.
        EquipmentAssignment openForOtherUser = EquipmentAssignment.builder()
                .equipment(crane)
                .assignedToId(otherWorker.getId())
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(Instant.now().minus(4, ChronoUnit.HOURS))
                .build();
        entityManager.persistAndFlush(openForOtherUser);

        List<EquipmentAssignment> result =
                equipmentAssignmentRepository.findByAssignedToIdAndCheckedInAtIsNull(userId);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(assignment -> assignment.getEquipment().getId())
                .containsExactlyInAnyOrder(excavator.getId(), bulldozer.getId());
        assertThat(result).allMatch(assignment -> assignment.getCheckedInAt() == null);
    }

    @Test
    void save_persistsAndReloadsCheckedOutAndCheckedInTimestamps_notNullCoerced() {
        Instant checkedOut = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant checkedIn = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        EquipmentAssignment assignment = EquipmentAssignment.builder()
                .equipment(excavator)
                .assignedToId(worker.getId())
                .warehouseId(siteWarehouse.getId())
                .checkedOutAt(checkedOut)
                .checkedInAt(checkedIn)
                .build();

        // persistFlushFind clears the persistence context and reloads from the
        // DB, so this actually verifies the round trip rather than just the
        // in-memory object still holding the value we set.
        EquipmentAssignment persisted = entityManager.persistFlushFind(assignment);

        assertThat(persisted.getCheckedOutAt()).isNotNull();
        assertThat(persisted.getCheckedOutAt()).isEqualTo(checkedOut);
        assertThat(persisted.getCheckedInAt()).isNotNull();
        assertThat(persisted.getCheckedInAt()).isEqualTo(checkedIn);
    }
}