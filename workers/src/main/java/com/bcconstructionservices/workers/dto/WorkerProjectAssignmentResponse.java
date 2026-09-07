package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerProjectAssignmentResponse {

    @Schema(description = "Unique identifier of the assignment", example = "1")
    private Long id;

    @Schema(description = "Identifier of the assigned worker", example = "1")
    private Long workerId;

    @Schema(description = "Full name of the assigned worker", example = "Ramon Villanueva")
    private String workerName;

    @Schema(description = "Identifier of the project this worker is assigned to", example = "12")
    private Long projectId;

    @Schema(description = "Name of the project this worker is assigned to", example = "Sta. Maria Warehouse Expansion")
    private String projectName;

    @Schema(description = "Whether this assignment is still active", example = "true")
    private boolean active;

    @Schema(description = "ID of the user who created this assignment", example = "1")
    private Long createdBy;

    @Schema(description = "Full name of the user who created this assignment", example = "Renzo Mendoza")
    private String createdByName;

    @Schema(description = "Timestamp when this assignment was created", example = "2026-09-07T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when this assignment was last updated", example = "2026-09-07T09:15:30Z")
    private Instant updatedAt;
}
