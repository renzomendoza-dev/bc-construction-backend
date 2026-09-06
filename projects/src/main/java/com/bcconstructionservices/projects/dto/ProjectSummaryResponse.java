package com.bcconstructionservices.projects.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Aggregated running-expense totals for a project, computed on demand from
 * its ProjectExpense rows (not stored/cached) — always reflects every
 * expense recorded so far, per this codebase's aggregation-root convention
 * of summing from the source rows rather than an incrementally-maintained
 * running total.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryResponse {

    @Schema(description = "Identifier of the project this summary is for", example = "12")
    private Long projectId;

    @Schema(description = "Sum of all LABOR-category expenses", example = "180000.00")
    private BigDecimal totalLabor;

    @Schema(description = "Sum of all MATERIAL-category expenses", example = "95500.00")
    private BigDecimal totalMaterial;

    @Schema(description = "Sum of all OTHER-category expenses", example = "12000.00")
    private BigDecimal totalOther;

    @Schema(description = "Sum of every expense regardless of category", example = "287500.00")
    private BigDecimal totalExpenses;

    @Schema(description = "The project's planned budget, if one was set", example = "2500000.00")
    private BigDecimal budget;

    @Schema(description = "budget minus totalExpenses; null when the project has no budget set",
            example = "2212500.00")
    private BigDecimal budgetRemaining;
}
