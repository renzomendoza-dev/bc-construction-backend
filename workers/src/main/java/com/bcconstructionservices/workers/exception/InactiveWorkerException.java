package com.bcconstructionservices.workers.exception;

import lombok.Getter;

/**
 * Thrown when attendance is recorded against a worker whose {@code active}
 * flag is false (retired from the roster). Maps to HTTP 400, matching
 * inventory's InactiveResourceException — "the request references a real
 * resource, but it's the wrong kind/state of resource for this operation."
 */
@Getter
public class InactiveWorkerException extends RuntimeException {

    private final Long workerId;

    public InactiveWorkerException(Long workerId) {
        super("Worker " + workerId + " is inactive and can no longer have attendance recorded against it");
        this.workerId = workerId;
    }
}
