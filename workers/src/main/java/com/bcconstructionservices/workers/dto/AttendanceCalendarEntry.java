package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One (date, project) pair with recorded attendance, for calendar rendering.
 * Reflects only what's actually been recorded — GET /api/attendance/calendar
 * deliberately doesn't blend in assigned-but-not-yet-recorded crew size; that
 * can be derived client-side from GET /api/worker-assignments if needed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCalendarEntry {

    @Schema(description = "The date this entry summarizes", example = "2026-09-01")
    private LocalDate date;

    @Schema(description = "Identifier of the project with recorded attendance on this date", example = "12")
    private Long projectId;

    @Schema(description = "Name of the project with recorded attendance on this date", example = "Sta. Maria Warehouse Expansion")
    private String projectName;

    @Schema(description = "Number of distinct workers with an attendance record for this project on this date", example = "6")
    private long workerCount;
}
