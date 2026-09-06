package com.bcconstructionservices.projects.controller;

import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.dto.ProjectUpdateRequest;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.DuplicateResourceException;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.service.ProjectService;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for ProjectController.
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    private ProjectService projectService;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private ProjectCreateRequest validCreateRequest() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setCode("PRJ-2026-001");
        request.setName("Sta. Maria Warehouse Expansion");
        request.setStartDate(LocalDate.of(2026, 9, 1));
        return request;
    }

    private ProjectUpdateRequest validUpdateRequest() {
        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setName("Updated Name");
        request.setStartDate(LocalDate.of(2026, 9, 1));
        return request;
    }

    private ProjectResponse sampleResponse(Long id, ProjectStatus status) {
        ProjectResponse response = new ProjectResponse();
        response.setId(id);
        response.setCode("PRJ-2026-001");
        response.setName("Sta. Maria Warehouse Expansion");
        response.setStatus(status);
        response.setStartDate(LocalDate.of(2026, 9, 1));
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/projects
    // ---------------------------------------------------------------

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedProjectForValidBody() throws Exception {
            when(projectService.createProject(any(ProjectCreateRequest.class)))
                    .thenReturn(sampleResponse(12L, ProjectStatus.ACTIVE));

            mockMvc.perform(post("/api/projects")
                            .with(authenticatedJwt("PROJECT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(12))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void shouldReturn400WhenNameIsBlank() throws Exception {
            ProjectCreateRequest request = validCreateRequest();
            request.setName("");

            mockMvc.perform(post("/api/projects")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn409WhenCodeAlreadyExists() throws Exception {
            when(projectService.createProject(any(ProjectCreateRequest.class)))
                    .thenThrow(new DuplicateResourceException("Project", "code", "PRJ-2026-001"));

            mockMvc.perform(post("/api/projects")
                            .with(authenticatedJwt("PROJECT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn403WhenCallerLacksProjectCreatePermission() throws Exception {
            mockMvc.perform(post("/api/projects")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // PUT /api/projects/{id}
    // ---------------------------------------------------------------

    @Nested
    class UpdateTests {

        @Test
        void shouldReturn200WithUpdatedProjectForValidBody() throws Exception {
            when(projectService.updateProject(eq(12L), any(ProjectUpdateRequest.class)))
                    .thenReturn(sampleResponse(12L, ProjectStatus.ACTIVE));

            mockMvc.perform(put("/api/projects/{id}", 12L)
                            .with(authenticatedJwt("PROJECT_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(12));
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectService.updateProject(eq(999L), any(ProjectUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(put("/api/projects/{id}", 999L)
                            .with(authenticatedJwt("PROJECT_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenProjectIsNoLongerEditable() throws Exception {
            when(projectService.updateProject(eq(12L), any(ProjectUpdateRequest.class)))
                    .thenThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(put("/api/projects/{id}", 12L)
                            .with(authenticatedJwt("PROJECT_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerHasOnlyCreatePermissionNotEdit() throws Exception {
            mockMvc.perform(put("/api/projects/{id}", 12L)
                            .with(authenticatedJwt("PROJECT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/projects/{id}/complete
    // ---------------------------------------------------------------

    @Nested
    class CompleteTests {

        @Test
        void shouldReturn200WithCompletedProject() throws Exception {
            when(projectService.completeProject(12L)).thenReturn(sampleResponse(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(post("/api/projects/{id}/complete", 12L)
                            .with(authenticatedJwt("PROJECT_COMPLETE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void shouldReturn422WhenAlreadyCompleted() throws Exception {
            when(projectService.completeProject(12L))
                    .thenThrow(new ProjectNotEditableException(12L, ProjectStatus.COMPLETED));

            mockMvc.perform(post("/api/projects/{id}/complete", 12L)
                            .with(authenticatedJwt("PROJECT_COMPLETE")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectService.completeProject(999L)).thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(post("/api/projects/{id}/complete", 999L)
                            .with(authenticatedJwt("PROJECT_COMPLETE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksCompletePermission() throws Exception {
            mockMvc.perform(post("/api/projects/{id}/complete", 12L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/projects/{id}
    // ---------------------------------------------------------------

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithProjectWhenFound() throws Exception {
            when(projectService.getById(12L)).thenReturn(sampleResponse(12L, ProjectStatus.ACTIVE));

            mockMvc.perform(get("/api/projects/{id}", 12L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(12));
        }

        @Test
        void shouldReturn404WhenProjectNotFound() throws Exception {
            when(projectService.getById(999L)).thenThrow(new ResourceNotFoundException("Project", 999L));

            mockMvc.perform(get("/api/projects/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/projects
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfProjects() throws Exception {
            when(projectService.search(any(), any()))
                    .thenReturn(PageResponse.<ProjectResponse>builder()
                            .content(List.of(sampleResponse(12L, ProjectStatus.ACTIVE)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/projects").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(12));
        }
    }
}
