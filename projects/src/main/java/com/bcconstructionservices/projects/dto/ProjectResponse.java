package com.bcconstructionservices.projects.dto;

import com.bcconstructionservices.projects.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    @Schema(description = "Unique identifier of the project", example = "12")
    private Long id;

    @Schema(description = "Unique short code for the project", example = "PRJ-2026-001")
    private String code;

    @Schema(description = "Project name", example = "Sta. Maria Warehouse Expansion")
    private String name;

    @Schema(description = "Optional free-text description", example = "New 500sqm warehouse extension for the Sta. Maria site")
    private String description;

    @Schema(description = "Current project status", example = "ACTIVE",
            allowableValues = {"ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"})
    private ProjectStatus status;

    @Schema(description = "Optional planned budget", example = "2500000.00")
    private BigDecimal budget;

    @Schema(description = "Date the project starts", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "Optional planned/actual end date", example = "2026-12-15")
    private LocalDate endDate;

    @Schema(description = "ID of the user who created this project", example = "3")
    private Long initiatedBy;

    @Schema(description = "Full name of the user who created this project", example = "Juan Dela Cruz")
    private String initiatedByName;

    @Schema(description = "Timestamp when the project was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the project was last updated", example = "2026-07-20T14:05:00Z")
    private Instant updatedAt;
}
