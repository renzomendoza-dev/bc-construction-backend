package com.bcconstructionservices.projects.repository;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByCode(String code);

    /**
     * Filters projects by optional status.
     */
    @Query("""
            SELECT p FROM Project p
            WHERE (:status IS NULL OR p.status = :status)
            """)
    Page<Project> search(@Param("status") ProjectStatus status, Pageable pageable);
}
