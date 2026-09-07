package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBatchSkippedEntry {

    @Schema(description = "Identifier of the worker whose entry was skipped", example = "1")
    private Long workerId;

    @Schema(description = "Why this entry was skipped", example = "Attendance already recorded for this date")
    private String reason;
}
