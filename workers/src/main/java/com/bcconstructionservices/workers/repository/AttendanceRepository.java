package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.workers.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * One row per (date, project) with any recorded attendance in range —
     * feeds GET /api/attendance/calendar. Reflects only what's actually been
     * recorded, deliberately not blended with WorkerProjectAssignment's
     * assigned-crew size (see AttendanceCalendarEntry's own javadoc).
     */
    @Query("""
            SELECT a.attendanceDate AS date, a.projectId AS projectId, COUNT(DISTINCT a.worker.id) AS workerCount
            FROM Attendance a
            WHERE (:projectId IS NULL OR a.projectId = :projectId)
              AND (:dateFrom IS NULL OR a.attendanceDate >= :dateFrom)
              AND (:dateTo IS NULL OR a.attendanceDate <= :dateTo)
            GROUP BY a.attendanceDate, a.projectId
            ORDER BY a.attendanceDate ASC
            """)
    List<AttendanceCalendarRow> calendarSummary(@Param("projectId") Long projectId,
                                                 @Param("dateFrom") LocalDate dateFrom,
                                                 @Param("dateTo") LocalDate dateTo);
}
