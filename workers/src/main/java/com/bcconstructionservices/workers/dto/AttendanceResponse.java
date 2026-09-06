package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    @Schema(description = "Unique identifier of the attendance record", example = "501")
    private Long id;

    @Schema(description = "Identifier of the worker", example = "1")
    private Long workerId;

    @Schema(description = "Full name of the worker", example = "Ramon Villanueva")
    private String workerName;

    @Schema(description = "Identifier of the project this labor is attributed to", example = "12")
    private Long projectId;

    @Schema(description = "Name of the project this labor is attributed to", example = "Sta. Maria Warehouse Expansion")
    private String projectName;

    @Schema(description = "Date this attendance covers", example = "2026-09-05")
    private LocalDate attendanceDate;

    @Schema(description = "Portion of a day present", example = "1.0")
    private BigDecimal daysPresent;

    @Schema(description = "Worker's daily rate at the time this record was created", example = "800.00")
    private BigDecimal rateSnapshot;

    @Schema(description = "rateSnapshot * daysPresent — matches the amount of the auto-generated ProjectExpense",
            example = "800.00")
    private BigDecimal amount;

    @Schema(description = "Optional notes", example = "Rebar tying, Building A")
    private String notes;

    @Schema(description = "Identifier of the LABOR ProjectExpense this attendance record generated", example = "305")
    private Long projectExpenseId;

    @Schema(description = "ID of the user who recorded this attendance", example = "1")
    private Long recordedBy;

    @Schema(description = "Full name of the user who recorded this attendance", example = "Renzo Mendoza")
    private String recordedByName;

    @Schema(description = "Timestamp when this attendance record was created", example = "2026-09-05T09:15:30Z")
    private Instant createdAt;
}
