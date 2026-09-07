package com.bcconstructionservices.workers.exception;

import lombok.Getter;

/**
 * Thrown when assigning a worker to a project would violate the
 * one-active-assignment-per-worker rule (uq_worker_project_assignment_active_worker).
 * A genuine conflict discovered at operation time, not a lifecycle-lock
 * issue, so this maps to HTTP 409 — same reasoning as DuplicateAttendanceException.
 * Deactivate the existing assignment first (PATCH /{id}/deactivate), then
 * reassign — matching EquipmentService.checkOut's precedent of rejecting an
 * already-checked-out item rather than silently transferring it.
 */
@Getter
public class DuplicateActiveAssignmentException extends RuntimeException {

    private final Long workerId;

    public DuplicateActiveAssignmentException(Long workerId) {
        super("Worker " + workerId + " already has an active project assignment; deactivate it first");
        this.workerId = workerId;
    }
}
