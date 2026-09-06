package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.workers.entity.Worker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    /**
     * Filters the roster by optional active flag.
     */
    @Query("""
            SELECT w FROM Worker w
            WHERE (:active IS NULL OR w.active = :active)
            """)
    Page<Worker> search(@Param("active") Boolean active, Pageable pageable);
}
