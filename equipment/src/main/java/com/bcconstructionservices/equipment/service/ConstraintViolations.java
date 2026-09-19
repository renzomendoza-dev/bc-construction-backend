package com.bcconstructionservices.equipment.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifies which named DB constraint a failed insert violated, so a race
 * past an application-level pre-check can be mapped to the same 4xx the
 * pre-check returns, without mislabeling unrelated integrity errors. Each
 * module keeps its own copy (see CLAUDE.md's uniqueness convention).
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    public static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
