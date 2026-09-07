package com.bcconstructionservices.workers.exception;

import com.bcconstructionservices.workers.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central exception handler for the workers API. Named
 * {@code WorkersExceptionHandler} (not the generic {@code GlobalExceptionHandler}
 * inventory uses) — see the bean-naming-uniqueness rule in the repo-root
 * CLAUDE.md, added after that exact collision happened once already between
 * inventory and projects.
 * <p>
 * Because {@code AttendanceService} calls {@code ProjectExpenseService}
 * directly (a real cross-module dependency, not just an id + LookupHelper —
 * see Attendance's own javadoc), exceptions owned by the projects module
 * (its own {@code ResourceNotFoundException}, {@code ProjectNotEditableException})
 * can legitimately bubble up through this module's own controllers. They're
 * handled explicitly here, mapped to the same status codes projects itself
 * uses for them, rather than falling through to the generic 500 fallback
 * below — this module's own {@code Exception.class} handler has no way to
 * know about a status code convention defined in a different module's
 * exception classes.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bcconstructionservices.workers.controller")
public class WorkersExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(com.bcconstructionservices.projects.exception.ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProjectsResourceNotFound(
            com.bcconstructionservices.projects.exception.ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(com.bcconstructionservices.projects.exception.ProjectNotEditableException.class)
    public ResponseEntity<ErrorResponse> handleProjectNotEditable(
            com.bcconstructionservices.projects.exception.ProjectNotEditableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateAttendanceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAttendance(
            DuplicateAttendanceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InactiveWorkerException.class)
    public ResponseEntity<ErrorResponse> handleInactiveWorker(
            InactiveWorkerException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateActiveAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateActiveAssignment(
            DuplicateActiveAssignmentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAttendanceBatchRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAttendanceBatchRequest(
            InvalidAttendanceBatchRequestException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Thrown by {@code @PreAuthorize} when an authenticated caller lacks the
     * required permission. Handled explicitly here — otherwise it would be
     * caught by the generic {@code Exception.class} fallback below and
     * returned as a 500, instead of the correct 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    /**
     * Triggered by @Valid failures on request DTOs. Returns a field-by-field
     * breakdown so clients can surface which inputs were invalid and why.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            );
        }

        ValidationErrorResponse body = ValidationErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed for one or more fields")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Triggered when the request body is missing, unparsable, or malformed JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed or unreadable JSON request body", request);
    }

    /**
     * Catch-all fallback for any exception not handled above. Logs the full
     * exception server-side for diagnostics, but never exposes stack traces
     * or internal details to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing [{} {}]",
                request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
