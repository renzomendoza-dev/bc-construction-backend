package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchLineRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatch;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchLine;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.equipment.exception.EquipmentAssignmentBatchNotFoundException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentBatchRequestException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.WarehouseNotFoundException;
import com.bcconstructionservices.equipment.mapper.EquipmentAssignmentBatchMapperImpl;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentBatchLineRepository;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentBatchRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import com.bcconstructionservices.inventory.service.WarehouseLookupHelper;
import com.bcconstructionservices.user.repository.UserRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentAssignmentBatchServiceTest {

    private static final Long BATCH_ID = 15L;
    private static final Long SITE_WAREHOUSE_ID = 2L;
    private static final Long MAIN_WAREHOUSE_ID = 1L;
    private static final Long HOLDER_ID = 17L;
    private static final Long EQUIPMENT_ID = 42L;

    @Mock
    private EquipmentAssignmentBatchRepository equipmentAssignmentBatchRepository;
    @Mock
    private EquipmentAssignmentBatchLineRepository equipmentAssignmentBatchLineRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EquipmentService equipmentService;
    @Spy
    private EquipmentAssignmentBatchMapperImpl equipmentAssignmentBatchMapper = new EquipmentAssignmentBatchMapperImpl();

    @InjectMocks
    private EquipmentAssignmentBatchService equipmentAssignmentBatchService;

    @Mock
    private UserLookupHelper userLookupHelper;
    @Mock
    private WarehouseLookupHelper warehouseLookupHelper;

    private Warehouse siteWarehouse;
    private Warehouse mainWarehouse;
    private Equipment drill;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(equipmentAssignmentBatchMapper, "userLookupHelper", userLookupHelper);
        ReflectionTestUtils.setField(equipmentAssignmentBatchMapper, "warehouseLookupHelper", warehouseLookupHelper);

        siteWarehouse = Warehouse.builder().id(SITE_WAREHOUSE_ID).code("WH-SITE1").name("Site B - Riverside")
                .type(WarehouseType.SITE).active(true).build();
        mainWarehouse = Warehouse.builder().id(MAIN_WAREHOUSE_ID).code("WH-MAIN").name("Main Warehouse")
                .type(WarehouseType.MAIN).active(true).build();

        drill = new Equipment();
        drill.setId(EQUIPMENT_ID);
        drill.setAssetTag("EQ-2026-0042");
        drill.setName("DeWalt 20V Cordless Drill");
    }

    // ---------------------------------------------------------------
    // Test data / stubbing helpers
    // ---------------------------------------------------------------

    private EquipmentAssignmentBatchLineRequest lineRequest(Long equipmentId, String conditionNotes) {
        EquipmentAssignmentBatchLineRequest req = new EquipmentAssignmentBatchLineRequest();
        req.setEquipmentId(equipmentId);
        req.setConditionNotes(conditionNotes);
        return req;
    }

    private EquipmentAssignmentBatchCreateRequest assignOutRequest(Long holderId, List<EquipmentAssignmentBatchLineRequest> lines) {
        EquipmentAssignmentBatchCreateRequest req = new EquipmentAssignmentBatchCreateRequest();
        req.setDestinationWarehouseId(SITE_WAREHOUSE_ID);
        req.setHolderId(holderId);
        req.setLines(lines);
        return req;
    }

    private EquipmentAssignmentBatchCreateRequest returnRequest(Long holderId, List<EquipmentAssignmentBatchLineRequest> lines) {
        EquipmentAssignmentBatchCreateRequest req = new EquipmentAssignmentBatchCreateRequest();
        req.setDestinationWarehouseId(MAIN_WAREHOUSE_ID);
        req.setHolderId(holderId);
        req.setLines(lines);
        return req;
    }

    private void givenSavesEchoTheirArgument() {
        lenient().when(equipmentAssignmentBatchRepository.save(any(EquipmentAssignmentBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EquipmentAssignmentBatch captureSavedBatch() {
        ArgumentCaptor<EquipmentAssignmentBatch> captor = ArgumentCaptor.forClass(EquipmentAssignmentBatch.class);
        verify(equipmentAssignmentBatchRepository).save(captor.capture());
        return captor.getValue();
    }

    private EquipmentAssignmentBatch buildDraftBatch(Long destinationWarehouseId, Long holderId,
                                                       List<EquipmentAssignmentBatchLine> lines) {
        EquipmentAssignmentBatch batch = new EquipmentAssignmentBatch();
        batch.setId(BATCH_ID);
        batch.setStatus(EquipmentAssignmentBatchStatus.DRAFT);
        batch.setDestinationWarehouseId(destinationWarehouseId);
        batch.setHolderId(holderId);
        batch.setLines(new ArrayList<>(lines));
        return batch;
    }

    private EquipmentAssignmentBatchLine line(EquipmentAssignmentBatch batch, Equipment equipment, String notes) {
        EquipmentAssignmentBatchLine line = new EquipmentAssignmentBatchLine();
        line.setId(501L);
        line.setBatch(batch);
        line.setEquipment(equipment);
        line.setConditionNotes(notes);
        return line;
    }

    // ---------------------------------------------------------------
    // createDraft
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldSaveAssignOutDraftWithLinesAndHolder() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(siteWarehouse));
            when(userRepository.existsById(HOLDER_ID)).thenReturn(true);
            when(equipmentRepository.findById(EQUIPMENT_ID)).thenReturn(Optional.of(drill));
            givenSavesEchoTheirArgument();

            equipmentAssignmentBatchService.createDraft(
                    assignOutRequest(HOLDER_ID, List.of(lineRequest(EQUIPMENT_ID, "Minor scuff"))));

            EquipmentAssignmentBatch saved = captureSavedBatch();
            assertThat(saved.getDestinationWarehouseId()).isEqualTo(SITE_WAREHOUSE_ID);
            assertThat(saved.getHolderId()).isEqualTo(HOLDER_ID);
            assertThat(saved.getLines()).hasSize(1);
            assertThat(saved.getLines().get(0).getEquipment()).isEqualTo(drill);
            assertThat(saved.getLines().get(0).getConditionNotes()).isEqualTo("Minor scuff");
        }

        @Test
        void shouldSaveReturnDraftWithNullHolder() {
            when(warehouseRepository.findById(MAIN_WAREHOUSE_ID)).thenReturn(Optional.of(mainWarehouse));
            when(equipmentRepository.findById(EQUIPMENT_ID)).thenReturn(Optional.of(drill));
            givenSavesEchoTheirArgument();

            equipmentAssignmentBatchService.createDraft(
                    returnRequest(null, List.of(lineRequest(EQUIPMENT_ID, "Returned fully functional"))));

            EquipmentAssignmentBatch saved = captureSavedBatch();
            assertThat(saved.getDestinationWarehouseId()).isEqualTo(MAIN_WAREHOUSE_ID);
            assertThat(saved.getHolderId()).isNull();
            verifyNoInteractions(userRepository);
        }

        @Test
        void shouldThrowWarehouseNotFoundExceptionWhenDestinationWarehouseDoesNotExist() {
            when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

            EquipmentAssignmentBatchCreateRequest request = new EquipmentAssignmentBatchCreateRequest();
            request.setDestinationWarehouseId(999L);
            request.setLines(List.of(lineRequest(EQUIPMENT_ID, null)));

            assertThatExceptionOfType(WarehouseNotFoundException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.createDraft(request));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidEquipmentBatchRequestExceptionWhenHolderIdMissingForAssignOut() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(siteWarehouse));

            assertThatExceptionOfType(InvalidEquipmentBatchRequestException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.createDraft(
                            assignOutRequest(null, List.of(lineRequest(EQUIPMENT_ID, null)))));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidEquipmentBatchRequestExceptionWhenHolderIdPresentForReturn() {
            when(warehouseRepository.findById(MAIN_WAREHOUSE_ID)).thenReturn(Optional.of(mainWarehouse));

            assertThatExceptionOfType(InvalidEquipmentBatchRequestException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.createDraft(
                            returnRequest(HOLDER_ID, List.of(lineRequest(EQUIPMENT_ID, null)))));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidCheckoutUserExceptionWhenHolderDoesNotExist() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(siteWarehouse));
            when(userRepository.existsById(999L)).thenReturn(false);

            assertThatExceptionOfType(InvalidCheckoutUserException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.createDraft(
                            assignOutRequest(999L, List.of(lineRequest(EQUIPMENT_ID, null)))));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowEquipmentNotFoundExceptionWhenALineEquipmentDoesNotExist() {
            when(warehouseRepository.findById(SITE_WAREHOUSE_ID)).thenReturn(Optional.of(siteWarehouse));
            when(userRepository.existsById(HOLDER_ID)).thenReturn(true);
            when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EquipmentNotFoundException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.createDraft(
                            assignOutRequest(HOLDER_ID, List.of(lineRequest(999L, null)))));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldCallCheckOutOncePerLineForAnAssignOutBatchAndMarkCompleted() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());
            EquipmentAssignmentBatchLine batchLine = line(batch, drill, "Minor scuff");
            batch.setLines(List.of(batchLine));

            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
            when(equipmentAssignmentBatchLineRepository.findByBatchId(BATCH_ID)).thenReturn(List.of(batchLine));
            when(equipmentService.checkOut(EQUIPMENT_ID, HOLDER_ID, SITE_WAREHOUSE_ID, "Minor scuff"))
                    .thenReturn(drill);
            givenSavesEchoTheirArgument();

            EquipmentAssignmentBatchResponse response = equipmentAssignmentBatchService.submit(BATCH_ID);

            verify(equipmentService, times(1)).checkOut(EQUIPMENT_ID, HOLDER_ID, SITE_WAREHOUSE_ID, "Minor scuff");
            EquipmentAssignmentBatch saved = captureSavedBatch();
            assertThat(saved.getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.COMPLETED);
            assertThat(response.getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.COMPLETED);
        }

        @Test
        void shouldCallCheckInOncePerLineForAReturnBatchAndMarkCompleted() {
            EquipmentAssignmentBatch batch = buildDraftBatch(MAIN_WAREHOUSE_ID, null, List.of());
            EquipmentAssignmentBatchLine batchLine = line(batch, drill, "Returned fully functional");
            batch.setLines(List.of(batchLine));

            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
            when(equipmentAssignmentBatchLineRepository.findByBatchId(BATCH_ID)).thenReturn(List.of(batchLine));
            when(equipmentService.checkIn(EQUIPMENT_ID, MAIN_WAREHOUSE_ID, "Returned fully functional"))
                    .thenReturn(drill);
            givenSavesEchoTheirArgument();

            equipmentAssignmentBatchService.submit(BATCH_ID);

            verify(equipmentService, times(1)).checkIn(EQUIPMENT_ID, MAIN_WAREHOUSE_ID, "Returned fully functional");
            EquipmentAssignmentBatch saved = captureSavedBatch();
            assertThat(saved.getStatus()).isEqualTo(EquipmentAssignmentBatchStatus.COMPLETED);
        }

        @Test
        void shouldNotSaveBatchWhenALineFailsWithInvalidEquipmentStatus() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());
            EquipmentAssignmentBatchLine batchLine = line(batch, drill, null);
            batch.setLines(List.of(batchLine));

            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
            when(equipmentAssignmentBatchLineRepository.findByBatchId(BATCH_ID)).thenReturn(List.of(batchLine));
            when(equipmentService.checkOut(EQUIPMENT_ID, HOLDER_ID, SITE_WAREHOUSE_ID, null))
                    .thenThrow(new InvalidEquipmentStatusException(
                            "Equipment EQ-2026-0042 is not available for checkout (current status: CHECKED_OUT)"));

            assertThatExceptionOfType(InvalidEquipmentStatusException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.submit(BATCH_ID));

            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowInvalidEquipmentBatchRequestExceptionWhenBatchHasNoLines() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());

            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));
            when(equipmentAssignmentBatchLineRepository.findByBatchId(BATCH_ID)).thenReturn(List.of());

            assertThatExceptionOfType(InvalidEquipmentBatchRequestException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.submit(BATCH_ID));

            verifyNoInteractions(equipmentService);
            verify(equipmentAssignmentBatchRepository, never()).save(any());
        }

        @Test
        void shouldThrowEquipmentAssignmentBatchNotFoundExceptionWhenBatchDoesNotExist() {
            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EquipmentAssignmentBatchNotFoundException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.submit(BATCH_ID));

            verifyNoInteractions(equipmentService);
        }
    }

    // ---------------------------------------------------------------
    // getById / findAll
    // ---------------------------------------------------------------

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnMappedResponseForExistingBatch() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());
            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.of(batch));

            EquipmentAssignmentBatchResponse response = equipmentAssignmentBatchService.getById(BATCH_ID);

            assertThat(response.getId()).isEqualTo(BATCH_ID);
        }

        @Test
        void shouldThrowEquipmentAssignmentBatchNotFoundExceptionForGetById() {
            when(equipmentAssignmentBatchRepository.findById(BATCH_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EquipmentAssignmentBatchNotFoundException.class)
                    .isThrownBy(() -> equipmentAssignmentBatchService.getById(BATCH_ID));
        }

        @Test
        void shouldDelegateToFindByStatusWhenStatusFilterProvided() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());
            when(equipmentAssignmentBatchRepository.findByStatus(EquipmentAssignmentBatchStatus.DRAFT))
                    .thenReturn(List.of(batch));

            List<EquipmentAssignmentBatchResponse> result =
                    equipmentAssignmentBatchService.findAll(EquipmentAssignmentBatchStatus.DRAFT);

            assertThat(result).hasSize(1);
            verify(equipmentAssignmentBatchRepository, never()).findAll();
        }

        @Test
        void shouldDelegateToFindAllWhenNoStatusFilterProvided() {
            EquipmentAssignmentBatch batch = buildDraftBatch(SITE_WAREHOUSE_ID, HOLDER_ID, List.of());
            when(equipmentAssignmentBatchRepository.findAll()).thenReturn(List.of(batch));

            List<EquipmentAssignmentBatchResponse> result = equipmentAssignmentBatchService.findAll(null);

            assertThat(result).hasSize(1);
            verify(equipmentAssignmentBatchRepository, never()).findByStatus(any());
        }
    }
}
