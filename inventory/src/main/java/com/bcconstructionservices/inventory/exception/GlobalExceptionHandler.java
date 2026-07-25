package com.bcconstructionservices.inventory.exception;

import com.bcconstructionservices.inventory.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central exception handler for the inventory tracking API.
 * Translates domain and framework exceptions into a consistent ErrorResponse
 * (or ValidationErrorResponse for field-level validation failures) body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStockOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStockOperation(
            InvalidStockOperationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InactiveResourceException.class)
    public ResponseEntity<ErrorResponse> handleInactiveResource(
            InactiveResourceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ReceiptProcessingException.class)
    public ResponseEntity<ErrorResponse> handleReceiptProcessing(
            ReceiptProcessingException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, HttpServletRequest request) {

        log.error("Illegal state encountered: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
}
