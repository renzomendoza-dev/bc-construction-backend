package com.bcconstructionservices.projects.service;

import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.dto.ProjectSummaryResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.mapper.ProjectExpenseMapper;
import com.bcconstructionservices.projects.repository.ProjectExpenseRepository;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service layer for recording and summarizing a project's running expenses.
 * Kept separate from ProjectService (rather than folded into it) since
 * recording/listing/summarizing expenses is a distinct concern from the
 * project's own lifecycle — matching how PurchaseReceiptService stays
 * separate from PurchaseOrderService despite referencing it.
 */
@Service
@RequiredArgsConstructor
public class ProjectExpenseService {

    private final ProjectExpenseRepository projectExpenseRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectExpenseMapper projectExpenseMapper;

    /**
     * Only while the project is ACTIVE/ON_HOLD (422 otherwise) — matches
     * ProjectService.updateProject's exact lock condition, via the same
     * shared check (ProjectService.requireEditableProject).
     */
    @Transactional
    public ProjectExpenseResponse addExpense(Long projectId, ProjectExpenseCreateRequest request) {
        Project project = projectService.requireEditableProject(projectId);

        ProjectExpense expense = projectExpenseMapper.toEntity(request);
        expense.setProject(project);

        ProjectExpense saved = projectExpenseRepository.save(expense);
        return projectExpenseMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectExpenseResponse> search(Long projectId, ExpenseCategory category, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        Page<ProjectExpense> page = projectExpenseRepository.search(projectId, category, pageable);
        return PageResponse.of(page, projectExpenseMapper::toResponse);
    }

    /**
     * Sums every expense recorded against the project so far, grouped by
     * category, plus budget-remaining if a budget was set. Always computed
     * fresh from the source rows (not an incrementally-maintained running
     * total) — same reasoning as PurchaseOrderService.updateStatusFromReceipts.
     */
    @Transactional(readOnly = true)
    public ProjectSummaryResponse getSummary(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<ProjectExpense> expenses = projectExpenseRepository.findAllByProjectId(projectId);

        BigDecimal totalLabor = sumByCategory(expenses, ExpenseCategory.LABOR);
        BigDecimal totalMaterial = sumByCategory(expenses, ExpenseCategory.MATERIAL);
        BigDecimal totalOther = sumByCategory(expenses, ExpenseCategory.OTHER);
        BigDecimal totalExpenses = totalLabor.add(totalMaterial).add(totalOther);

        BigDecimal budget = project.getBudget();
        BigDecimal budgetRemaining = budget != null ? budget.subtract(totalExpenses) : null;

        return ProjectSummaryResponse.builder()
                .projectId(projectId)
                .totalLabor(totalLabor)
                .totalMaterial(totalMaterial)
                .totalOther(totalOther)
                .totalExpenses(totalExpenses)
                .budget(budget)
                .budgetRemaining(budgetRemaining)
                .build();
    }

    private BigDecimal sumByCategory(List<ProjectExpense> expenses, ExpenseCategory category) {
        return expenses.stream()
                .filter(e -> e.getCategory() == category)
                .map(ProjectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
