package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.workers.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * Defense-in-depth check ahead of the uq_attendance_worker_date DB
     * constraint, so a duplicate attempt gets a clean DuplicateAttendanceException
     * (409) rather than a raw DataIntegrityViolationException.
     */
    boolean existsByWorkerIdAndAttendanceDate(Long workerId, LocalDate attendanceDate);

    /**
     * Filters attendance records by optional worker, project, and date range.
     */
    @Query("""
            SELECT a FROM Attendance a
            WHERE (:workerId IS NULL OR a.worker.id = :workerId)
              AND (:projectId IS NULL OR a.projectId = :projectId)
              AND (:dateFrom IS NULL OR a.attendanceDate >= :dateFrom)
              AND (:dateTo IS NULL OR a.attendanceDate <= :dateTo)
            """)
    Page<Attendance> search(@Param("workerId") Long workerId,
                             @Param("projectId") Long projectId,
                             @Param("dateFrom") LocalDate dateFrom,
                             @Param("dateTo") LocalDate dateTo,
                             Pageable pageable);
}
