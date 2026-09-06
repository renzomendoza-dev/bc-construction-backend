package com.bcconstructionservices.projects.controller;

import com.bcconstructionservices.projects.dto.ErrorResponse;
import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.dto.ProjectUpdateRequest;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.ValidationErrorResponse;
import com.bcconstructionservices.projects.service.ProjectService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for projects — the aggregation root whose running expense
 * is tracked via ProjectExpenseController.
 */
@RestController
@RequestMapping(value = "/api/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Construction projects and their running expense")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(
            summary = "Create a project",
            description = "Records a new project, starting at status ACTIVE. code must be unique (409 if already "
                    + "in use)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "code is already in use by another project",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PROJECT_CREATE')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a project's editable fields",
            description = "Full-replacement update, only while ACTIVE/ON_HOLD (422 otherwise). code is immutable "
                    + "and not part of this request body. description/budget/endDate are copied as given, "
                    + "including null (clearing the field)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The project is COMPLETED/CANCELLED and can no "
                    + "longer be edited",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PROJECT_EDIT')")
    public ResponseEntity<ProjectResponse> update(
            @Parameter(description = "Identifier of the project to update", example = "12")
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @PostMapping("/{id}/complete")
    @Operation(
            summary = "Mark a project COMPLETED",
            description = "ACTIVE/ON_HOLD -> COMPLETED, only while ACTIVE/ON_HOLD (422 otherwise). Terminal — no "
                    + "further edits or expenses can be recorded against this project afterward."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project marked COMPLETED",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The project is already COMPLETED/CANCELLED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PROJECT_COMPLETE')")
    public ResponseEntity<ProjectResponse> complete(
            @Parameter(description = "Identifier of the project to complete", example = "12")
            @PathVariable Long id) {
        return ResponseEntity.ok(projectService.completeProject(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProjectResponse> getById(
            @Parameter(description = "Identifier of the project to retrieve", example = "12")
            @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List projects", description = "Returns a paged list of projects, optionally filtered by status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of projects",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<ProjectResponse>> search(
            @Parameter(description = "Filter by status", example = "ACTIVE")
            @RequestParam(required = false) ProjectStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(projectService.search(status, pageable));
    }
}
