package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Standard error response payload returned by the global exception handler
 * for all API errors (validation failures, not-found, conflicts, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    @Schema(description = "Timestamp when the error occurred", example = "2026-07-18T09:15:30Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "Short HTTP status reason phrase", example = "Not Found")
    private String error;

    @Schema(description = "Human-readable message describing the error", example = "Equipment not found with id: 42")
    private String message;

    @Schema(description = "Request path that produced the error", example = "/api/equipment/42")
    private String path;
}
