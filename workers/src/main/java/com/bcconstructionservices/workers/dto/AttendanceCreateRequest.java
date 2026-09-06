package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to record one worker's attendance for one day. Automatically creates a "
        + "LABOR expense (amount = the worker's current dailyRate * daysPresent) against the referenced project.")
public class AttendanceCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the worker who was present", example = "1")
    private Long workerId;

    @NotNull
    @Schema(description = "Identifier of the project this labor is attributed to", example = "12")
    private Long projectId;

    @NotNull
    @Schema(description = "Date this attendance covers", example = "2026-09-05")
    private LocalDate attendanceDate;

    @NotNull
    @Positive
    @Schema(description = "Portion of a day present (e.g. 0.5 for a half day, 1.0 for a full day)", example = "1.0")
    private BigDecimal daysPresent;

    @Size(max = 1000)
    @Schema(description = "Optional notes", example = "Rebar tying, Building A")
    private String notes;
}
