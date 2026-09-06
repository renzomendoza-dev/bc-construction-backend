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

    /**
     * Validates that an expense exists under the given project and can
     * currently be deleted (project ACTIVE/ON_HOLD, 422 otherwise) — without
     * deleting anything. Split out from deleteExpense specifically for
     * callers that must delete something else first, in the same
     * transaction, before this expense can be removed: workers.AttendanceService
     * deletes its Attendance row before calling deleteExpense below, because
     * Attendance.projectExpenseId is a plain Long backed by a real,
     * non-deferrable FK (not a JPA relation — see Attendance's own javadoc),
     * so Postgres would reject deleting this row first while that FK still
     * points at it. Calling this first lets a locked project (422) still
     * leave both rows untouched, exactly as if deleteExpense itself had
     * rejected the whole operation up front.
     */
    @Transactional(readOnly = true)
    public ProjectExpense assertExpenseDeletable(Long projectId, Long expenseId) {
        ProjectExpense expense = projectExpenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectExpense", expenseId));
        if (!expense.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("ProjectExpense", expenseId);
        }

        projectService.requireEditableProject(projectId);
        return expense;
    }

    /**
     * Only while the project is ACTIVE/ON_HOLD (422 otherwise) — same lock
     * condition as addExpense. Used both by a manual delete via the REST
     * endpoint below and by the workers module's AttendanceService, which
     * calls this directly (after deleting its own Attendance row first — see
     * assertExpenseDeletable's javadoc) to remove the ProjectExpense that
     * Attendance record generated.
     */
    @Transactional
    public void deleteExpense(Long projectId, Long expenseId) {
        ProjectExpense expense = assertExpenseDeletable(projectId, expenseId);
        projectExpenseRepository.delete(expense);
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
