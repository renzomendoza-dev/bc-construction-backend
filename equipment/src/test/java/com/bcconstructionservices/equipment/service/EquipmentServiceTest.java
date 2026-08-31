package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.exception.DuplicateAssetTagException;
import com.bcconstructionservices.equipment.exception.EquipmentAlreadyAtWarehouseException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.InvalidWarehouseTypeException;
import com.bcconstructionservices.equipment.exception.NoOpenAssignmentException;
import com.bcconstructionservices.equipment.exception.WarehouseNotFoundException;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
import com.bcconstructionservices.inventory.entity.Warehouse;
import com.bcconstructionservices.inventory.entity.WarehouseType;
import com.bcconstructionservices.inventory.repository.WarehouseRepository;
import com.bcconstructionservices.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EquipmentService}, matched against the actual
 * implementation (constructor takes EquipmentRepository,
 * EquipmentAssignmentRepository, EquipmentMapper).
 *
 * NOTES ON COVERAGE BOUNDARIES:
 *
 * - update()'s "does not touch status/holder/site" guarantee is implemented by
 *   EquipmentMapper#updateEntityFromRequest, which is mocked here. This test can
 *   only prove the SERVICE itself never independently mutates those fields
 *   (it doesn't call any setStatus/setCurrentHolderId/setCurrentSite outside of
 *   checkOut/checkIn). It CANNOT prove the real generated mapper preserves those
 *   fields — that belongs in an EquipmentMapperTest against the actual MapStruct
 *   impl, per this project's business-logic-vs-mapping separation convention.
 *
 * - findOverdue(days) delegates entirely to
 *   equipmentRepository.findByStatusAndCheckedOutAtBefore(CHECKED_OUT, cutoff).
 *   There is no in-memory filtering in the service, so the "excludes recently
 *   checked out equipment" behavior is NOT service-level logic — it lives in the
 *   repository query itself. The test below only verifies the service passes the
 *   correct status/cutoff through and returns whatever the repository gives back.
 *   Real coverage of the filtering behavior belongs in EquipmentRepositoryTest,
 *   exercised against H2 with real persisted rows.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private EquipmentAssignmentRepository equipmentAssignmentRepository;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    private EquipmentService equipmentService;

    private Equipment availableEquipment;
    private Equipment checkedOutEquipment;
    private Warehouse siteWarehouse;
    private Warehouse otherSiteWarehouse;
    private Warehouse mainWarehouse;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService(
                equipmentRepository, equipmentAssignmentRepository, equipmentMapper, userRepository,
                warehouseRepository);

        availableEquipment = Equipment.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .category("Power Tools")
                .status(EquipmentStatus.AVAILABLE)
                .build();

        checkedOutEquipment = Equipment.builder()
                .id(2L)
                .assetTag("EQ-2026-0002")
                .name("Bulldozer")
                .category("Heavy Machinery")
                .status(EquipmentStatus.CHECKED_OUT)
                .currentHolderId(17L)
                .currentWarehouseId(2L)
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        siteWarehouse = Warehouse.builder().id(2L).code("WH-SITE1").name("Site B - Riverside")
                .type(WarehouseType.SITE).active(true).build();
        otherSiteWarehouse = Warehouse.builder().id(3L).code("WH-SITE2").name("Site C - Downtown")
                .type(WarehouseType.SITE).active(true).build();
        mainWarehouse = Warehouse.builder().id(1L).code("WH-MAIN").name("Main Warehouse")
                .type(WarehouseType.MAIN).active(true).build();

        // NOTE: stubs are added per-test in the test body, not here, so tests that
        // don't touch a given mock don't trip Mockito's strict stubbing checks.
    }

    // ---------------------------------------------------------------
    // create()
    // ---------------------------------------------------------------

    @Test
    void create_throwsWhenAssetTagAlreadyExists() {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0001")
                .name("DeWalt 20V Cordless Drill")
                .warehouseId(1L)
                .build();

        when(equipmentRepository.findByAssetTag("EQ-2026-0001"))
                .thenReturn(Optional.of(availableEquipment));

        assertThrows(DuplicateAssetTagException.class,
                () -> equipmentService.create(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
        verify(equipmentMapper, never()).toEntity(any());
    }

    @Test
    void create_succeedsAndDefaultsStatusToAvailable_whenAssetTagIsUnique() {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0003")
                .name("Bosch Rotary Hammer")
                .warehouseId(1L)
                .build();

        // Mapper hands back an entity with no status set yet — the service is
        // responsible for defaulting it to AVAILABLE.
        Equipment mappedEntity = Equipment.builder()
                .assetTag("EQ-2026-0003")
                .name("Bosch Rotary Hammer")
                .build();

        when(equipmentRepository.findByAssetTag("EQ-2026-0003")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(mainWarehouse));
        when(equipmentMapper.toEntity(request)).thenReturn(mappedEntity);
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.create(request);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());

        Equipment saved = captor.getValue();
        assertThat(saved.getAssetTag()).isEqualTo("EQ-2026-0003");
        assertThat(saved.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(saved.getCurrentWarehouseId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void create_throwsWarehouseNotFoundException_whenWarehouseIdDoesNotExist() {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0004")
                .name("Bosch Rotary Hammer")
                .warehouseId(999L)
                .build();

        when(equipmentRepository.findByAssetTag("EQ-2026-0004")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(WarehouseNotFoundException.class, () -> equipmentService.create(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void create_throwsInvalidWarehouseTypeException_whenWarehouseIsNotMain() {
        EquipmentCreateRequest request = EquipmentCreateRequest.builder()
                .assetTag("EQ-2026-0005")
                .name("Bosch Rotary Hammer")
                .warehouseId(2L)
                .build();

        when(equipmentRepository.findByAssetTag("EQ-2026-0005")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(siteWarehouse));

        assertThrows(InvalidWarehouseTypeException.class, () -> equipmentService.create(request));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    // ---------------------------------------------------------------
    // update()
    // ---------------------------------------------------------------

    @Test
    void update_updatesOnlyAllowedFields_andDoesNotTouchStatusHolderSite() {
        EquipmentUpdateRequest request = EquipmentUpdateRequest.builder()
                .name("Bulldozer (renamed)")
                .build();

        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));

        // Simulate the mapper doing exactly what it's meant to: touch only `name`.
        // This proves the SERVICE doesn't independently mutate status/holder/site
        // outside of what the mapper does — see class-level note re: the real
        // mapper's field-preservation behavior needing its own test.
        doAnswer(invocation -> {
            Equipment target = invocation.getArgument(1);
            target.setName("Bulldozer (renamed)");
            return null;
        }).when(equipmentMapper).updateEntityFromRequest(eq(request), eq(checkedOutEquipment));

        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.update(2L, request);

        assertThat(result.getName()).isEqualTo("Bulldozer (renamed)");
        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.CHECKED_OUT);
        assertThat(result.getCurrentHolderId()).isEqualTo(17L);
        assertThat(result.getCurrentWarehouseId()).isEqualTo(2L);

        verify(equipmentMapper).updateEntityFromRequest(request, checkedOutEquipment);
    }

    @Test
    void update_throwsWhenEquipmentIdDoesNotExist() {
        EquipmentUpdateRequest request = EquipmentUpdateRequest.builder()
                .name("New Name")
                .build();

        when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class,
                () -> equipmentService.update(999L, request));

        verify(equipmentMapper, never()).updateEntityFromRequest(any(), any());
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    // ---------------------------------------------------------------
    // checkOut()
    // ---------------------------------------------------------------

    @Test
    void checkOut_throwsWhenCurrentStatusIsNeitherAvailableNorCheckedOutNorInUse() {
        // CHECKED_OUT/IN_USE are now valid starting statuses too (the
        // transfer case) — this test needs a status that's invalid for
        // checkout under either interpretation.
        Equipment retiredEquipment = Equipment.builder()
                .id(3L)
                .assetTag("EQ-2026-0003")
                .status(EquipmentStatus.RETIRED)
                .build();
        when(equipmentRepository.findById(3L)).thenReturn(Optional.of(retiredEquipment));

        assertThrows(InvalidEquipmentStatusException.class,
                () -> equipmentService.checkOut(3L, 21L, 2L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_throwsWhenUserDoesNotExist() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(InvalidCheckoutUserException.class,
                () -> equipmentService.checkOut(1L, 99L, 2L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_throwsWarehouseNotFoundException_whenSiteWarehouseIdDoesNotExist() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(17L)).thenReturn(true);
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(WarehouseNotFoundException.class,
                () -> equipmentService.checkOut(1L, 17L, 999L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_throwsInvalidWarehouseTypeException_whenSiteWarehouseIsNotSite() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(17L)).thenReturn(true);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(mainWarehouse));

        assertThrows(InvalidWarehouseTypeException.class,
                () -> equipmentService.checkOut(1L, 17L, 1L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_succeeds_createsAssignmentAndUpdatesEquipment() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(17L)).thenReturn(true);
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(siteWarehouse));
        when(equipmentAssignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.checkOut(
                1L, 17L, 2L, "Minor scuff on housing, fully functional");

        ArgumentCaptor<EquipmentAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(equipmentAssignmentRepository).save(assignmentCaptor.capture());

        EquipmentAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.getEquipment()).isEqualTo(availableEquipment);
        assertThat(savedAssignment.getAssignedToId()).isEqualTo(17L);
        assertThat(savedAssignment.getWarehouseId()).isEqualTo(2L);
        assertThat(savedAssignment.getConditionOut()).isEqualTo("Minor scuff on housing, fully functional");
        assertThat(savedAssignment.getCheckedOutAt()).isNotNull();
        assertThat(savedAssignment.getCheckedInAt()).isNull();

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());

        Equipment savedEquipment = equipmentCaptor.getValue();
        assertThat(savedEquipment.getStatus()).isEqualTo(EquipmentStatus.CHECKED_OUT);
        assertThat(savedEquipment.getCurrentHolderId()).isEqualTo(17L);
        assertThat(savedEquipment.getCurrentWarehouseId()).isEqualTo(2L);
        assertThat(savedEquipment.getCheckedOutAt()).isNotNull();

        assertThat(result).isEqualTo(savedEquipment);
    }

    // ---------------------------------------------------------------
    // checkOut() — direct site-to-site transfer
    // ---------------------------------------------------------------

    @Test
    void checkOut_transfersEquipment_whenCurrentlyCheckedOutAtADifferentSite() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .id(301L)
                .equipment(checkedOutEquipment)
                .assignedToId(17L)
                .warehouseId(2L)
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(userRepository.existsById(21L)).thenReturn(true);
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(openAssignment));
        when(warehouseRepository.findById(3L)).thenReturn(Optional.of(otherSiteWarehouse));
        when(equipmentAssignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.checkOut(2L, 21L, 3L, "Relocated to downtown site");

        // The OLD assignment is closed like a check-in would close it...
        assertThat(openAssignment.getCheckedInAt()).isNotNull();
        assertThat(openAssignment.getConditionIn()).isEqualTo("Relocated to downtown site");
        assertThat(openAssignment.getReturnWarehouseId()).isEqualTo(3L);

        // ...and a NEW assignment is opened at the destination, same as a
        // fresh checkout, reassigning custody to the new holder.
        ArgumentCaptor<EquipmentAssignment> assignmentCaptor = ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(equipmentAssignmentRepository, times(2)).save(assignmentCaptor.capture());
        EquipmentAssignment newAssignment = assignmentCaptor.getAllValues().get(1);
        assertThat(newAssignment.getAssignedToId()).isEqualTo(21L);
        assertThat(newAssignment.getWarehouseId()).isEqualTo(3L);
        assertThat(newAssignment.getConditionOut()).isEqualTo("Relocated to downtown site");
        assertThat(newAssignment.getCheckedInAt()).isNull();

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.CHECKED_OUT);
        assertThat(result.getCurrentHolderId()).isEqualTo(21L);
        assertThat(result.getCurrentWarehouseId()).isEqualTo(3L);
    }

    @Test
    void checkOut_throwsEquipmentAlreadyAtWarehouseException_whenTransferTargetsCurrentWarehouse() {
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(userRepository.existsById(21L)).thenReturn(true);
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(EquipmentAssignment.builder().equipment(checkedOutEquipment).build()));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(siteWarehouse));

        // checkedOutEquipment.currentWarehouseId is already 2L (siteWarehouse).
        assertThrows(EquipmentAlreadyAtWarehouseException.class,
                () -> equipmentService.checkOut(2L, 21L, 2L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_throwsNoOpenAssignmentException_whenTransferHasNoOpenAssignmentToClose() {
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(userRepository.existsById(21L)).thenReturn(true);
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.empty());

        assertThrows(NoOpenAssignmentException.class,
                () -> equipmentService.checkOut(2L, 21L, 3L, null));

        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    // ---------------------------------------------------------------
    // checkIn()
    // ---------------------------------------------------------------

    @Test
    void checkIn_throwsWhenEquipmentStatusIsNotCheckedOutOrInUse() {
        Equipment available = Equipment.builder()
                .id(1L)
                .assetTag("EQ-2026-0001")
                .status(EquipmentStatus.AVAILABLE)
                .build();
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(available));

        assertThrows(InvalidEquipmentStatusException.class,
                () -> equipmentService.checkIn(1L, 1L, "Returned in working order"));

        verify(equipmentAssignmentRepository, never()).findByEquipmentIdAndCheckedInAtIsNull(any());
        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkIn_throwsWhenThereIsNoOpenAssignment() {
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.empty());

        assertThrows(NoOpenAssignmentException.class,
                () -> equipmentService.checkIn(2L, 1L, "Returned in working order"));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkIn_throwsWarehouseNotFoundException_whenDestinationWarehouseIdDoesNotExist() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .id(301L)
                .equipment(checkedOutEquipment)
                .assignedToId(17L)
                .warehouseId(2L)
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(openAssignment));
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(WarehouseNotFoundException.class,
                () -> equipmentService.checkIn(2L, 999L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkIn_throwsInvalidWarehouseTypeException_whenDestinationWarehouseIsNotMain() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .id(301L)
                .equipment(checkedOutEquipment)
                .assignedToId(17L)
                .warehouseId(2L)
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(openAssignment));
        when(warehouseRepository.findById(2L)).thenReturn(Optional.of(siteWarehouse));

        assertThrows(InvalidWarehouseTypeException.class,
                () -> equipmentService.checkIn(2L, 2L, null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkIn_succeeds_closesAssignmentAndResetsEquipment() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .id(301L)
                .equipment(checkedOutEquipment)
                .assignedToId(17L)
                .warehouseId(2L)
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .checkedInAt(null)
                .build();

        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(openAssignment));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(mainWarehouse));
        when(equipmentAssignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.checkIn(2L, 1L, "Returned in working order, blade needs sharpening");

        ArgumentCaptor<EquipmentAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(equipmentAssignmentRepository).save(assignmentCaptor.capture());

        EquipmentAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.getCheckedInAt()).isNotNull();
        assertThat(savedAssignment.getConditionIn())
                .isEqualTo("Returned in working order, blade needs sharpening");
        assertThat(savedAssignment.getReturnWarehouseId()).isEqualTo(1L);

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());

        Equipment savedEquipment = equipmentCaptor.getValue();
        assertThat(savedEquipment.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(savedEquipment.getCurrentHolderId()).isNull();
        assertThat(savedEquipment.getCurrentWarehouseId()).isEqualTo(1L);
        assertThat(savedEquipment.getCheckedOutAt()).isNull();

        assertThat(result).isEqualTo(savedEquipment);
    }

    // ---------------------------------------------------------------
    // findOverdue()
    // ---------------------------------------------------------------

    @Test
    void findOverdue_delegatesToRepositoryWithCorrectStatusAndCutoff() {
        Equipment overdueEquipment = Equipment.builder()
                .id(3L)
                .assetTag("EQ-2026-0010")
                .name("Overdue Crane")
                .status(EquipmentStatus.CHECKED_OUT)
                .checkedOutAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();

        when(equipmentRepository.findByStatusAndCheckedOutAtBefore(eq(EquipmentStatus.CHECKED_OUT), any(Instant.class)))
                .thenReturn(List.of(overdueEquipment));

        List<Equipment> result = equipmentService.findOverdue(5);

        assertThat(result).containsExactly(overdueEquipment);

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(equipmentRepository, times(1))
                .findByStatusAndCheckedOutAtBefore(eq(EquipmentStatus.CHECKED_OUT), cutoffCaptor.capture());

        // Cutoff should be ~5 days before "now" — allow a small tolerance for test
        // execution time between Instant.now() here and inside the service.
        Instant expectedCutoff = Instant.now().minus(5, ChronoUnit.DAYS);
        assertThat(cutoffCaptor.getValue())
                .isCloseTo(expectedCutoff, within(2, ChronoUnit.SECONDS));
    }

    private static org.assertj.core.data.TemporalUnitWithinOffset within(long value, ChronoUnit unit) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(value, unit);
    }
}