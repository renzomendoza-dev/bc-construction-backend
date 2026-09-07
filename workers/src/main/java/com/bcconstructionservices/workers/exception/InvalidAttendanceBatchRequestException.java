package com.bcconstructionservices.workers.exception;

/**
 * Thrown for a structurally invalid AttendanceBatchCreateRequest — the same
 * workerId listed more than once, or an entry's timeOut not after its
 * timeIn. Maps to HTTP 400 Bad Request, matching InvalidEquipmentBatchRequestException's
 * "a batch's internal fields contradicting each other" bucket in the
 * repo-root CLAUDE.md.
 *
 * Callers are expected to supply a descriptive message that includes the
 * offending value(s), e.g.:
 *   new InvalidAttendanceBatchRequestException("Worker " + workerId + " appears more than once in this batch")
 */
public class InvalidAttendanceBatchRequestException extends RuntimeException {

    public InvalidAttendanceBatchRequestException(String message) {
        super(message);
    }
}
