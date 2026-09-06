package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.ErrorResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.exception.ValidationErrorResponse;
import com.bcconstructionservices.workers.service.WorkerService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the field-labor roster. Attendance is exposed
 * separately by AttendanceController.
 */
@RestController
@RequestMapping(value = "/api/workers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Workers", description = "Field-labor roster, independent of AppUser/Keycloak logins")
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    @Operation(summary = "Add a worker to the roster")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Worker created",
                    content = @Content(schema = @Schema(implementation = WorkerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class)))
    })
    @PreAuthorize("hasRole('WORKER_CREATE')")
    public ResponseEntity<WorkerResponse> create(@Valid @RequestBody WorkerCreateRequest request) {
        WorkerResponse response = workerService.createWorker(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a worker's editable fields",
            description = "Full-replacement update of name/position/dailyRate. Allowed regardless of the active "
                    + "flag; active is only ever changed via PATCH /{id}/deactivate."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Worker updated",
                    content = @Content(schema = @Schema(implementation = WorkerResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Worker not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('WORKER_EDIT')")
    public ResponseEntity<WorkerResponse> update(
            @Parameter(description = "Identifier of the worker to update", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody WorkerUpdateRequest request) {
        return ResponseEntity.ok(workerService.updateWorker(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate a worker",
            description = "Soft-retires a worker by setting active to false. The worker and its attendance "
                    + "history are preserved, not deleted — workers are never hard-deletable."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Worker deactivated"),
            @ApiResponse(responseCode = "404", description = "Worker not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('WORKER_DEACTIVATE')")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Identifier of the worker to deactivate", example = "1")
            @PathVariable Long id) {
        workerService.deactivateWorker(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a worker by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Worker found",
                    content = @Content(schema = @Schema(implementation = WorkerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Worker not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<WorkerResponse> getById(
            @Parameter(description = "Identifier of the worker to retrieve", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(workerService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List workers", description = "Returns a paged list of workers, optionally filtered by active.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of workers",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<WorkerResponse>> search(
            @Parameter(description = "Filter by active flag", example = "true")
            @RequestParam(required = false) Boolean active,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(workerService.search(active, pageable));
    }
}
