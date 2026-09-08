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
     *
     * The bare "(:param IS NULL OR ...)" idiom needs an explicit CAST on the
     * IS NULL side on Postgres: the extended query protocol resolves each
     * bind parameter's type at PARSE time, from surrounding syntax alone (not
     * from the actual bound value) — a parameter used only in "$n IS NULL"
     * gives Postgres nothing to infer a type from, and once the sibling
     * comparison operator is overloaded across multiple types (as >=/<= are
     * for date/timestamp/timestamptz), Postgres can't resolve it either and
     * throws PSQLException: "could not determine data type of parameter $n"
     * instead of guessing. H2 has no equivalent restriction, which is why
     * this module's own @DataJpaTest suite never caught it — see
     * calendarSummary below for the real 500 this caused on the live server.
     */
    @Query("""
            SELECT a FROM Attendance a
            WHERE (CAST(:workerId AS Long) IS NULL OR a.worker.id = :workerId)
              AND (CAST(:projectId AS Long) IS NULL OR a.projectId = :projectId)
              AND (CAST(:dateFrom AS LocalDate) IS NULL OR a.attendanceDate >= :dateFrom)
              AND (CAST(:dateTo AS LocalDate) IS NULL OR a.attendanceDate <= :dateTo)
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
     *
     * Real 500 on the live dev server, reproduced from the actual server
     * stack trace (not guessed): org.postgresql.util.PSQLException: "could
     * not determine data type of parameter $3". Root cause is the bare
     * "(:param IS NULL OR ...)" idiom — see search()'s javadoc above for the
     * full mechanism; the explicit CAST below is the fix, giving Postgres a
     * literal type from the SQL text instead of needing to infer one.
     *
     * (An earlier fix attempt in this same query — switching
     * AttendanceCalendarRow from a Spring Data interface projection to a
     * JPQL constructor expression — was a real, worthwhile change on its own
     * merits, but targeted a different, unconfirmed hypothesis and did not
     * fix this actual failure.)
     */
    @Query("""
            SELECT new com.bcconstructionservices.workers.repository.AttendanceCalendarRow(
                a.attendanceDate, a.projectId, COUNT(DISTINCT a.worker.id))
            FROM Attendance a
            WHERE (CAST(:projectId AS Long) IS NULL OR a.projectId = :projectId)
              AND (CAST(:dateFrom AS LocalDate) IS NULL OR a.attendanceDate >= :dateFrom)
              AND (CAST(:dateTo AS LocalDate) IS NULL OR a.attendanceDate <= :dateTo)
            GROUP BY a.attendanceDate, a.projectId
            ORDER BY a.attendanceDate ASC
            """)
    List<AttendanceCalendarRow> calendarSummary(@Param("projectId") Long projectId,
                                                 @Param("dateFrom") LocalDate dateFrom,
                                                 @Param("dateTo") LocalDate dateTo);
}
