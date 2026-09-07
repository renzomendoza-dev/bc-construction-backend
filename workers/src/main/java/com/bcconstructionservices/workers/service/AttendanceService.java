package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.workers.dto.AttendanceBatchCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchLineRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchResponse;
import com.bcconstructionservices.workers.dto.AttendanceBatchSkippedEntry;
import com.bcconstructionservices.workers.dto.AttendanceCalendarEntry;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records a worker's daily attendance and, in the same transaction, auto-creates
 * a LABOR ProjectExpense on the referenced project by calling
 * ProjectExpenseService directly — a real cross-module service dependency
 * (workers -&gt; projects), not just an id + LookupHelper, since this is an
 * actual write-orchestration rather than a display-name lookup. Chosen over
 * an event-driven approach: there's no message broker or async requirement
 * in this single-JVM modular monolith, and a direct call already mirrors
 * what the boundary would become if these two modules were ever split into
 * separate services (an HTTP call standing in for this Java one).
 * ProjectExpenseService.addExpense already enforces the project-editable
 * lock (422) and validates the project exists (404) — reused here rather
 * than duplicated.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final BigDecimal STANDARD_HOURS_PER_DAY = new BigDecimal("8");

    private final AttendanceRepository attendanceRepository;
    private final WorkerRepository workerRepository;
    private final AttendanceMapper attendanceMapper;
    private final ProjectExpenseService projectExpenseService;
    private final ProjectLookupHelper projectLookupHelper;

    @Transactional
    public AttendanceResponse createAttendance(AttendanceCreateRequest request) {
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker", request.getWorkerId()));

        if (!worker.isActive()) {
            throw new InactiveWorkerException(worker.getId());
        }

        if (attendanceRepository.existsByWorkerIdAndAttendanceDate(worker.getId(), request.getAttendanceDate())) {
            throw new DuplicateAttendanceException(worker.getId(), request.getAttendanceDate());
        }

        Attendance attendance = attendanceMapper.toEntity(request);
        attendance.setWorker(worker);
        attendance.setRateSnapshot(worker.getDailyRate());

        // Created before the Attendance row itself is persisted — if the
        // project doesn't exist or is locked, ProjectExpenseService.addExpense
        // throws (404/422) and nothing about this attendance touches the DB.
        ProjectExpenseResponse expense = projectExpenseService.addExpense(
                request.getProjectId(),
                buildExpenseRequest(worker, attendance)
        );
        attendance.setProjectExpenseId(expense.getId());

        Attendance saved = attendanceRepository.save(attendance);
        return attendanceMapper.toResponse(saved);
    }

    /**
     * Deletes an attendance record and, if it generated one, the linked
     * ProjectExpense — the only fix path for a mis-entered record (no edit
     * endpoint; delete and re-add).
     *
     * <p>Order matters here and is easy to get backwards: Attendance.projectExpenseId
     * is a plain Long column with a real, non-deferrable FK to project_expense
     * (V29) — not a JPA relation, so Hibernate has no object-graph metadata to
     * sequence these deletes itself. Deleting the ProjectExpense first (this
     * method's original, buggy order) leaves this Attendance row still
     * referencing it via that FK, and Postgres checks FK constraints
     * immediately, so that delete fails with a foreign-key violation —
     * surfaced as an undifferentiated 500 by the generic exception handler.
     * The fix: validate first via {@link ProjectExpenseService#assertExpenseDeletable}
     * (still leaves both rows untouched if the project is locked, 422 —
     * same external behavior as before), then delete this Attendance row
     * (clearing the FK reference), then delete the ProjectExpense.
     */
    @Transactional
    public void deleteAttendance(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance", attendanceId));

        if (attendance.getProjectExpenseId() != null) {
            projectExpenseService.assertExpenseDeletable(attendance.getProjectId(), attendance.getProjectExpenseId());
        }

        attendanceRepository.delete(attendance);

        if (attendance.getProjectExpenseId() != null) {
            projectExpenseService.deleteExpense(attendance.getProjectId(), attendance.getProjectExpenseId());
        }
    }

    /**
     * Records attendance for multiple workers on one project/day in a single
     * transaction — daysPresent is derived per entry from timeIn/timeOut
     * (see deriveDaysPresent), reusing the same dailyRate * daysPresent
     * expense formula createAttendance already uses, unchanged.
     *
     * <p>A worker who already has a record for this date is skipped, not
     * treated as an error — reported back in the response rather than
     * failing the whole batch, since revisiting an already-recorded day is
     * an expected, normal use of the calendar. Any other failure (inactive
     * worker, worker/project not found, invalid time range, locked project)
     * aborts the whole batch — same all-or-nothing transaction as
     * TransferBatchService.submit.
     */
    @Transactional
    public AttendanceBatchResponse createBatch(AttendanceBatchCreateRequest request) {
        Set<Long> seenWorkerIds = new HashSet<>();
        for (AttendanceBatchLineRequest line : request.getEntries()) {
            if (!seenWorkerIds.add(line.getWorkerId())) {
                throw new InvalidAttendanceBatchRequestException(
                        "Worker " + line.getWorkerId() + " appears more than once in this batch");
            }
        }

        List<AttendanceResponse> created = new ArrayList<>();
        List<AttendanceBatchSkippedEntry> skipped = new ArrayList<>();

        for (AttendanceBatchLineRequest line : request.getEntries()) {
            if (attendanceRepository.existsByWorkerIdAndAttendanceDate(line.getWorkerId(), request.getDate())) {
                skipped.add(AttendanceBatchSkippedEntry.builder()
                        .workerId(line.getWorkerId())
                        .reason("Attendance already recorded for this date")
                        .build());
                continue;
            }

            if (!line.getTimeOut().isAfter(line.getTimeIn())) {
                throw new InvalidAttendanceBatchRequestException(
                        "timeOut (" + line.getTimeOut() + ") must be after timeIn (" + line.getTimeIn()
                                + ") for worker " + line.getWorkerId());
            }

            Worker worker = workerRepository.findById(line.getWorkerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Worker", line.getWorkerId()));
            if (!worker.isActive()) {
                throw new InactiveWorkerException(worker.getId());
            }

            Attendance attendance = new Attendance();
            attendance.setWorker(worker);
            attendance.setProjectId(request.getProjectId());
            attendance.setAttendanceDate(request.getDate());
            attendance.setDaysPresent(deriveDaysPresent(line.getTimeIn(), line.getTimeOut()));
            attendance.setRateSnapshot(worker.getDailyRate());
            attendance.setTimeIn(line.getTimeIn());
            attendance.setTimeOut(line.getTimeOut());
            attendance.setNotes(line.getNotes());

            ProjectExpenseResponse expense = projectExpenseService.addExpense(
                    request.getProjectId(), buildExpenseRequest(worker, attendance));
            attendance.setProjectExpenseId(expense.getId());

            Attendance saved = attendanceRepository.save(attendance);
            created.add(attendanceMapper.toResponse(saved));
        }

        return AttendanceBatchResponse.builder().created(created).skipped(skipped).build();
    }

    /**
     * hoursWorked / a standard 8-hour day, capped at 1.0 (no overtime
     * modeling — that's deferred to payroll work, which can consume the raw
     * timeIn/timeOut instead if it ever needs the uncapped figure).
     */
    private BigDecimal deriveDaysPresent(LocalTime timeIn, LocalTime timeOut) {
        long minutes = Duration.between(timeIn, timeOut).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal fraction = hours.divide(STANDARD_HOURS_PER_DAY, 4, RoundingMode.HALF_UP);
        return fraction.min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Recorded-data-only summary for calendar rendering — deliberately not
     * blended with WorkerProjectAssignment's assigned-crew size; see
     * AttendanceCalendarEntry's own javadoc.
     */
    @Transactional(readOnly = true)
    public List<AttendanceCalendarEntry> getCalendar(Long projectId, LocalDate dateFrom, LocalDate dateTo) {
        List<AttendanceCalendarRow> rows = attendanceRepository.calendarSummary(projectId, dateFrom, dateTo);

        Map<Long, String> projectNameCache = new HashMap<>();
        List<AttendanceCalendarEntry> entries = new ArrayList<>();
        for (AttendanceCalendarRow row : rows) {
            String projectName = projectNameCache.computeIfAbsent(
                    row.getProjectId(), projectLookupHelper::resolveProjectName);
            entries.add(AttendanceCalendarEntry.builder()
                    .date(row.getDate())
                    .projectId(row.getProjectId())
                    .projectName(projectName)
                    .workerCount(row.getWorkerCount())
                    .build());
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getById(Long attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance", attendanceId));
        return attendanceMapper.toResponse(attendance);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> search(Long workerId, Long projectId,
                                                     LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Page<Attendance> page = attendanceRepository.search(workerId, projectId, dateFrom, dateTo, pageable);
        return PageResponse.of(page, attendanceMapper::toResponse);
    }

    private ProjectExpenseCreateRequest buildExpenseRequest(Worker worker, Attendance attendance) {
        String description = worker.getName() + " - " + attendance.getDaysPresent().stripTrailingZeros().toPlainString()
                + "d on " + attendance.getAttendanceDate();
        return ProjectExpenseCreateRequest.builder()
                .category(ExpenseCategory.LABOR)
                .description(description)
                .amount(attendance.getRateSnapshot().multiply(attendance.getDaysPresent()))
                .expenseDate(attendance.getAttendanceDate())
                .build();
    }
}
