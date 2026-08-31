package com.bcconstructionservices.equipment.repository;

import com.bcconstructionservices.equipment.JpaAuditingTestConfig;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.user.entity.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class EquipmentAssignmentBatchRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EquipmentAssignmentBatchRepository equipmentAssignmentBatchRepository;

    @Autowired
    private EquipmentAssignmentBatchLineRepository equipmentAssignmentBatchLineRepository;

    private Warehouse siteWarehouse;
    private Equipment drill;
    private AppUser holder;

    @BeforeEach
    void setUp() {
        siteWarehouse = Warehouse.builder()
                .code("WH-SITE1").name("Site B - Riverside").type(WarehouseType.SITE).active(true).build();
        entityManager.persist(siteWarehouse);
        entityManager.flush();

        drill = Equipment.builder()
                .assetTag("EQ-2026-0042").name("DeWalt 20V Cordless Drill").status(EquipmentStatus.AVAILABLE).build();
        entityManager.persist(drill);
        entityManager.flush();

        holder = AppUser.builder().keycloakId(UUID.randomUUID()).fullName("Field Worker").build();
        entityManager.persist(holder);
        entityManager.flush();
    }

    private EquipmentAssignmentBatch buildBatch() {
        EquipmentAssignmentBatch batch = new EquipmentAssignmentBatch();
        batch.setDestinationWarehouseId(siteWarehouse.getId());
        batch.setHolderId(holder.getId());
        batch.setLines(new ArrayList<>());
        return batch;
    }

    @Nested
    class LineCascadeBehavior {

        @Test
        void shouldCascadeSaveLineWithCorrectBatchAndEquipmentForeignKeys() {
            EquipmentAssignmentBatch batch = buildBatch();
            EquipmentAssignmentBatchLine line = EquipmentAssignmentBatchLine.builder()
                    .batch(batch).equipment(drill).conditionNotes("Minor scuff").build();
            batch.getLines().add(line);

            EquipmentAssignmentBatch saved = equipmentAssignmentBatchRepository.saveAndFlush(batch);
            Long batchId = saved.getId();
            entityManager.clear();

            List<EquipmentAssignmentBatchLine> lines =
                    equipmentAssignmentBatchLineRepository.findByBatchId(batchId);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).getEquipment().getId()).isEqualTo(drill.getId());
            assertThat(lines.get(0).getConditionNotes()).isEqualTo("Minor scuff");
        }

        @Test
        void shouldDeleteAssociatedLinesWhenBatchIsDeleted() {
            EquipmentAssignmentBatch batch = buildBatch();
            EquipmentAssignmentBatchLine line = EquipmentAssignmentBatchLine.builder()
                    .batch(batch).equipment(drill).build();
            batch.getLines().add(line);

            EquipmentAssignmentBatch saved = equipmentAssignmentBatchRepository.saveAndFlush(batch);
            Long batchId = saved.getId();
            Long lineId = saved.getLines().get(0).getId();
            entityManager.clear();

            assertThat(equipmentAssignmentBatchLineRepository.findById(lineId)).isPresent();

            equipmentAssignmentBatchRepository.deleteById(batchId);
            entityManager.flush();
            entityManager.clear();

            assertThat(equipmentAssignmentBatchRepository.findById(batchId)).isEmpty();
            assertThat(equipmentAssignmentBatchLineRepository.findById(lineId)).isEmpty();
        }
    }

    @Nested
    class StatusDefaultAndFilter {

        @Test
        void shouldDefaultStatusToDraft() {
            EquipmentAssignmentBatch saved = equipmentAssignmentBatchRepository.saveAndFlush(buildBatch());
            entityManager.clear();

            EquipmentAssignmentBatch reloaded = equipmentAssignmentBatchRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.DRAFT);
        }

        @Test
        void shouldFilterByStatus() {
            EquipmentAssignmentBatch draft = buildBatch();
            equipmentAssignmentBatchRepository.saveAndFlush(draft);

            EquipmentAssignmentBatch completed = buildBatch();
            completed.setStatus(EquipmentAssignmentBatchStatus.COMPLETED);
            equipmentAssignmentBatchRepository.saveAndFlush(completed);
            entityManager.clear();

            List<EquipmentAssignmentBatch> result =
                    equipmentAssignmentBatchRepository.findByStatus(EquipmentAssignmentBatchStatus.COMPLETED);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.COMPLETED);
        }
    }
}
