package com.bcconstructionservices.equipment.exception;

import com.bcconstructionservices.equipment.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Central exception handler for the equipment tracking API.
 * Translates domain exceptions into a consistent ErrorResponse body,
 * following the same shape as the inventory module's GlobalExceptionHandler.
 *
 * Scoped to the equipment controller package (rather than a bare
 * @RestControllerAdvice) so its catch-all Exception handler only applies to
 * equipment's own controllers. An unscoped advice applies to every
 * controller in the full app context once all modules are wired together,
 * which would make it ambiguous with inventory's own unscoped catch-all
 * for exceptions thrown by inventory's controllers.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bcconstructionservices.equipment.controller")
public class EquipmentExceptionHandler {

    @ExceptionHandler(EquipmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEquipmentNotFound(
            EquipmentNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidEquipmentStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEquipmentStatus(
            InvalidEquipmentStatusException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(NoOpenAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleNoOpenAssignment(
            NoOpenAssignmentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateAssetTagException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAssetTag(
            DuplicateAssetTagException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCheckoutUserException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCheckoutUser(
            InvalidCheckoutUserException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
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
     * Triggered by @Valid failures on request DTOs.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields", request);
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
