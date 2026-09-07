package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.AttendanceBatchCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceBatchResponse;
import com.bcconstructionservices.workers.dto.AttendanceCalendarEntry;
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
import java.util.List;

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

    @PostMapping("/batch")
    @Operation(
            summary = "Record attendance for multiple workers on one project/day in a single batch",
            description = "Derives daysPresent per entry from timeIn/timeOut (capped at one standard 8-hour day) "
                    + "using the same dailyRate * daysPresent expense formula as the single-record endpoint. "
                    + "A worker who already has a record for this date is skipped (not an error) and reported in "
                    + "the response — revisiting an already-recorded day is a normal, expected use of the "
                    + "calendar. Any other failure (inactive worker, worker/project not found, timeOut not after "
                    + "timeIn, a duplicate workerId within the same request, or the project being "
                    + "COMPLETED/CANCELLED) aborts the whole batch — same all-or-nothing transaction as "
                    + "POST /api/inventory/transfer-batches/{id}/submit."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Batch processed — see the response for which "
                    + "entries were created vs. skipped",
                    content = @Content(schema = @Schema(implementation = AttendanceBatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation, an entry's timeOut "
                    + "isn't after its timeIn, a workerId appears more than once, or a worker is inactive",
                    content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "A worker or the project was not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "The project is COMPLETED/CANCELLED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ATTENDANCE_BATCH_CREATE')")
    public ResponseEntity<AttendanceBatchResponse> createBatch(
            @Valid @RequestBody AttendanceBatchCreateRequest request) {
        AttendanceBatchResponse response = attendanceService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/calendar")
    @Operation(
            summary = "Get a calendar summary of recorded attendance",
            description = "One entry per (date, project) with any recorded attendance in range, plus a distinct-worker "
                    + "count — shaped for calendar rendering. Reflects only what's actually been recorded, not "
                    + "assigned-but-not-yet-recorded crew size."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calendar entries",
                    content = @Content(schema = @Schema(implementation = AttendanceCalendarEntry.class)))
    })
    public ResponseEntity<List<AttendanceCalendarEntry>> calendar(
            @Parameter(description = "Filter by project", example = "12")
            @RequestParam(required = false) Long projectId,
            @Parameter(description = "Filter to date >= this date", example = "2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter to date <= this date", example = "2026-09-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(attendanceService.getCalendar(projectId, dateFrom, dateTo));
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
