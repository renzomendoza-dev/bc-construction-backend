package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBatchResponse {

    @Schema(description = "Attendance records actually created (and their auto-generated LABOR expenses)")
    private List<AttendanceResponse> created;

    @Schema(description = "Entries skipped because that worker already had a record for this date")
    private List<AttendanceBatchSkippedEntry> skipped;
}
