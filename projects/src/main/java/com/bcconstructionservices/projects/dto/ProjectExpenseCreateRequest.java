package com.bcconstructionservices.projects.dto;

import com.bcconstructionservices.projects.entity.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload to record one expense against a project")
public class ProjectExpenseCreateRequest {

    @NotNull
    @Schema(description = "Expense category", example = "MATERIAL", allowableValues = {"LABOR", "MATERIAL", "OTHER"})
    private ExpenseCategory category;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Short description of the expense", example = "50 bags of Portland cement")
    private String description;

    @NotNull
    @Schema(description = "Cost of this expense. Normally positive; a negative value represents a credit or "
            + "reversal (e.g. inventory.TransferBatchService auto-generates one when materials are pulled back "
            + "out of a project site) and reduces the category's running total accordingly.",
            example = "14500.00")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "Date the cost was actually incurred", example = "2026-09-03")
    private LocalDate expenseDate;
}
