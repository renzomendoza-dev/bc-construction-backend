package com.bcconstructionservices.workers.repository;

import java.time.LocalDate;

/**
 * Spring Data interface projection for AttendanceRepository#calendarSummary —
 * getter names match the JPQL SELECT aliases (date/projectId/workerCount).
 */
public interface AttendanceCalendarRow {

    LocalDate getDate();

    Long getProjectId();

    long getWorkerCount();
}
