package com.bcconstructionservices.projects.controller;

import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.dto.ProjectSummaryResponse;
import com.bcconstructionservices.projects.entity.ExpenseCategory;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.service.ProjectExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for ProjectExpenseController.
 */
@WebMvcTest(ProjectExpenseController.class)
class ProjectExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private ProjectExpenseService projectExpenseService;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    private ProjectExpenseCreateRequest validCreateRequest() {
        ProjectExpenseCreateRequest request = new ProjectExpenseCreateRequest();
        request.setCategory(ExpenseCategory.MATERIAL);
        request.setDescription("50 bags of Portland cement");
        request.setAmount(new BigDecimal("14500.00"));
        request.setExpenseDate(LocalDate.of(2026, 9, 3));
        return request;
    }

    private ProjectExpenseResponse sampleResponse(Long id, Long projectId) {
        ProjectExpenseResponse response = new ProjectExpenseResponse();
        response.setId(id);
        response.setProjectId(projectId);
        response.setCategory(ExpenseCategory.MATERIAL);
        response.setDescription("50 bags of Portland cement");
        response.setAmount(new BigDecimal("14500.00"));
        response.setExpenseDate(LocalDate.of(2026, 9, 3));
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/projects/{projectId}/expenses
    // ---------------------------------------------------------------

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedExpenseForValidBody() throws Exception {
            when(projectExpenseService.addExpense(eq(12L), any(ProjectExpenseCreateRequest.class)))
                    .thenReturn(sampleResponse(301L, 12L));

            mockMvc.perform(post("/api/projects/{projectId}/expenses", 12L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(301))
                    .andExpect(jsonPath("$.projectId").value(12));
        }

        @Test
        void shouldReturn400WhenAmountIsMissing() throws Exception {
            ProjectExpenseCreateRequest request = validCreateRequest();
            request.setAmount(null);

            mockMvc.perform(post("/api/projects/{projectId}/expenses", 12L)
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectExpenseService.addExpense(eq(999L), any(ProjectExpenseCreateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(post("/api/projects/{projectId}/expenses", 999L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenProjectIsNotEditable() throws Exception {
            when(projectExpenseService.addExpense(eq(12L), any(ProjectExpenseCreateRequest.class)))
                    .thenThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(post("/api/projects/{projectId}/expenses", 12L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksExpenseCreatePermission() throws Exception {
            mockMvc.perform(post("/api/projects/{projectId}/expenses", 12L)
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/projects/{projectId}/expenses", 12L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // DELETE /api/projects/{projectId}/expenses/{expenseId}
    // ---------------------------------------------------------------

    @Nested
    class DeleteTests {

        @Test
        void shouldReturn204WhenDeleted() throws Exception {
            mockMvc.perform(delete("/api/projects/{projectId}/expenses/{expenseId}", 12L, 301L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_DELETE")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenExpenseNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("ProjectExpense", 999L))
                    .when(projectExpenseService).deleteExpense(12L, 999L);

            mockMvc.perform(delete("/api/projects/{projectId}/expenses/{expenseId}", 12L, 999L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_DELETE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenProjectIsNotEditable() throws Exception {
            org.mockito.Mockito.doThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED))
                    .when(projectExpenseService).deleteExpense(12L, 301L);

            mockMvc.perform(delete("/api/projects/{projectId}/expenses/{expenseId}", 12L, 301L)
                            .with(authenticatedJwt("PROJECT_EXPENSE_DELETE")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksExpenseDeletePermission() throws Exception {
            mockMvc.perform(delete("/api/projects/{projectId}/expenses/{expenseId}", 12L, 301L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/projects/{projectId}/expenses
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfExpenses() throws Exception {
            when(projectExpenseService.search(eq(12L), any(), any()))
                    .thenReturn(PageResponse.<ProjectExpenseResponse>builder()
                            .content(List.of(sampleResponse(301L, 12L)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/projects/{projectId}/expenses", 12L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(301));
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectExpenseService.search(eq(999L), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(get("/api/projects/{projectId}/expenses", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/projects/{projectId}/summary
    // ---------------------------------------------------------------

    @Nested
    class SummaryTests {

        @Test
        void shouldReturn200WithAggregatedSummary() throws Exception {
            ProjectSummaryResponse summary = ProjectSummaryResponse.builder()
                    .projectId(12L)
                    .totalLabor(new BigDecimal("8000.00"))
                    .totalMaterial(new BigDecimal("14500.00"))
                    .totalOther(new BigDecimal("500.00"))
                    .totalExpenses(new BigDecimal("23000.00"))
                    .build();
            when(projectExpenseService.getSummary(12L)).thenReturn(summary);

            mockMvc.perform(get("/api/projects/{projectId}/summary", 12L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalExpenses").value(23000.00));
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectExpenseService.getSummary(999L)).thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(get("/api/projects/{projectId}/summary", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }
}
