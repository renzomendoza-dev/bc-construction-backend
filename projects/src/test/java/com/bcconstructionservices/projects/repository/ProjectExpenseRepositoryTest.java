package com.bcconstructionservices.projects.repository;

import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProjectExpenseRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectExpenseRepository projectExpenseRepository;

    private Project persistProject(String code) {
        Project project = new Project();
        project.setCode(code);
        project.setName("Sta. Maria Warehouse Expansion");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private ProjectExpense buildExpense(Project project, ExpenseCategory category, BigDecimal amount) {
        ProjectExpense expense = new ProjectExpense();
        expense.setProject(project);
        expense.setCategory(category);
        expense.setDescription("Test expense");
        expense.setAmount(amount);
        expense.setExpenseDate(LocalDate.of(2026, 9, 3));
        return expense;
    }

    @Nested
    class FindAllByProjectIdTests {

        @Test
        void shouldReturnEveryExpenseForTheProjectRegardlessOfCategory() {
            Project project = persistProject("PRJ-100");
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.LABOR, new BigDecimal("5000.00")));
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.MATERIAL, new BigDecimal("14500.00")));
            entityManager.clear();

            List<ProjectExpense> result = projectExpenseRepository.findAllByProjectId(project.getId());

            assertThat(result).hasSize(2);
        }

        @Test
        void shouldNotReturnExpensesForADifferentProject() {
            Project project1 = persistProject("PRJ-101");
            Project project2 = persistProject("PRJ-102");
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project1, ExpenseCategory.OTHER, new BigDecimal("500.00")));
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project2, ExpenseCategory.OTHER, new BigDecimal("999.00")));
            entityManager.clear();

            List<ProjectExpense> result = projectExpenseRepository.findAllByProjectId(project1.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAmount()).isEqualByComparingTo("500.00");
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByCategory() {
            Project project = persistProject("PRJ-103");
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.LABOR, new BigDecimal("5000.00")));
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.MATERIAL, new BigDecimal("14500.00")));
            entityManager.clear();

            List<ProjectExpense> result = projectExpenseRepository
                    .search(project.getId(), ExpenseCategory.MATERIAL, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCategory()).isEqualTo(ExpenseCategory.MATERIAL);
        }

        @Test
        void shouldReturnAllCategoriesWhenFilterIsNull() {
            Project project = persistProject("PRJ-104");
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.LABOR, new BigDecimal("5000.00")));
            projectExpenseRepository.saveAndFlush(
                    buildExpense(project, ExpenseCategory.OTHER, new BigDecimal("300.00")));
            entityManager.clear();

            List<ProjectExpense> result = projectExpenseRepository
                    .search(project.getId(), null, PageRequest.of(0, 10)).getContent();

            assertThat(result).hasSize(2);
        }
    }
}
