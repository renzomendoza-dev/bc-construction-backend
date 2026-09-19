package com.bcconstructionservices.workers.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/**
 * JPQL constructor-expression projection for AttendanceRepository#calendarSummary
 * — a plain class (the query uses {@code SELECT new ...AttendanceCalendarRow(...)}),
 * not a Spring Data interface projection, so each row is a fully materialized
 * object with no proxy. (It was switched from an interface while chasing a
 * live 500 on GET /api/attendance/calendar; that 500 turned out to be the
 * unrelated missing-CAST bug — see CLAUDE.md's "Optional-filter queries".)
 */
@Getter
@AllArgsConstructor
public class AttendanceCalendarRow {

    private final LocalDate date;
    private final Long projectId;
    private final long workerCount;
}
