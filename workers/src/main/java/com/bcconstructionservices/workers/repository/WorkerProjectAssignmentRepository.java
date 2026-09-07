package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerProjectAssignmentRepository extends JpaRepository<WorkerProjectAssignment, Long> {

    /**
     * Defense-in-depth check ahead of the uq_worker_project_assignment_active_worker
     * DB constraint, so a duplicate attempt gets a clean DuplicateActiveAssignmentException
     * (409) rather than a raw DataIntegrityViolationException.
     */
    boolean existsByWorkerIdAndActiveTrue(Long workerId);

    /**
     * Filters assignments by optional project and active flag.
     */
    @Query("""
            SELECT wpa FROM WorkerProjectAssignment wpa
            WHERE (:projectId IS NULL OR wpa.projectId = :projectId)
              AND (:active IS NULL OR wpa.active = :active)
            """)
    Page<WorkerProjectAssignment> search(@Param("projectId") Long projectId,
                                          @Param("active") Boolean active,
                                          Pageable pageable);
}
