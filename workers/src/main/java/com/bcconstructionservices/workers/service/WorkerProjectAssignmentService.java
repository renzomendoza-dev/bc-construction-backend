package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.service.ProjectService;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import com.bcconstructionservices.workers.exception.DuplicateActiveAssignmentException;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.mapper.WorkerProjectAssignmentMapper;
import com.bcconstructionservices.workers.repository.WorkerProjectAssignmentRepository;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks which project a worker's crew currently belongs to — purely roster
 * data for the attendance calendar/batch form; AttendanceService never
 * consults this before recording attendance. projectService.getById is
 * called only to validate the referenced project exists (404 otherwise) —
 * reusing projects' own service rather than injecting its repository
 * directly, matching the established cross-module pattern even though this
 * particular call is a read, not a write.
 */
@Service
@RequiredArgsConstructor
public class WorkerProjectAssignmentService {

    private final WorkerProjectAssignmentRepository workerProjectAssignmentRepository;
    private final WorkerRepository workerRepository;
    private final ProjectService projectService;
    private final WorkerProjectAssignmentMapper workerProjectAssignmentMapper;

    /**
     * 409 if the worker already has an active assignment — matches
     * EquipmentService.checkOut's precedent (reject rather than silently
     * transfer). Deactivate the existing assignment first, then reassign.
     */
    @Transactional
    public WorkerProjectAssignmentResponse assign(WorkerProjectAssignmentCreateRequest request) {
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker", request.getWorkerId()));

        projectService.getById(request.getProjectId()); // 404 if missing; response itself unused

        if (workerProjectAssignmentRepository.existsByWorkerIdAndActiveTrue(worker.getId())) {
            throw new DuplicateActiveAssignmentException(worker.getId());
        }

        WorkerProjectAssignment assignment = WorkerProjectAssignment.builder()
                .worker(worker)
                .projectId(request.getProjectId())
                .build();

        WorkerProjectAssignment saved = workerProjectAssignmentRepository.save(assignment);
        return workerProjectAssignmentMapper.toResponse(saved);
    }

    /**
     * Idempotent — matches WorkerService.deactivateWorker's convention.
     */
    @Transactional
    public void deactivate(Long assignmentId) {
        WorkerProjectAssignment assignment = workerProjectAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerProjectAssignment", assignmentId));
        assignment.setActive(false);
        workerProjectAssignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public WorkerProjectAssignmentResponse getById(Long assignmentId) {
        WorkerProjectAssignment assignment = workerProjectAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkerProjectAssignment", assignmentId));
        return workerProjectAssignmentMapper.toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkerProjectAssignmentResponse> search(Long projectId, Boolean active, Pageable pageable) {
        Page<WorkerProjectAssignment> page = workerProjectAssignmentRepository.search(projectId, active, pageable);
        return PageResponse.of(page, workerProjectAssignmentMapper::toResponse);
    }
}
