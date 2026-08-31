package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import lombok.Getter;

/**
 * Thrown when attempting to update a MaterialRequest whose status is already
 * PARTIALLY_FULFILLED or FULFILLED — i.e. at least one submitted TransferBatch
 * has already moved real stock against it. A DRAFT TransferBatch that merely
 * references the request via sourceMaterialRequestId does NOT trigger this;
 * only the request's own status (set by TransferBatchService.submit) does.
 * Maps to HTTP 422 Unprocessable Entity, matching this codebase's existing
 * "already progressed past a one-way state transition" convention (see
 * ReceiptProcessingException, used for confirmPurchaseReceipt's analogous
 * "already confirmed" guard).
 */
@Getter
public class MaterialRequestNotEditableException extends RuntimeException {

    private final Long materialRequestId;
    private final MaterialRequestStatus status;

    public MaterialRequestNotEditableException(Long materialRequestId, MaterialRequestStatus status) {
        super("Material request " + materialRequestId + " can no longer be edited (status: " + status
                + "); it has already been at least partially fulfilled by a submitted transfer batch");
        this.materialRequestId = materialRequestId;
        this.status = status;
    }
}
