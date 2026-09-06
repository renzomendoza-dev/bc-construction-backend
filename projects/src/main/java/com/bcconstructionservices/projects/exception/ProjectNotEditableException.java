package com.bcconstructionservices.projects.exception;

import com.bcconstructionservices.projects.entity.ProjectStatus;
import lombok.Getter;

/**
 * Thrown when updating a project, completing it, or recording an expense
 * against it is attempted while its status is already COMPLETED or
 * CANCELLED — both are terminal; ACTIVE and ON_HOLD are the only
 * editable/expense-postable states. Maps to HTTP 422, matching inventory's
 * established "already progressed past a one-way state transition"
 * convention (see MaterialRequestNotEditableException,
 * PurchaseOrderNotEditableException) — the closest analogous domain in this
 * codebase, and there's no reason to invent a different convention for a
 * new module doing the same kind of lifecycle check.
 */
@Getter
public class ProjectNotEditableException extends RuntimeException {

    private final Long projectId;
    private final ProjectStatus status;

    public ProjectNotEditableException(Long projectId, ProjectStatus status) {
        super("Project " + projectId + " can no longer be edited (status: " + status
                + "); only ACTIVE/ON_HOLD projects can be changed or have expenses recorded against them");
        this.projectId = projectId;
        this.status = status;
    }
}
