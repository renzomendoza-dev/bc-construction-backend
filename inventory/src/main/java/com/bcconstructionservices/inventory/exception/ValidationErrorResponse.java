package com.bcconstructionservices.inventory.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Error payload returned by the global exception handler for @Valid
 * request-body validation failures. Extends the standard error shape with a
 * field-to-message map so clients can highlight the exact invalid fields.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationErrorResponse {

    @Schema(description = "Timestamp when the error occurred", example = "2026-07-18T09:15:30Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Short HTTP status reason phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Human-readable summary message", example = "Validation failed for one or more fields")
    private String message;

    @Schema(description = "Request path that triggered the error", example = "/api/items")
    private String path;

    @Schema(
            description = "Map of field name to the validation error message for that field",
            example = "{\"sku\": \"must not be blank\", \"sellingPrice\": \"must be greater than or equal to 0.0\"}"
    )
    private Map<String, String> fieldErrors;
}
