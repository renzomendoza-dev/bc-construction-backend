package com.bcconstructionservices.equipment.controller;

import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchCreateRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchLineRequest;
import com.bcconstructionservices.equipment.dto.EquipmentAssignmentBatchResponse;
import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import com.bcconstructionservices.equipment.exception.EquipmentAssignmentBatchNotFoundException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentBatchRequestException;
import com.bcconstructionservices.equipment.exception.InvalidEquipmentStatusException;
import com.bcconstructionservices.equipment.exception.WarehouseNotFoundException;
import com.bcconstructionservices.equipment.service.EquipmentAssignmentBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for EquipmentAssignmentBatchController.
 */
@WebMvcTest(EquipmentAssignmentBatchController.class)
class EquipmentAssignmentBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private EquipmentAssignmentBatchService equipmentAssignmentBatchService;

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

    private EquipmentAssignmentBatchCreateRequest validAssignOutRequest() {
        EquipmentAssignmentBatchLineRequest line = new EquipmentAssignmentBatchLineRequest();
        line.setEquipmentId(42L);

        EquipmentAssignmentBatchCreateRequest request = new EquipmentAssignmentBatchCreateRequest();
        request.setDestinationWarehouseId(2L);
        request.setHolderId(17L);
        request.setLines(List.of(line));
        return request;
    }

    private EquipmentAssignmentBatchResponse sampleResponse(Long id, EquipmentAssignmentBatchStatus status) {
        EquipmentAssignmentBatchResponse response = new EquipmentAssignmentBatchResponse();
        response.setId(id);
        response.setDestinationWarehouseId(2L);
        response.setHolderId(17L);
        response.setStatus(status);
        response.setLines(List.of());
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/equipment/assignment-batches
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldReturn201WithCreatedBatchForValidRequest() throws Exception {
            when(equipmentAssignmentBatchService.createDraft(any(EquipmentAssignmentBatchCreateRequest.class)))
                    .thenReturn(sampleResponse(15L, EquipmentAssignmentBatchStatus.DRAFT));

            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_CREATE"))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validAssignOutRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(15))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void shouldReturn400WhenLinesListIsEmpty() throws Exception {
            EquipmentAssignmentBatchCreateRequest request = validAssignOutRequest();
            request.setLines(List.of());

            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .with(authenticatedJwt())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenHolderIdMissingForAssignOut() throws Exception {
            when(equipmentAssignmentBatchService.createDraft(any(EquipmentAssignmentBatchCreateRequest.class)))
                    .thenThrow(new InvalidEquipmentBatchRequestException(
                            "holderId is required for an assign-out batch (destinationWarehouseId 2 is a SITE warehouse)"));

            EquipmentAssignmentBatchCreateRequest request = validAssignOutRequest();
            request.setHolderId(null);

            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_CREATE"))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenDestinationWarehouseNotFound() throws Exception {
            when(equipmentAssignmentBatchService.createDraft(any(EquipmentAssignmentBatchCreateRequest.class)))
                    .thenThrow(new WarehouseNotFoundException(999L));

            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_CREATE"))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validAssignOutRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksCreatePermission() throws Exception {
            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .with(authenticatedJwt())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validAssignOutRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/equipment/assignment-batches")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validAssignOutRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/equipment/assignment-batches/{id}/submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldReturn200WithCompletedBatchOnSuccessfulSubmit() throws Exception {
            when(equipmentAssignmentBatchService.submit(15L))
                    .thenReturn(sampleResponse(15L, EquipmentAssignmentBatchStatus.COMPLETED));

            mockMvc.perform(post("/api/equipment/assignment-batches/{id}/submit", 15L)
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_SUBMIT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void shouldReturn404WhenBatchNotFound() throws Exception {
            when(equipmentAssignmentBatchService.submit(999L))
                    .thenThrow(new EquipmentAssignmentBatchNotFoundException(999L));

            mockMvc.perform(post("/api/equipment/assignment-batches/{id}/submit", 999L)
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_SUBMIT")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenALineEquipmentIsNotInAValidStatus() throws Exception {
            when(equipmentAssignmentBatchService.submit(15L))
                    .thenThrow(new InvalidEquipmentStatusException(
                            "Equipment EQ-2026-0042 is not available for checkout (current status: CHECKED_OUT)"));

            mockMvc.perform(post("/api/equipment/assignment-batches/{id}/submit", 15L)
                            .with(authenticatedJwt("EQUIPMENT_ASSIGNMENT_BATCH_SUBMIT")))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn403WhenCallerLacksSubmitPermission() throws Exception {
            mockMvc.perform(post("/api/equipment/assignment-batches/{id}/submit", 15L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/equipment/assignment-batches/{id}/submit", 15L))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/equipment/assignment-batches/{id}
    // ---------------------------------------------------------------

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithBatchWhenFound() throws Exception {
            when(equipmentAssignmentBatchService.getById(15L))
                    .thenReturn(sampleResponse(15L, EquipmentAssignmentBatchStatus.DRAFT));

            mockMvc.perform(get("/api/equipment/assignment-batches/{id}", 15L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(15));
        }

        @Test
        void shouldReturn404WhenBatchNotFound() throws Exception {
            when(equipmentAssignmentBatchService.getById(999L))
                    .thenThrow(new EquipmentAssignmentBatchNotFoundException(999L));

            mockMvc.perform(get("/api/equipment/assignment-batches/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/equipment/assignment-batches
    // ---------------------------------------------------------------

    @Nested
    class FindAllTests {

        @Test
        void shouldReturn200WithListOfBatches() throws Exception {
            when(equipmentAssignmentBatchService.findAll(eq(null)))
                    .thenReturn(List.of(sampleResponse(15L, EquipmentAssignmentBatchStatus.DRAFT)));

            mockMvc.perform(get("/api/equipment/assignment-batches").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(15));
        }
    }
}
