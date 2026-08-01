package com.bcconstructionservices.user.exception;

import com.bcconstructionservices.user.dto.ErrorResponse;
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
 * Central exception handler for the user module's admin API. Translates
 * domain exceptions into a consistent ErrorResponse body.
 * <p>
 * Scoped to the {@code controller} package (rather than a bare
 * {@code @RestControllerAdvice}) so its catch-all Exception handler only
 * applies to this module's own controllers — an unscoped advice applies to
 * every controller in the full app context once all modules are wired
 * together, which would make it ambiguous with other modules' own unscoped
 * catch-alls (this exact collision was hit and fixed earlier for the
 * equipment module).
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bcconstructionservices.user.controller")
public class AdminExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(KeycloakRoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakRoleNotFound(
            KeycloakRoleNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(KeycloakAdminApiException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakAdminApiException(
            KeycloakAdminApiException ex, HttpServletRequest request) {
        log.error("Keycloak Admin API call failed while processing [{} {}]",
                request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY,
                "Failed to reach the identity provider. Please try again later.", request);
    }

    /**
     * Thrown by {@code @PreAuthorize} when an authenticated caller lacks the
     * required role. Handled explicitly here — otherwise it would be caught
     * by the generic {@code Exception.class} fallback below and returned as
     * a 500, instead of the correct 403.
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
