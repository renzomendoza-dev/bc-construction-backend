package com.bcconstructionservices.equipment.mapper;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchLineResponse;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.inventory.service.WarehouseLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentAssignmentBatchMapperTest {

    private EquipmentAssignmentBatchMapper mapper;

    private UserLookupHelper userLookupHelper;
    private WarehouseLookupHelper warehouseLookupHelper;

    private Equipment drill;

    @BeforeEach
    void setUp() {
        mapper = new EquipmentAssignmentBatchMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        warehouseLookupHelper = mock(WarehouseLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);
        ReflectionTestUtils.setField(mapper, "warehouseLookupHelper", warehouseLookupHelper);

        drill = new Equipment();
        drill.setId(42L);
        drill.setAssetTag("EQ-2026-0042");
        drill.setName("DeWalt 20V Cordless Drill");
    }

    private EquipmentAssignmentBatch buildBatch() {
        EquipmentAssignmentBatch batch = EquipmentAssignmentBatch.builder()
                .id(15L)
                .status(EquipmentAssignmentBatchStatus.DRAFT)
                .destinationWarehouseId(2L)
                .holderId(17L)
                .initiatedBy(3L)
                .notes("Weekly dispatch for Sta. Maria site")
                .createdAt(Instant.parse("2026-07-18T09:15:30Z"))
                .updatedAt(Instant.parse("2026-07-18T09:20:00Z"))
                .lines(new ArrayList<>())
                .build();
        return batch;
    }

    private EquipmentAssignmentBatchLine buildLine(Long id, EquipmentAssignmentBatch batch, Equipment equipment) {
        return EquipmentAssignmentBatchLine.builder()
                .id(id)
                .batch(batch)
                .equipment(equipment)
                .conditionNotes("Minor scuff on housing, fully functional")
                .build();
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapBatchToResponseWithAllFields() {
            when(warehouseLookupHelper.resolveWarehouseName(2L)).thenReturn("Site B - Riverside");
            when(userLookupHelper.resolveUserName(17L)).thenReturn("Maria Santos");
            when(userLookupHelper.resolveUserName(3L)).thenReturn("Juan Dela Cruz");
            EquipmentAssignmentBatch batch = buildBatch();

            EquipmentAssignmentBatchResponse response = mapper.toResponse(batch);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(15L);
            assertThat(response.getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.DRAFT);
            assertThat(response.getDestinationWarehouseId()).isEqualTo(2L);
            assertThat(response.getDestinationWarehouseName()).isEqualTo("Site B - Riverside");
            assertThat(response.getHolderId()).isEqualTo(17L);
            assertThat(response.getHolderName()).isEqualTo("Maria Santos");
            assertThat(response.getInitiatedBy()).isEqualTo(3L);
            assertThat(response.getInitiatedByName()).isEqualTo("Juan Dela Cruz");
            assertThat(response.getNotes()).isEqualTo("Weekly dispatch for Sta. Maria site");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-07-18T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-18T09:20:00Z"));
        }

        @Test
        void shouldMapLineItemsInOrder() {
            EquipmentAssignmentBatch batch = buildBatch();
            batch.getLines().add(buildLine(501L, batch, drill));

            EquipmentAssignmentBatchResponse response = mapper.toResponse(batch);

            assertThat(response.getLines()).hasSize(1);
            EquipmentAssignmentBatchLineResponse line = response.getLines().get(0);
            assertThat(line.getId()).isEqualTo(501L);
            assertThat(line.getEquipmentId()).isEqualTo(42L);
            assertThat(line.getEquipmentAssetTag()).isEqualTo("EQ-2026-0042");
            assertThat(line.getEquipmentName()).isEqualTo("DeWalt 20V Cordless Drill");
            assertThat(line.getConditionNotes()).isEqualTo("Minor scuff on housing, fully functional");
        }

        @Test
        void shouldMapBatchWithEmptyLinesListWithoutError() {
            EquipmentAssignmentBatch batch = buildBatch();

            assertThatCode(() -> mapper.toResponse(batch)).doesNotThrowAnyException();

            EquipmentAssignmentBatchResponse response = mapper.toResponse(batch);
            assertThat(response.getLines()).isNotNull();
            assertThat(response.getLines()).isEmpty();
        }

        @Test
        void shouldLeaveHolderNameNull_whenHolderIdIsNull() {
            EquipmentAssignmentBatch batch = buildBatch();
            batch.setHolderId(null);

            EquipmentAssignmentBatchResponse response = mapper.toResponse(batch);

            assertThat(response.getHolderId()).isNull();
            assertThat(response.getHolderName()).isNullOrEmpty();
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            EquipmentAssignmentBatchCreateRequest request = EquipmentAssignmentBatchCreateRequest.builder()
                    .destinationWarehouseId(2L)
                    .holderId(17L)
                    .notes("Weekly dispatch for Sta. Maria site")
                    .lines(List.of())
                    .build();

            EquipmentAssignmentBatch entity = mapper.toEntity(request);

            assertThat(entity).isNotNull();
            assertThat(entity.getDestinationWarehouseId()).isEqualTo(2L);
            assertThat(entity.getHolderId()).isEqualTo(17L);
            assertThat(entity.getNotes()).isEqualTo("Weekly dispatch for Sta. Maria site");
        }

        @Test
        void shouldNotSetServerManagedOrResolvedFieldsFromCreateRequest() {
            EquipmentAssignmentBatchCreateRequest request = EquipmentAssignmentBatchCreateRequest.builder()
                    .destinationWarehouseId(2L)
                    .holderId(17L)
                    .lines(List.of())
                    .build();

            EquipmentAssignmentBatch entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getInitiatedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
            assertThat(entity.getLines()).isNullOrEmpty();
        }
    }
}
