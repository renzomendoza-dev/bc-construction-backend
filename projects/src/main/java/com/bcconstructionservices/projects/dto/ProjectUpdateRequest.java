package com.bcconstructionservices.projects.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Full-replacement update of a project's editable fields — only while
 * ACTIVE/ON_HOLD (422 otherwise, see ProjectNotEditableException). {@code code}
 * is immutable after creation, same as Item's sku/Warehouse's code elsewhere
 * in this codebase, and is not part of this request body. {@code status}
 * changes only via POST /{id}/complete, never through this endpoint.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to replace a project's editable fields")
public class ProjectUpdateRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Project name", example = "Sta. Maria Warehouse Expansion")
    private String name;

    @Size(max = 2000)
    @Schema(description = "Optional free-text description; omit or send null to clear it",
            example = "New 500sqm warehouse extension for the Sta. Maria site")
    private String description;

    @Positive
    @Schema(description = "Optional planned budget; omit or send null to clear it", example = "2500000.00")
    private BigDecimal budget;

    @NotNull
    @Schema(description = "Date the project starts", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "Optional planned/actual end date; omit or send null to clear it", example = "2026-12-15")
    private LocalDate endDate;
}
