package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResponse {

    @Schema(description = "Unique identifier of the worker", example = "1")
    private Long id;

    @Schema(description = "Worker's full name", example = "Ramon Villanueva")
    private String name;

    @Schema(description = "Optional role/position", example = "Mason")
    private String position;

    @Schema(description = "Current daily rate in PHP", example = "800.00")
    private BigDecimal dailyRate;

    @Schema(description = "Whether this worker is still active on the roster", example = "true")
    private boolean active;

    @Schema(description = "ID of the user who added this worker", example = "1")
    private Long createdBy;

    @Schema(description = "Full name of the user who added this worker", example = "Renzo Mendoza")
    private String createdByName;

    @Schema(description = "Timestamp when this worker was added", example = "2026-09-01T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when this worker was last updated", example = "2026-09-05T14:05:00Z")
    private Instant updatedAt;
}
