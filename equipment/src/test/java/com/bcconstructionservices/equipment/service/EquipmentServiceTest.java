package com.bcconstructionservices.equipment.service;

import com.bcconstructionservices.equipment.dto.EquipmentCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentUpdateRequest;
import com.bcconstructionservices.equipment.entity.Equipment;
import com.bcconstructionservices.equipment.entity.EquipmentAssignment;
import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import com.bcconstructionservices.equipment.exception.DuplicateAssetTagException;
import com.bcconstructionservices.equipment.exception.EquipmentNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidCheckoutUserException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.NoOpenAssignmentException;
import com.bcconstructionservices.equipment.mapper.EquipmentMapper;
import com.bcconstructionservices.equipment.repository.EquipmentAssignmentRepository;
import com.bcconstructionservices.equipment.repository.EquipmentRepository;
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

    private EquipmentService equipmentService;

    private Equipment availableEquipment;
    private Equipment checkedOutEquipment;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService(
                equipmentRepository, equipmentAssignmentRepository, equipmentMapper, userRepository);

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
                .currentSite("Site A")
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

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
                .build();

        // Mapper hands back an entity with no status set yet — the service is
        // responsible for defaulting it to AVAILABLE.
        Equipment mappedEntity = Equipment.builder()
                .assetTag("EQ-2026-0003")
                .name("Bosch Rotary Hammer")
                .build();

        when(equipmentRepository.findByAssetTag("EQ-2026-0003")).thenReturn(Optional.empty());
        when(equipmentMapper.toEntity(request)).thenReturn(mappedEntity);
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.create(request);

        ArgumentCaptor<Equipment> captor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(captor.capture());

        Equipment saved = captor.getValue();
        assertThat(saved.getAssetTag()).isEqualTo("EQ-2026-0003");
        assertThat(saved.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
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
        assertThat(result.getCurrentSite()).isEqualTo("Site A");

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
    void checkOut_throwsWhenCurrentStatusIsNotAvailable() {
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));

        assertThrows(InvalidEquipmentStatusException.class,
                () -> equipmentService.checkOut(2L, 21L, "Site B - Riverside", null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_throwsWhenUserDoesNotExist() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(InvalidCheckoutUserException.class,
                () -> equipmentService.checkOut(1L, 99L, "Site B - Riverside", null));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkOut_succeeds_createsAssignmentAndUpdatesEquipment() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(availableEquipment));
        when(userRepository.existsById(17L)).thenReturn(true);
        when(equipmentAssignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.checkOut(
                1L, 17L, "Site C", "Minor scuff on housing, fully functional");

        ArgumentCaptor<EquipmentAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(equipmentAssignmentRepository).save(assignmentCaptor.capture());

        EquipmentAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.getEquipment()).isEqualTo(availableEquipment);
        assertThat(savedAssignment.getAssignedToId()).isEqualTo(17L);
        assertThat(savedAssignment.getSite()).isEqualTo("Site C");
        assertThat(savedAssignment.getConditionOut()).isEqualTo("Minor scuff on housing, fully functional");
        assertThat(savedAssignment.getCheckedOutAt()).isNotNull();
        assertThat(savedAssignment.getCheckedInAt()).isNull();

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());

        Equipment savedEquipment = equipmentCaptor.getValue();
        assertThat(savedEquipment.getStatus()).isEqualTo(EquipmentStatus.CHECKED_OUT);
        assertThat(savedEquipment.getCurrentHolderId()).isEqualTo(17L);
        assertThat(savedEquipment.getCurrentSite()).isEqualTo("Site C");
        assertThat(savedEquipment.getCheckedOutAt()).isNotNull();

        assertThat(result).isEqualTo(savedEquipment);
    }

    // ---------------------------------------------------------------
    // checkIn()
    // ---------------------------------------------------------------

    @Test
    void checkIn_throwsWhenThereIsNoOpenAssignment() {
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.empty());

        assertThrows(NoOpenAssignmentException.class,
                () -> equipmentService.checkIn(2L, "Returned in working order"));

        verify(equipmentAssignmentRepository, never()).save(any(EquipmentAssignment.class));
        verify(equipmentRepository, never()).save(any(Equipment.class));
    }

    @Test
    void checkIn_succeeds_closesAssignmentAndResetsEquipment() {
        EquipmentAssignment openAssignment = EquipmentAssignment.builder()
                .id(301L)
                .equipment(checkedOutEquipment)
                .assignedToId(17L)
                .site("Site A")
                .checkedOutAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .checkedInAt(null)
                .build();

        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(checkedOutEquipment));
        when(equipmentAssignmentRepository.findByEquipmentIdAndCheckedInAtIsNull(2L))
                .thenReturn(Optional.of(openAssignment));
        when(equipmentAssignmentRepository.save(any(EquipmentAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentRepository.save(any(Equipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.checkIn(2L, "Returned in working order, blade needs sharpening");

        ArgumentCaptor<EquipmentAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(EquipmentAssignment.class);
        verify(equipmentAssignmentRepository).save(assignmentCaptor.capture());

        EquipmentAssignment savedAssignment = assignmentCaptor.getValue();
        assertThat(savedAssignment.getCheckedInAt()).isNotNull();
        assertThat(savedAssignment.getConditionIn())
                .isEqualTo("Returned in working order, blade needs sharpening");

        ArgumentCaptor<Equipment> equipmentCaptor = ArgumentCaptor.forClass(Equipment.class);
        verify(equipmentRepository).save(equipmentCaptor.capture());

        Equipment savedEquipment = equipmentCaptor.getValue();
        assertThat(savedEquipment.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(savedEquipment.getCurrentHolderId()).isNull();
        assertThat(savedEquipment.getCurrentSite()).isNull();
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