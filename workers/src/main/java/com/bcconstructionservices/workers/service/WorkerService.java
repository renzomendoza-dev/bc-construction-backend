package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.mapper.WorkerMapper;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerMapper workerMapper;

    @Transactional
    public WorkerResponse createWorker(WorkerCreateRequest request) {
        Worker worker = workerMapper.toEntity(request);
        Worker saved = workerRepository.save(worker);
        return workerMapper.toResponse(saved);
    }

    /**
     * Full-replacement update of name/position/dailyRate. Allowed regardless
     * of the active flag — there's no stated lock rule for editing a worker's
     * own record; active only ever changes via deactivateWorker.
     */
    @Transactional
    public WorkerResponse updateWorker(Long workerId, WorkerUpdateRequest request) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        workerMapper.updateEntityFromRequest(request, worker);

        Worker saved = workerRepository.save(worker);
        return workerMapper.toResponse(saved);
    }

    /**
     * Idempotent soft-retire — matches Item/Warehouse/Supplier's deactivate
     * convention. A worker with existing Attendance history is never
     * hard-deletable; this is the only removal path.
     */
    @Transactional
    public void deactivateWorker(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        worker.setActive(false);
        workerRepository.save(worker);
    }

    @Transactional(readOnly = true)
    public WorkerResponse getById(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        return workerMapper.toResponse(worker);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerResponse> search(Boolean active, Pageable pageable) {
        Page<Worker> page = workerRepository.search(active, pageable);
        return PageResponse.of(page, workerMapper::toResponse);
    }
}
