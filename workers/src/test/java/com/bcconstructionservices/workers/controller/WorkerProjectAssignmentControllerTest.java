package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.exception.DuplicateActiveAssignmentException;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.service.WorkerProjectAssignmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkerProjectAssignmentController.class)
class WorkerProjectAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WorkerProjectAssignmentService workerProjectAssignmentService;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    private WorkerProjectAssignmentCreateRequest validCreateRequest() {
        WorkerProjectAssignmentCreateRequest request = new WorkerProjectAssignmentCreateRequest();
        request.setWorkerId(1L);
        request.setProjectId(12L);
        return request;
    }

    private WorkerProjectAssignmentResponse sampleResponse(Long id) {
        WorkerProjectAssignmentResponse response = new WorkerProjectAssignmentResponse();
        response.setId(id);
        response.setWorkerId(1L);
        response.setProjectId(12L);
        response.setActive(true);
        return response;
    }

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedAssignmentForValidBody() throws Exception {
            when(workerProjectAssignmentService.assign(any(WorkerProjectAssignmentCreateRequest.class)))
                    .thenReturn(sampleResponse(21L));

            mockMvc.perform(post("/api/worker-assignments")
                            .with(authenticatedJwt("WORKER_ASSIGNMENT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(21));
        }

        @Test
        void shouldReturn404WhenWorkerNotFound() throws Exception {
            when(workerProjectAssignmentService.assign(any(WorkerProjectAssignmentCreateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Worker", 999L));

            mockMvc.perform(post("/api/worker-assignments")
                            .with(authenticatedJwt("WORKER_ASSIGNMENT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenWorkerAlreadyHasAnActiveAssignment() throws Exception {
            when(workerProjectAssignmentService.assign(any(WorkerProjectAssignmentCreateRequest.class)))
                    .thenThrow(new DuplicateActiveAssignmentException(1L));

            mockMvc.perform(post("/api/worker-assignments")
                            .with(authenticatedJwt("WORKER_ASSIGNMENT_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn403WhenCallerLacksAssignmentCreatePermission() throws Exception {
            mockMvc.perform(post("/api/worker-assignments")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/worker-assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class DeactivateTests {

        @Test
        void shouldReturn204WhenDeactivated() throws Exception {
            mockMvc.perform(patch("/api/worker-assignments/{id}/deactivate", 21L)
                            .with(authenticatedJwt("WORKER_ASSIGNMENT_DEACTIVATE")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenAssignmentNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("WorkerProjectAssignment", 999L))
                    .when(workerProjectAssignmentService).deactivate(999L);

            mockMvc.perform(patch("/api/worker-assignments/{id}/deactivate", 999L)
                            .with(authenticatedJwt("WORKER_ASSIGNMENT_DEACTIVATE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksAssignmentDeactivatePermission() throws Exception {
            mockMvc.perform(patch("/api/worker-assignments/{id}/deactivate", 21L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithAssignmentWhenFound() throws Exception {
            when(workerProjectAssignmentService.getById(21L)).thenReturn(sampleResponse(21L));

            mockMvc.perform(get("/api/worker-assignments/{id}", 21L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(21));
        }

        @Test
        void shouldReturn404WhenNotFound() throws Exception {
            when(workerProjectAssignmentService.getById(999L))
                    .thenThrow(new ResourceNotFoundException("WorkerProjectAssignment", 999L));

            mockMvc.perform(get("/api/worker-assignments/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfAssignments() throws Exception {
            when(workerProjectAssignmentService.search(any(), any(), any()))
                    .thenReturn(PageResponse.<WorkerProjectAssignmentResponse>builder()
                            .content(List.of(sampleResponse(21L)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/worker-assignments").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(21));
        }
    }
}
