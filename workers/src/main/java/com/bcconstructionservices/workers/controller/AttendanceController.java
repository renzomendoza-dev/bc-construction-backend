package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.dto.ErrorResponse;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.exception.ValidationErrorResponse;
import com.bcconstructionservices.workers.service.AttendanceService;
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
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/**
 * REST endpoints for daily attendance. Recording an entry auto-creates a
 * LABOR ProjectExpense on the referenced project — see AttendanceService.
 */
@RestController
@RequestMapping(value = "/api/attendance", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Daily worker attendance, auto-generating a LABOR project expense per record")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    @Operation(
            summary = "Record one worker's attendance for one day",
            description = "At most one record per worker per day (409 on a duplicate). Automatically creates a "
                    + "LABOR ProjectExpense on the referenced project (amount = the worker's current dailyRate * "
                    + "daysPresent) — 422 if that project is COMPLETED/CANCELLED, same lock rule as recording an "
                    + "expense manually. 400 if the worker is inactive."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Attendance recorded and expense created",
                    content = @Content(schema = @Schema(implementation = AttendanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, or the worker is inactive",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Worker or project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "This worker already has an attendance record for this date",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The referenced project is COMPLETED/CANCELLED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ATTENDANCE_CREATE')")
    public ResponseEntity<AttendanceResponse> create(@Valid @RequestBody AttendanceCreateRequest request) {
        AttendanceResponse response = attendanceService.createAttendance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an attendance record",
            description = "Also deletes the LABOR ProjectExpense this record generated, if any — the only fix "
                    + "path for a mis-entered record (no edit endpoint). 422 if the referenced project is "
                    + "COMPLETED/CANCELLED, same lock rule as deleting an expense manually."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Attendance (and its generated expense, if any) deleted"),
            @ApiResponse(responseCode = "404", description = "Attendance record not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The referenced project is COMPLETED/CANCELLED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ATTENDANCE_DELETE')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifier of the attendance record to delete", example = "501")
            @PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an attendance record by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attendance record found",
                    content = @Content(schema = @Schema(implementation = AttendanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Attendance record not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AttendanceResponse> getById(
            @Parameter(description = "Identifier of the attendance record", example = "501")
            @PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getById(id));
    }

    @GetMapping
    @Operation(
            summary = "List attendance records",
            description = "Returns a paged list of attendance records, optionally filtered by worker, project, "
                    + "and/or date range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of attendance records",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<AttendanceResponse>> search(
            @Parameter(description = "Filter by worker", example = "1")
            @RequestParam(required = false) Long workerId,
            @Parameter(description = "Filter by project", example = "12")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "Filter to attendanceDate >= this date", example = "2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter to attendanceDate <= this date", example = "2026-09-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(attendanceService.search(workerId, projectId, dateFrom, dateTo, pageable));
    }
}
