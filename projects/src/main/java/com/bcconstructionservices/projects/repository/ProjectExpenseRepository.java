package com.bcconstructionservices.projects.repository;

import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectExpenseRepository extends JpaRepository<ProjectExpense, Long> {

    /**
     * Every expense for a project, regardless of category — used by
     * ProjectService.getSummary to sum totals per category. Scoped to the
     * parent project id (not "since the last summary call" or similar), so
     * repeated calls always reflect every expense recorded so far, matching
     * this codebase's aggregation-root convention (see
     * PurchaseOrderService.updateStatusFromReceipts's own javadoc for why
     * that matters).
     */
    List<ProjectExpense> findAllByProjectId(Long projectId);

    /**
     * Filters a project's expenses by optional category.
     */
    @Query("""
            SELECT e FROM ProjectExpense e
            WHERE e.project.id = :projectId
              AND (:category IS NULL OR e.category = :category)
            """)
    Page<ProjectExpense> search(@Param("projectId") Long projectId,
                                 @Param("category") ExpenseCategory category,
                                 Pageable pageable);
}
