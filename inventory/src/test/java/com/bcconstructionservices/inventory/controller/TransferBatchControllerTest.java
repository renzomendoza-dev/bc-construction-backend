package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.TransferBatchCreateRequest;
import com.bcconstructionservices.inventory.dto.TransferBatchResponse;
import com.bcconstructionservices.inventory.dto.TransferLineItemRequest;
import com.bcconstructionservices.inventory.entity.TransferBatchStatus;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.TransferBatchService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for TransferBatchController.
 */
@WebMvcTest(TransferBatchController.class)
class TransferBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TransferBatchService transferBatchService;

    /**
     * Authenticated JWT, optionally granted the given permission(s). See
     * EquipmentControllerTest for why authorities are set directly via
     * .authorities(...) rather than a realm_access claim.
     */
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

    private TransferBatchCreateRequest validCreateRequest() {
        TransferLineItemRequest line = new TransferLineItemRequest();
        line.setItemId(1L);
        line.setQuantity(50);

        TransferBatchCreateRequest request = new TransferBatchCreateRequest();
        request.setOriginWarehouseId(1L);
        request.setDestinationWarehouseId(2L);
        request.setLines(List.of(line));
        return request;
    }

    private TransferBatchResponse sampleResponse(Long id, TransferBatchStatus status) {
        TransferBatchResponse response = new TransferBatchResponse();
        response.setId(id);
        response.setOriginWarehouseId(1L);
        response.setDestinationWarehouseId(2L);
        response.setStatus(status);
        response.setLines(List.of());
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/inventory/transfer-batches
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldReturn201WithCreatedBatchForValidRequest() throws Exception {
            when(transferBatchService.createDraft(any(TransferBatchCreateRequest.class)))
                    .thenReturn(sampleResponse(15L, TransferBatchStatus.DRAFT));

            mockMvc.perform(post("/api/inventory/transfer-batches")
                            .with(authenticatedJwt("TRANSFER_BATCH_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(15))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void shouldReturn400WhenLinesListIsEmpty() throws Exception {
            TransferBatchCreateRequest request = validCreateRequest();
            request.setLines(List.of());

            mockMvc.perform(post("/api/inventory/transfer-batches")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenCallerLacksTransferBatchCreatePermission() throws Exception {
            mockMvc.perform(post("/api/inventory/transfer-batches")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/inventory/transfer-batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/inventory/transfer-batches/{id}/submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldReturn200WithCompletedBatchOnSuccessfulSubmit() throws Exception {
            when(transferBatchService.submit(15L)).thenReturn(sampleResponse(15L, TransferBatchStatus.COMPLETED));

            mockMvc.perform(post("/api/inventory/transfer-batches/{id}/submit", 15L)
                            .with(authenticatedJwt("TRANSFER_BATCH_SUBMIT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void shouldReturn404WhenBatchNotFound() throws Exception {
            when(transferBatchService.submit(999L)).thenThrow(new ResourceNotFoundException("TransferBatch", 999L));

            mockMvc.perform(post("/api/inventory/transfer-batches/{id}/submit", 999L)
                            .with(authenticatedJwt("TRANSFER_BATCH_SUBMIT")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksTransferBatchSubmitPermission() throws Exception {
            mockMvc.perform(post("/api/inventory/transfer-batches/{id}/submit", 15L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/inventory/transfer-batches/{id}/submit", 15L))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/inventory/transfer-batches/{id}
    // ---------------------------------------------------------------

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithBatchWhenFound() throws Exception {
            when(transferBatchService.getById(15L)).thenReturn(sampleResponse(15L, TransferBatchStatus.DRAFT));

            mockMvc.perform(get("/api/inventory/transfer-batches/{id}", 15L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(15));
        }

        @Test
        void shouldReturn404WhenBatchNotFound() throws Exception {
            when(transferBatchService.getById(999L)).thenThrow(new ResourceNotFoundException("TransferBatch", 999L));

            mockMvc.perform(get("/api/inventory/transfer-batches/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/inventory/transfer-batches
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfBatches() throws Exception {
            when(transferBatchService.search(any(), any(), any(), any()))
                    .thenReturn(com.bcconstructionservices.inventory.dto.PageResponse.<TransferBatchResponse>builder()
                            .content(List.of(sampleResponse(15L, TransferBatchStatus.DRAFT)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/inventory/transfer-batches").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(15));
        }
    }
}
