package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.ErrorResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.exception.ValidationErrorResponse;
import com.bcconstructionservices.workers.service.WorkerProjectAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for which project a worker's crew currently belongs to —
 * feeds the attendance calendar/batch form's crew list. Purely roster data;
 * AttendanceController's endpoints don't consult this.
 */
@RestController
@RequestMapping(value = "/api/worker-assignments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Worker Assignments", description = "Which project a worker's crew currently belongs to")
public class WorkerProjectAssignmentController {

    private final WorkerProjectAssignmentService workerProjectAssignmentService;

    @PostMapping
    @Operation(
            summary = "Assign a worker to a project",
            description = "409 if the worker already has an active assignment — deactivate it first "
                    + "(PATCH /{id}/deactivate), then reassign; this endpoint never silently transfers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Assignment created",
                    content = @Content(schema = @Schema(implementation = WorkerProjectAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Worker or project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This worker already has an active assignment",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('WORKER_ASSIGNMENT_CREATE')")
    public ResponseEntity<WorkerProjectAssignmentResponse> create(
            @Valid @RequestBody WorkerProjectAssignmentCreateRequest request) {
        WorkerProjectAssignmentResponse response = workerProjectAssignmentService.assign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(
            summary = "End a worker's project assignment",
            description = "Idempotent — sets active to false. History is preserved, not deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Assignment deactivated"),
            @ApiResponse(responseCode = "404", description = "Assignment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('WORKER_ASSIGNMENT_DEACTIVATE')")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Identifier of the assignment to deactivate", example = "1")
            @PathVariable Long id) {
        workerProjectAssignmentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an assignment by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignment found",
                    content = @Content(schema = @Schema(implementation = WorkerProjectAssignmentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Assignment not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkerProjectAssignmentResponse> getById(
            @Parameter(description = "Identifier of the assignment", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(workerProjectAssignmentService.getById(id));
    }

    @GetMapping
    @Operation(
            summary = "List worker-project assignments",
            description = "Returns a paged list of assignments, optionally filtered by project and/or active flag. "
                    + "GET ?projectId=X&active=true is how the attendance batch form fetches a project's crew."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of assignments",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<WorkerProjectAssignmentResponse>> search(
            @Parameter(description = "Filter by project", example = "12")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "Filter by active flag", example = "true")
            @RequestParam(required = false) Boolean active,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(workerProjectAssignmentService.search(projectId, active, pageable));
    }
}
