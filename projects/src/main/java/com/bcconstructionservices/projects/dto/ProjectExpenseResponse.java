package com.bcconstructionservices.projects.dto;

import com.bcconstructionservices.projects.entity.ExpenseCategory;
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
public class ProjectExpenseResponse {

    @Schema(description = "Unique identifier of the expense", example = "301")
    private Long id;

    @Schema(description = "Identifier of the project this expense is recorded against", example = "12")
    private Long projectId;

    @Schema(description = "Expense category", example = "MATERIAL", allowableValues = {"LABOR", "MATERIAL", "OTHER"})
    private ExpenseCategory category;

    @Schema(description = "Short description of the expense", example = "50 bags of Portland cement")
    private String description;

    @Schema(description = "Cost of this expense", example = "14500.00")
    private BigDecimal amount;

    @Schema(description = "Date the cost was actually incurred", example = "2026-09-03")
    private LocalDate expenseDate;

    @Schema(description = "ID of the user who recorded this expense", example = "3")
    private Long recordedBy;

    @Schema(description = "Full name of the user who recorded this expense", example = "Juan Dela Cruz")
    private String recordedByName;

    @Schema(description = "Timestamp when the expense was recorded", example = "2026-09-03T09:15:30Z")
    private Instant createdAt;
}
