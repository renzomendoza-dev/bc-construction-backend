package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.entity.Attendance;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.exception.DuplicateAttendanceException;
import com.bcconstructionservices.workers.exception.InactiveWorkerException;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.mapper.AttendanceMapper;
import com.bcconstructionservices.workers.repository.AttendanceRepository;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    private final AttendanceRepository attendanceRepository;
    private final WorkerRepository workerRepository;
    private final AttendanceMapper attendanceMapper;
    private final ProjectExpenseService projectExpenseService;

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
