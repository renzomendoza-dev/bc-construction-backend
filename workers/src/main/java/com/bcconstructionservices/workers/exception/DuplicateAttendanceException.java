package com.bcconstructionservices.workers.exception;

import lombok.Getter;

import java.time.LocalDate;

/**
 * Thrown when creating an Attendance record would violate the one-record-
 * per-worker-per-day rule (uq_attendance_worker_date). A genuine conflict
 * discovered at operation time, not a lifecycle-lock issue, so this maps to
 * HTTP 409 — matching InsufficientStockException's use of 409 in inventory
 * for the same kind of "conflict, not wrong state" distinction (see the
 * HTTP status code conventions in the repo-root CLAUDE.md).
 */
@Getter
public class DuplicateAttendanceException extends RuntimeException {

    private final Long workerId;
    private final LocalDate attendanceDate;

    public DuplicateAttendanceException(Long workerId, LocalDate attendanceDate) {
        super("Worker " + workerId + " already has an attendance record for " + attendanceDate);
        this.workerId = workerId;
        this.attendanceDate = attendanceDate;
    }
}
