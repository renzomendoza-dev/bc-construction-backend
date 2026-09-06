package com.bcconstructionservices.projects.mapper;

import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectExpenseMapperTest {

    private ProjectExpenseMapper mapper;

    private UserLookupHelper userLookupHelper;

    private Project project;

    @BeforeEach
    void setUp() {
        mapper = new ProjectExpenseMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);

        project = new Project();
        project.setId(12L);
    }

    private ProjectExpense buildExpense() {
        ProjectExpense expense = new ProjectExpense();
        expense.setId(301L);
        expense.setProject(project);
        expense.setCategory(ExpenseCategory.MATERIAL);
        expense.setDescription("50 bags of Portland cement");
        expense.setAmount(new BigDecimal("14500.00"));
        expense.setExpenseDate(LocalDate.of(2026, 9, 3));
        expense.setRecordedBy(3L);
        expense.setCreatedAt(Instant.parse("2026-09-03T09:15:30Z"));
        return expense;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapExpenseToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(3L)).thenReturn("Juan Dela Cruz");
            ProjectExpense expense = buildExpense();

            ProjectExpenseResponse response = mapper.toResponse(expense);

            assertThat(response.getId()).isEqualTo(301L);
            assertThat(response.getProjectId()).isEqualTo(12L);
            assertThat(response.getCategory()).isEqualTo(ExpenseCategory.MATERIAL);
            assertThat(response.getDescription()).isEqualTo("50 bags of Portland cement");
            assertThat(response.getAmount()).isEqualByComparingTo("14500.00");
            assertThat(response.getExpenseDate()).isEqualTo(LocalDate.of(2026, 9, 3));
            assertThat(response.getRecordedBy()).isEqualTo(3L);
            assertThat(response.getRecordedByName()).isEqualTo("Juan Dela Cruz");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-09-03T09:15:30Z"));
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            ProjectExpenseCreateRequest request = ProjectExpenseCreateRequest.builder()
                    .category(ExpenseCategory.LABOR)
                    .description("Week 32 wages - 5 workers")
                    .amount(new BigDecimal("22000.00"))
                    .expenseDate(LocalDate.of(2026, 9, 4))
                    .build();

            ProjectExpense entity = mapper.toEntity(request);

            assertThat(entity.getCategory()).isEqualTo(ExpenseCategory.LABOR);
            assertThat(entity.getDescription()).isEqualTo("Week 32 wages - 5 workers");
            assertThat(entity.getAmount()).isEqualByComparingTo("22000.00");
            assertThat(entity.getExpenseDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        }

        @Test
        void shouldNotSetServerManagedOrResolvedFieldsFromCreateRequest() {
            ProjectExpenseCreateRequest request = ProjectExpenseCreateRequest.builder()
                    .category(ExpenseCategory.OTHER)
                    .description("Site permit fee")
                    .amount(new BigDecimal("5000.00"))
                    .expenseDate(LocalDate.of(2026, 9, 4))
                    .build();

            ProjectExpense entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getProject()).isNull();
            assertThat(entity.getRecordedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
        }
    }
}
