package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.workers.dto.AttendanceBatchCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchLineRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchResponse;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.entity.Attendance;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.exception.DuplicateAttendanceException;
import com.bcconstructionservices.workers.exception.InactiveWorkerException;
import com.bcconstructionservices.workers.exception.InvalidAttendanceBatchRequestException;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.mapper.AttendanceMapper;
import com.bcconstructionservices.workers.repository.AttendanceCalendarRow;
import com.bcconstructionservices.workers.repository.AttendanceRepository;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final Long WORKER_ID = 1L;
    private static final Long PROJECT_ID = 12L;
    private static final Long ATTENDANCE_ID = 501L;

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private ProjectExpenseService projectExpenseService;
    @Mock
    private ProjectLookupHelper projectLookupHelper;

    @InjectMocks
    private AttendanceService attendanceService;

    private Worker activeWorker() {
        Worker worker = new Worker();
        worker.setId(WORKER_ID);
        worker.setName("Ramon Villanueva");
        worker.setDailyRate(new BigDecimal("800.00"));
        worker.setActive(true);
        return worker;
    }

    private AttendanceCreateRequest validRequest() {
        return AttendanceCreateRequest.builder()
                .workerId(WORKER_ID)
                .projectId(PROJECT_ID)
                .attendanceDate(LocalDate.of(2026, 9, 5))
                .daysPresent(new BigDecimal("1.0"))
                .build();
    }

    /**
     * What the real AttendanceMapper.toEntity would produce from
     * validRequest() — the mocked mapper below doesn't run real mapping
     * logic, so callers need to hand back a populated entity themselves.
     */
    private Attendance mappedAttendance() {
        Attendance attendance = new Attendance();
        attendance.setProjectId(PROJECT_ID);
        attendance.setAttendanceDate(LocalDate.of(2026, 9, 5));
        attendance.setDaysPresent(new BigDecimal("1.0"));
        return attendance;
    }

    @Nested
    class CreateAttendanceTests {

        @Test
        void shouldCreateExpenseThenSaveAttendanceWithSnapshotAndExpenseId() {
            Worker worker = activeWorker();
            Attendance mapped = mappedAttendance();
            ProjectExpenseResponse expenseResponse = ProjectExpenseResponse.builder().id(305L).build();

            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 5)))
                    .thenReturn(false);
            when(attendanceMapper.toEntity(any(AttendanceCreateRequest.class))).thenReturn(mapped);
            when(projectExpenseService.addExpense(eq(PROJECT_ID), any(ProjectExpenseCreateRequest.class)))
                    .thenReturn(expenseResponse);
            when(attendanceRepository.save(mapped)).thenReturn(mapped);
            when(attendanceMapper.toResponse(mapped)).thenReturn(AttendanceResponse.builder().id(ATTENDANCE_ID).build());

            attendanceService.createAttendance(validRequest());

            assertThat(mapped.getWorker()).isEqualTo(worker);
            assertThat(mapped.getRateSnapshot()).isEqualByComparingTo("800.00");
            assertThat(mapped.getProjectExpenseId()).isEqualTo(305L);

            ArgumentCaptor<ProjectExpenseCreateRequest> captor = ArgumentCaptor.forClass(ProjectExpenseCreateRequest.class);
            verify(projectExpenseService).addExpense(eq(PROJECT_ID), captor.capture());
            assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.LABOR);
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo("800.00");
            assertThat(captor.getValue().getExpenseDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenWorkerDoesNotExist() {
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            AttendanceCreateRequest request = validRequest();
            request.setWorkerId(999L);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> attendanceService.createAttendance(request));
            verify(projectExpenseService, never()).addExpense(any(), any());
        }

        @Test
        void shouldThrowInactiveWorkerExceptionWhenWorkerIsInactive() {
            Worker inactiveWorker = activeWorker();
            inactiveWorker.setActive(false);
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(inactiveWorker));

            assertThatExceptionOfType(InactiveWorkerException.class)
                    .isThrownBy(() -> attendanceService.createAttendance(validRequest()));
            verify(projectExpenseService, never()).addExpense(any(), any());
        }

        @Test
        void shouldThrowDuplicateAttendanceExceptionWhenARecordAlreadyExistsForThatDay() {
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(activeWorker()));
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 5)))
                    .thenReturn(true);

            assertThatExceptionOfType(DuplicateAttendanceException.class)
                    .isThrownBy(() -> attendanceService.createAttendance(validRequest()));
            verify(projectExpenseService, never()).addExpense(any(), any());
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        void shouldPropagateProjectNotEditableExceptionFromProjectExpenseServiceWithoutSavingAttendance() {
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(activeWorker()));
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 5)))
                    .thenReturn(false);
            when(attendanceMapper.toEntity(any(AttendanceCreateRequest.class))).thenReturn(mappedAttendance());
            when(projectExpenseService.addExpense(eq(PROJECT_ID), any(ProjectExpenseCreateRequest.class)))
                    .thenThrow(new ProjectNotEditableException(PROJECT_ID, ProjectStatus.COMPLETED));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> attendanceService.createAttendance(validRequest()));
            verify(attendanceRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteAttendanceTests {

        @Test
        void shouldValidateThenDeleteAttendanceBeforeDeletingTheLinkedExpense() {
            Attendance attendance = new Attendance();
            attendance.setId(ATTENDANCE_ID);
            attendance.setProjectId(PROJECT_ID);
            attendance.setProjectExpenseId(305L);
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));

            attendanceService.deleteAttendance(ATTENDANCE_ID);

            // Order matters: Attendance must be deleted before ProjectExpense,
            // since project_expense_id is a plain column backed by a real,
            // non-deferrable FK (no JPA relation for Hibernate to sequence
            // this itself) — see deleteAttendance's own javadoc for the bug
            // this order fixes.
            InOrder inOrder = inOrder(projectExpenseService, attendanceRepository);
            inOrder.verify(projectExpenseService).assertExpenseDeletable(PROJECT_ID, 305L);
            inOrder.verify(attendanceRepository).delete(attendance);
            inOrder.verify(projectExpenseService).deleteExpense(PROJECT_ID, 305L);
        }

        @Test
        void shouldSkipExpenseDeletionWhenNoExpenseWasLinked() {
            Attendance attendance = new Attendance();
            attendance.setId(ATTENDANCE_ID);
            attendance.setProjectId(PROJECT_ID);
            attendance.setProjectExpenseId(null);
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));

            attendanceService.deleteAttendance(ATTENDANCE_ID);

            verifyNoInteractions(projectExpenseService);
            verify(attendanceRepository).delete(attendance);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenAttendanceDoesNotExist() {
            when(attendanceRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> attendanceService.deleteAttendance(999L));
        }

        @Test
        void shouldPropagateProjectNotEditableExceptionAndLeaveAttendanceUndeleted() {
            Attendance attendance = new Attendance();
            attendance.setId(ATTENDANCE_ID);
            attendance.setProjectId(PROJECT_ID);
            attendance.setProjectExpenseId(305L);
            when(attendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
            org.mockito.Mockito.doThrow(new ProjectNotEditableException(PROJECT_ID, ProjectStatus.COMPLETED))
                    .when(projectExpenseService).assertExpenseDeletable(PROJECT_ID, 305L);

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> attendanceService.deleteAttendance(ATTENDANCE_ID));

            // Validated (and failed) before anything was deleted — both rows
            // untouched, matching the pre-existing external behavior.
            verify(attendanceRepository, never()).delete(any());
            verify(projectExpenseService, never()).deleteExpense(any(), any());
        }
    }

    @Nested
    class CreateBatchTests {

        private AttendanceBatchCreateRequest batchRequest(AttendanceBatchLineRequest... lines) {
            return AttendanceBatchCreateRequest.builder()
                    .projectId(PROJECT_ID)
                    .date(LocalDate.of(2026, 9, 7))
                    .entries(List.of(lines))
                    .build();
        }

        private AttendanceBatchLineRequest line(Long workerId, LocalTime timeIn, LocalTime timeOut) {
            return AttendanceBatchLineRequest.builder()
                    .workerId(workerId).timeIn(timeIn).timeOut(timeOut).build();
        }

        @Test
        void shouldCreateAttendanceWithDaysPresentDerivedFromTimeInAndTimeOut() {
            Worker worker = activeWorker();
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 7)))
                    .thenReturn(false);
            when(projectExpenseService.addExpense(eq(PROJECT_ID), any(ProjectExpenseCreateRequest.class)))
                    .thenReturn(ProjectExpenseResponse.builder().id(305L).build());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
            when(attendanceMapper.toResponse(any(Attendance.class)))
                    .thenReturn(AttendanceResponse.builder().id(ATTENDANCE_ID).build());

            AttendanceBatchResponse response = attendanceService.createBatch(
                    batchRequest(line(WORKER_ID, LocalTime.of(7, 0), LocalTime.of(11, 0)))); // 4 hours

            assertThat(response.getCreated()).hasSize(1);
            assertThat(response.getSkipped()).isEmpty();

            ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getDaysPresent()).isEqualByComparingTo("0.50");
            assertThat(captor.getValue().getTimeIn()).isEqualTo(LocalTime.of(7, 0));
            assertThat(captor.getValue().getTimeOut()).isEqualTo(LocalTime.of(11, 0));

            ArgumentCaptor<ProjectExpenseCreateRequest> expenseCaptor =
                    ArgumentCaptor.forClass(ProjectExpenseCreateRequest.class);
            verify(projectExpenseService).addExpense(eq(PROJECT_ID), expenseCaptor.capture());
            assertThat(expenseCaptor.getValue().getAmount()).isEqualByComparingTo("400.00");
        }

        @Test
        void shouldCapDaysPresentAtOneForMoreThanEightHours() {
            Worker worker = activeWorker();
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 7)))
                    .thenReturn(false);
            when(projectExpenseService.addExpense(eq(PROJECT_ID), any(ProjectExpenseCreateRequest.class)))
                    .thenReturn(ProjectExpenseResponse.builder().id(305L).build());
            when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
            when(attendanceMapper.toResponse(any(Attendance.class)))
                    .thenReturn(AttendanceResponse.builder().id(ATTENDANCE_ID).build());

            attendanceService.createBatch(
                    batchRequest(line(WORKER_ID, LocalTime.of(6, 0), LocalTime.of(18, 0)))); // 12 hours

            ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
            verify(attendanceRepository).save(captor.capture());
            assertThat(captor.getValue().getDaysPresent()).isEqualByComparingTo("1.00");
        }

        @Test
        void shouldSkipAnEntryAlreadyRecordedForThatDateWithoutFailingTheBatch() {
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 7)))
                    .thenReturn(true);

            AttendanceBatchResponse response = attendanceService.createBatch(
                    batchRequest(line(WORKER_ID, LocalTime.of(7, 0), LocalTime.of(16, 0))));

            assertThat(response.getCreated()).isEmpty();
            assertThat(response.getSkipped()).hasSize(1);
            assertThat(response.getSkipped().get(0).getWorkerId()).isEqualTo(WORKER_ID);
            verifyNoInteractions(projectExpenseService);
            verify(workerRepository, never()).findById(any());
        }

        @Test
        void shouldThrowInvalidAttendanceBatchRequestExceptionWhenTimeOutIsNotAfterTimeIn() {
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 7)))
                    .thenReturn(false);

            assertThatExceptionOfType(InvalidAttendanceBatchRequestException.class)
                    .isThrownBy(() -> attendanceService.createBatch(
                            batchRequest(line(WORKER_ID, LocalTime.of(16, 0), LocalTime.of(7, 0)))));
            verifyNoInteractions(projectExpenseService);
        }

        @Test
        void shouldThrowInvalidAttendanceBatchRequestExceptionWhenAWorkerIdAppearsTwice() {
            AttendanceBatchLineRequest first = line(WORKER_ID, LocalTime.of(7, 0), LocalTime.of(11, 0));
            AttendanceBatchLineRequest second = line(WORKER_ID, LocalTime.of(12, 0), LocalTime.of(16, 0));

            assertThatExceptionOfType(InvalidAttendanceBatchRequestException.class)
                    .isThrownBy(() -> attendanceService.createBatch(batchRequest(first, second)));
            verifyNoInteractions(attendanceRepository, projectExpenseService);
        }

        @Test
        void shouldThrowInactiveWorkerExceptionForAnInactiveWorkerEntry() {
            Worker inactiveWorker = activeWorker();
            inactiveWorker.setActive(false);
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(WORKER_ID, LocalDate.of(2026, 9, 7)))
                    .thenReturn(false);
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(inactiveWorker));

            assertThatExceptionOfType(InactiveWorkerException.class)
                    .isThrownBy(() -> attendanceService.createBatch(
                            batchRequest(line(WORKER_ID, LocalTime.of(7, 0), LocalTime.of(16, 0)))));
            verifyNoInteractions(projectExpenseService);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenAWorkerDoesNotExist() {
            when(attendanceRepository.existsByWorkerIdAndAttendanceDate(999L, LocalDate.of(2026, 9, 7)))
                    .thenReturn(false);
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> attendanceService.createBatch(
                            batchRequest(line(999L, LocalTime.of(7, 0), LocalTime.of(16, 0)))));
        }
    }

    @Nested
    class GetCalendarTests {

        @Test
        void shouldResolveProjectNameOncePerDistinctProjectAcrossMultipleDates() {
            AttendanceCalendarRow row1 = mockRow(LocalDate.of(2026, 9, 1), PROJECT_ID, 6);
            AttendanceCalendarRow row2 = mockRow(LocalDate.of(2026, 9, 2), PROJECT_ID, 4);
            when(attendanceRepository.calendarSummary(null, null, null)).thenReturn(List.of(row1, row2));
            when(projectLookupHelper.resolveProjectName(PROJECT_ID)).thenReturn("Sta. Maria Warehouse Expansion");

            var entries = attendanceService.getCalendar(null, null, null);

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(entries.get(0).getWorkerCount()).isEqualTo(6);
            assertThat(entries.get(0).getProjectName()).isEqualTo("Sta. Maria Warehouse Expansion");
            assertThat(entries.get(1).getWorkerCount()).isEqualTo(4);
            verify(projectLookupHelper, org.mockito.Mockito.times(1)).resolveProjectName(PROJECT_ID);
        }

        @Test
        void shouldReturnEmptyListWhenNoAttendanceRecorded() {
            when(attendanceRepository.calendarSummary(PROJECT_ID, null, null)).thenReturn(List.of());

            var entries = attendanceService.getCalendar(PROJECT_ID, null, null);

            assertThat(entries).isEmpty();
            verifyNoInteractions(projectLookupHelper);
        }

        private AttendanceCalendarRow mockRow(LocalDate date, Long projectId, long workerCount) {
            AttendanceCalendarRow row = org.mockito.Mockito.mock(AttendanceCalendarRow.class);
            when(row.getDate()).thenReturn(date);
            when(row.getProjectId()).thenReturn(projectId);
            when(row.getWorkerCount()).thenReturn(workerCount);
            return row;
        }
    }
}
