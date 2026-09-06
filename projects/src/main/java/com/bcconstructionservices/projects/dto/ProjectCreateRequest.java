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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to create a new project")
public class ProjectCreateRequest {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "Unique short code for the project", example = "PRJ-2026-001")
    private String code;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Project name", example = "Sta. Maria Warehouse Expansion")
    private String name;

    @Size(max = 2000)
    @Schema(description = "Optional free-text description", example = "New 500sqm warehouse extension for the Sta. Maria site")
    private String description;

    @Positive
    @Schema(description = "Optional planned budget", example = "2500000.00")
    private BigDecimal budget;

    @NotNull
    @Schema(description = "Date the project starts", example = "2026-09-01")
    private LocalDate startDate;

    @Schema(description = "Optional planned/actual end date", example = "2026-12-15")
    private LocalDate endDate;
}
