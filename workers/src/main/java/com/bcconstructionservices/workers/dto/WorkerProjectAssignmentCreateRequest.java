package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload to assign a worker to a project's crew")
public class WorkerProjectAssignmentCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the worker being assigned", example = "1")
    private Long workerId;

    @NotNull
    @Schema(description = "Identifier of the project this worker is being assigned to", example = "12")
    private Long projectId;
}
