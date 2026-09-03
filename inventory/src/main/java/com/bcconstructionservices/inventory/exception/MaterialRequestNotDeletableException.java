package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import lombok.Getter;

/**
 * Thrown when attempting to delete a MaterialRequest whose status is already
 * PARTIALLY_FULFILLED or FULFILLED — the same lock condition as
 * {@link MaterialRequestNotEditableException}, since a request that has had
 * at least one submitted TransferBatch move real stock against it is no
 * longer safe to remove. A request that's merely SUBMITTED (its actual
 * initial persisted state — MaterialRequestService.create() never leaves one
 * at DRAFT) can still be deleted, same as it can still be edited. Maps to
 * HTTP 422 Unprocessable Entity, matching this module's "already progressed
 * past a one-way state transition" convention (see
 * TransferBatchNotDeletableException, MaterialRequestNotEditableException).
 */
@Getter
public class MaterialRequestNotDeletableException extends RuntimeException {

    private final Long materialRequestId;
    private final MaterialRequestStatus status;

    public MaterialRequestNotDeletableException(Long materialRequestId, MaterialRequestStatus status) {
        super("Material request " + materialRequestId + " cannot be deleted (status: " + status
                + "); it has already been at least partially fulfilled by a submitted transfer batch");
        this.materialRequestId = materialRequestId;
        this.status = status;
    }
}
