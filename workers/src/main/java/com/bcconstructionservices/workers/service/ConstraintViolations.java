package com.bcconstructionservices.workers.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifies which named DB constraint a failed insert violated, so a race
 * past an application-level pre-check can be mapped to the same 4xx the
 * pre-check returns, without mislabeling unrelated integrity errors.
 */
final class ConstraintViolations {

    private ConstraintViolations() {
    }

    static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
