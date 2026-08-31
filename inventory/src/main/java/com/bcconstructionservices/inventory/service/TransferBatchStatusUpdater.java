package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.repository.TransferBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists TransferBatchStatus.AWAITING_PURCHASE independently of
 * TransferBatchService.submit's own transaction.
 *
 * <p>Why this needs its own bean/transaction rather than just being a private
 * method on TransferBatchService: by the time submit() catches
 * InsufficientStockException, InventoryService.transferStock's own
 * {@code @Transactional} advice has already marked the ambient transaction
 * rollback-only — it joined submit()'s transaction via the default REQUIRED
 * propagation, and Spring's interceptor calls setRollbackOnly() on a
 * participating (non-new) transaction before rethrowing. Any write attempted
 * in that same transaction after catching the exception would still be
 * discarded when it rolls back, no matter how submit() itself returns.
 * {@code REQUIRES_NEW} suspends that doomed transaction and commits this one
 * on its own connection, so AWAITING_PURCHASE survives even though every
 * stock transfer already applied earlier in submit()'s loop is rolled back.
 *
 * <p>This only works because submit() calls this through the Spring proxy
 * (a genuine separate bean) — self-invocation from within TransferBatchService
 * would bypass the AOP proxy and silently stay in the same (doomed) transaction.
 */
@Service
@RequiredArgsConstructor
public class TransferBatchStatusUpdater {

    private final TransferBatchRepository transferBatchRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAwaitingPurchase(Long transferBatchId) {
        transferBatchRepository.findById(transferBatchId).ifPresent(batch -> {
            batch.setStatus(TransferBatchStatus.AWAITING_PURCHASE);
            transferBatchRepository.save(batch);
        });
    }
}
