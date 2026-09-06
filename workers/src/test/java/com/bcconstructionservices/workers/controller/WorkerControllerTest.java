package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.service.WorkerService;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkerController.class)
class WorkerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private WorkerService workerService;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    private WorkerCreateRequest validCreateRequest() {
        WorkerCreateRequest request = new WorkerCreateRequest();
        request.setName("Ramon Villanueva");
        request.setPosition("Mason");
        request.setDailyRate(new BigDecimal("800.00"));
        return request;
    }

    private WorkerUpdateRequest validUpdateRequest() {
        WorkerUpdateRequest request = new WorkerUpdateRequest();
        request.setName("Ramon Villanueva");
        request.setPosition("Mason");
        request.setDailyRate(new BigDecimal("800.00"));
        return request;
    }

    private WorkerResponse sampleResponse(Long id) {
        WorkerResponse response = new WorkerResponse();
        response.setId(id);
        response.setName("Ramon Villanueva");
        response.setDailyRate(new BigDecimal("800.00"));
        response.setActive(true);
        return response;
    }

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedWorkerForValidBody() throws Exception {
            when(workerService.createWorker(any(WorkerCreateRequest.class))).thenReturn(sampleResponse(1L));

            mockMvc.perform(post("/api/workers")
                            .with(authenticatedJwt("WORKER_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void shouldReturn400WhenNameIsBlank() throws Exception {
            WorkerCreateRequest request = validCreateRequest();
            request.setName("");

            mockMvc.perform(post("/api/workers")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenCallerLacksWorkerCreatePermission() throws Exception {
            mockMvc.perform(post("/api/workers")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/workers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void shouldReturn200WithUpdatedWorkerForValidBody() throws Exception {
            when(workerService.updateWorker(eq(1L), any(WorkerUpdateRequest.class))).thenReturn(sampleResponse(1L));

            mockMvc.perform(put("/api/workers/{id}", 1L)
                            .with(authenticatedJwt("WORKER_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void shouldReturn404WhenWorkerNotFound() throws Exception {
            when(workerService.updateWorker(eq(999L), any(WorkerUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Worker", 999L));

            mockMvc.perform(put("/api/workers/{id}", 999L)
                            .with(authenticatedJwt("WORKER_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerHasOnlyCreatePermissionNotEdit() throws Exception {
            mockMvc.perform(put("/api/workers/{id}", 1L)
                            .with(authenticatedJwt("WORKER_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class DeactivateTests {

        @Test
        void shouldReturn204WhenDeactivated() throws Exception {
            mockMvc.perform(patch("/api/workers/{id}/deactivate", 1L)
                            .with(authenticatedJwt("WORKER_DEACTIVATE")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenWorkerNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException("Worker", 999L))
                    .when(workerService).deactivateWorker(999L);

            mockMvc.perform(patch("/api/workers/{id}/deactivate", 999L)
                            .with(authenticatedJwt("WORKER_DEACTIVATE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksDeactivatePermission() throws Exception {
            mockMvc.perform(patch("/api/workers/{id}/deactivate", 1L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithWorkerWhenFound() throws Exception {
            when(workerService.getById(1L)).thenReturn(sampleResponse(1L));

            mockMvc.perform(get("/api/workers/{id}", 1L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void shouldReturn404WhenWorkerNotFound() throws Exception {
            when(workerService.getById(999L)).thenThrow(new ResourceNotFoundException("Worker", 999L));

            mockMvc.perform(get("/api/workers/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfWorkers() throws Exception {
            when(workerService.search(any(), any()))
                    .thenReturn(PageResponse.<WorkerResponse>builder()
                            .content(List.of(sampleResponse(1L)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/workers").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1));
        }
    }
}
