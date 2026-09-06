package com.bcconstructionservices.projects.service;

import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.dto.ProjectSummaryResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.mapper.ProjectExpenseMapperImpl;
import com.bcconstructionservices.projects.repository.ProjectExpenseRepository;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectExpenseServiceTest {

    private static final Long PROJECT_ID = 12L;

    @Mock
    private ProjectExpenseRepository projectExpenseRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectService projectService;
    @Spy
    private ProjectExpenseMapperImpl projectExpenseMapper = new ProjectExpenseMapperImpl();

    @InjectMocks
    private ProjectExpenseService projectExpenseService;

    @Mock
    private UserLookupHelper userLookupHelper;

    private Project project;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectExpenseMapper, "userLookupHelper", userLookupHelper);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectStatus.ACTIVE);
    }

    private ProjectExpenseCreateRequest expenseRequest(ExpenseCategory category, String amount) {
        ProjectExpenseCreateRequest request = new ProjectExpenseCreateRequest();
        request.setCategory(category);
        request.setDescription("Test expense");
        request.setAmount(new BigDecimal(amount));
        request.setExpenseDate(LocalDate.of(2026, 9, 3));
        return request;
    }

    private ProjectExpense expense(ExpenseCategory category, String amount) {
        ProjectExpense expense = new ProjectExpense();
        expense.setProject(project);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setDescription("Test expense");
        expense.setExpenseDate(LocalDate.of(2026, 9, 3));
        return expense;
    }

    // ---------------------------------------------------------------
    // addExpense
    // ---------------------------------------------------------------

    @Nested
    class AddExpenseTests {

        @Test
        void shouldRecordExpenseWhenProjectIsEditable() {
            when(projectService.requireEditableProject(PROJECT_ID)).thenReturn(project);
            when(projectExpenseRepository.save(any(ProjectExpense.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            projectExpenseService.addExpense(PROJECT_ID, expenseRequest(ExpenseCategory.MATERIAL, "14500.00"));

            ArgumentCaptor<ProjectExpense> captor = ArgumentCaptor.forClass(ProjectExpense.class);
            verify(projectExpenseRepository).save(captor.capture());
            ProjectExpense saved = captor.getValue();
            assertThat(saved.getProject()).isEqualTo(project);
            assertThat(saved.getCategory()).isEqualTo(ExpenseCategory.MATERIAL);
            assertThat(saved.getAmount()).isEqualByComparingTo("14500.00");
        }

        @Test
        void shouldPropagateProjectNotEditableExceptionFromProjectService() {
            when(projectService.requireEditableProject(PROJECT_ID))
                    .thenThrow(new ProjectNotEditableException(PROJECT_ID, ProjectStatus.COMPLETED));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectExpenseService.addExpense(
                            PROJECT_ID, expenseRequest(ExpenseCategory.MATERIAL, "14500.00")));

            verify(projectExpenseRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // deleteExpense
    // ---------------------------------------------------------------

    @Nested
    class AssertExpenseDeletableTests {

        @Test
        void shouldReturnTheExpenseWithoutDeletingItWhenProjectIsEditable() {
            ProjectExpense expense = expense(ExpenseCategory.MATERIAL, "14500.00");
            expense.setId(301L);
            when(projectExpenseRepository.findById(301L)).thenReturn(Optional.of(expense));
            when(projectService.requireEditableProject(PROJECT_ID)).thenReturn(project);

            ProjectExpense result = projectExpenseService.assertExpenseDeletable(PROJECT_ID, 301L);

            assertThat(result).isEqualTo(expense);
            verify(projectExpenseRepository, never()).delete(any());
        }

        @Test
        void shouldPropagateProjectNotEditableExceptionWithoutDeleting() {
            ProjectExpense expense = expense(ExpenseCategory.MATERIAL, "14500.00");
            expense.setId(301L);
            when(projectExpenseRepository.findById(301L)).thenReturn(Optional.of(expense));
            when(projectService.requireEditableProject(PROJECT_ID))
                    .thenThrow(new ProjectNotEditableException(PROJECT_ID, ProjectStatus.COMPLETED));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectExpenseService.assertExpenseDeletable(PROJECT_ID, 301L));
            verify(projectExpenseRepository, never()).delete(any());
        }
    }

    @Nested
    class DeleteExpenseTests {

        @Test
        void shouldDeleteExpenseWhenProjectIsEditable() {
            ProjectExpense expense = expense(ExpenseCategory.MATERIAL, "14500.00");
            expense.setId(301L);
            when(projectExpenseRepository.findById(301L)).thenReturn(Optional.of(expense));
            when(projectService.requireEditableProject(PROJECT_ID)).thenReturn(project);

            projectExpenseService.deleteExpense(PROJECT_ID, 301L);

            verify(projectExpenseRepository).delete(expense);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenExpenseDoesNotExist() {
            when(projectExpenseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectExpenseService.deleteExpense(PROJECT_ID, 999L));
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenExpenseBelongsToADifferentProject() {
            Project otherProject = new Project();
            otherProject.setId(999L);
            ProjectExpense expense = expense(ExpenseCategory.MATERIAL, "14500.00");
            expense.setId(301L);
            expense.setProject(otherProject);
            when(projectExpenseRepository.findById(301L)).thenReturn(Optional.of(expense));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectExpenseService.deleteExpense(PROJECT_ID, 301L));
            verify(projectExpenseRepository, never()).delete(any());
        }

        @Test
        void shouldPropagateProjectNotEditableExceptionFromProjectService() {
            ProjectExpense expense = expense(ExpenseCategory.MATERIAL, "14500.00");
            expense.setId(301L);
            when(projectExpenseRepository.findById(301L)).thenReturn(Optional.of(expense));
            when(projectService.requireEditableProject(PROJECT_ID))
                    .thenThrow(new ProjectNotEditableException(PROJECT_ID, ProjectStatus.COMPLETED));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectExpenseService.deleteExpense(PROJECT_ID, 301L));
            verify(projectExpenseRepository, never()).delete(any());
        }
    }

    // ---------------------------------------------------------------
    // search
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturnPagedExpensesWhenProjectExists() {
            when(projectRepository.existsById(PROJECT_ID)).thenReturn(true);
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProjectExpense> page =
                    new PageImpl<>(List.of(expense(ExpenseCategory.LABOR, "5000.00")), pageable, 1);
            when(projectExpenseRepository.search(PROJECT_ID, null, pageable)).thenReturn(page);

            PageResponse<ProjectExpenseResponse> result = projectExpenseService.search(PROJECT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenProjectDoesNotExist() {
            when(projectRepository.existsById(PROJECT_ID)).thenReturn(false);

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectExpenseService.search(PROJECT_ID, null, PageRequest.of(0, 10)));
        }
    }

    // ---------------------------------------------------------------
    // getSummary
    // ---------------------------------------------------------------

    @Nested
    class GetSummaryTests {

        @Test
        void shouldSumEachCategoryIndependently() {
            project.setBudget(null);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectExpenseRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                    expense(ExpenseCategory.LABOR, "5000.00"),
                    expense(ExpenseCategory.LABOR, "3000.00"),
                    expense(ExpenseCategory.MATERIAL, "14500.00"),
                    expense(ExpenseCategory.OTHER, "500.00")
            ));

            ProjectSummaryResponse summary = projectExpenseService.getSummary(PROJECT_ID);

            assertThat(summary.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(summary.getTotalLabor()).isEqualByComparingTo("8000.00");
            assertThat(summary.getTotalMaterial()).isEqualByComparingTo("14500.00");
            assertThat(summary.getTotalOther()).isEqualByComparingTo("500.00");
            assertThat(summary.getTotalExpenses()).isEqualByComparingTo("23000.00");
        }

        @Test
        void shouldReturnZeroTotalsWhenNoExpensesRecorded() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectExpenseRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of());

            ProjectSummaryResponse summary = projectExpenseService.getSummary(PROJECT_ID);

            assertThat(summary.getTotalLabor()).isEqualByComparingTo("0");
            assertThat(summary.getTotalMaterial()).isEqualByComparingTo("0");
            assertThat(summary.getTotalOther()).isEqualByComparingTo("0");
            assertThat(summary.getTotalExpenses()).isEqualByComparingTo("0");
        }

        @Test
        void shouldComputeBudgetRemainingWhenBudgetIsSet() {
            project.setBudget(new BigDecimal("2500000.00"));
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectExpenseRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(
                    expense(ExpenseCategory.MATERIAL, "14500.00")
            ));

            ProjectSummaryResponse summary = projectExpenseService.getSummary(PROJECT_ID);

            assertThat(summary.getBudget()).isEqualByComparingTo("2500000.00");
            assertThat(summary.getBudgetRemaining()).isEqualByComparingTo("2485500.00");
        }

        @Test
        void shouldReturnNullBudgetRemainingWhenNoBudgetSet() {
            project.setBudget(null);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
            when(projectExpenseRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of());

            ProjectSummaryResponse summary = projectExpenseService.getSummary(PROJECT_ID);

            assertThat(summary.getBudget()).isNull();
            assertThat(summary.getBudgetRemaining()).isNull();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenProjectDoesNotExist() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectExpenseService.getSummary(PROJECT_ID));
        }
    }
}
