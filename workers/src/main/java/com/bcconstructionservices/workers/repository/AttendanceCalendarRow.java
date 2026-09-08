package com.bcconstructionservices.workers.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * JPQL constructor-expression projection for AttendanceRepository#calendarSummary
 * — a plain class (the query uses {@code SELECT new ...AttendanceCalendarRow(...)}),
 * not a Spring Data interface projection. Was originally an interface;
 * changed after a real 500 on the live dev server calling
 * GET /api/attendance/calendar, not reproduced by this module's own
 * @DataJpaTest against H2 — interface projections combined with GROUP BY +
 * an aggregate (COUNT(DISTINCT ...)) are a known rough spot in some
 * Hibernate versions, and AttendanceService.getCalendar's loop is exactly
 * the shape that can trip it: firing a second query (ProjectLookupHelper)
 * per row while iterating projection-proxy results, which no existing test
 * happened to exercise together (see that method's own javadoc). A
 * constructor expression materializes a real object up front — no proxy,
 * no lingering tie to the persistence context — sidestepping the whole
 * class of issue regardless of the exact mechanism.
 */
@Getter
@AllArgsConstructor
public class AttendanceCalendarRow {

    private final LocalDate date;
    private final Long projectId;
    private final long workerCount;
}
