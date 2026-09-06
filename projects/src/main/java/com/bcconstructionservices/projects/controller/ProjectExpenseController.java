package com.bcconstructionservices.projects.controller;

import com.bcconstructionservices.projects.dto.ErrorResponse;
import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.dto.ProjectSummaryResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.exception.ValidationErrorResponse;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for a project's individual expense records and their
 * aggregated summary.
 */
@RestController
@RequestMapping(value = "/api/projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Project Expenses", description = "Labor/material/other costs recorded against a project")
public class ProjectExpenseController {

    private final ProjectExpenseService projectExpenseService;

    @PostMapping("/expenses")
    @Operation(
            summary = "Record an expense against a project",
            description = "Only while the project is ACTIVE/ON_HOLD (422 otherwise) — same lock condition as "
                    + "PUT /api/projects/{id}."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Expense recorded",
                    content = @Content(schema = @Schema(implementation = ProjectExpenseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The project is COMPLETED/CANCELLED and can no "
                    + "longer accept expenses",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PROJECT_EXPENSE_CREATE')")
    public ResponseEntity<ProjectExpenseResponse> create(
            @Parameter(description = "Identifier of the project to record the expense against", example = "12")
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectExpenseCreateRequest request) {
        ProjectExpenseResponse response = projectExpenseService.addExpense(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @Operation(
            summary = "Delete an expense from a project",
            description = "Only while the project is ACTIVE/ON_HOLD (422 otherwise) — same lock condition as "
                    + "creating an expense. Also used internally by the workers module to remove the expense an "
                    + "Attendance record generated, when that Attendance is deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Expense deleted"),
            @ApiResponse(responseCode = "404", description = "Project or expense not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The project is COMPLETED/CANCELLED and can no "
                    + "longer have expenses removed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('PROJECT_EXPENSE_DELETE')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifier of the project", example = "12")
            @PathVariable Long projectId,
            @Parameter(description = "Identifier of the expense to delete", example = "301")
            @PathVariable Long expenseId) {
        projectExpenseService.deleteExpense(projectId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses")
    @Operation(
            summary = "List a project's expenses",
            description = "Returns a paged list of expenses recorded against this project, optionally filtered "
                    + "by category."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of expenses",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ProjectExpenseResponse>> search(
            @Parameter(description = "Identifier of the project", example = "12")
            @PathVariable Long projectId,
            @Parameter(description = "Filter by category", example = "MATERIAL")
            @RequestParam(required = false) ExpenseCategory category,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(projectExpenseService.search(projectId, category, pageable));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get a project's aggregated running expense",
            description = "Sums every expense recorded so far, grouped by category (LABOR/MATERIAL/OTHER) plus a "
                    + "grand total, and budgetRemaining if the project has a budget set. Always computed fresh "
                    + "from every expense row, not an incrementally-maintained running total."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggregated expense summary",
                    content = @Content(schema = @Schema(implementation = ProjectSummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProjectSummaryResponse> getSummary(
            @Parameter(description = "Identifier of the project", example = "12")
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectExpenseService.getSummary(projectId));
    }
}
