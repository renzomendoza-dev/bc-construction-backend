package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.MaterialRequestCreateRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestLineItemRequest;
import com.bcconstructionservices.inventory.dto.MaterialRequestResponse;
import com.bcconstructionservices.inventory.dto.MaterialRequestUpdateRequest;
import com.bcconstructionservices.inventory.dto.PageResponse;
import com.bcconstructionservices.inventory.entity.MaterialRequestStatus;
import com.bcconstructionservices.inventory.exception.InvalidStockOperationException;
import com.bcconstructionservices.inventory.exception.MaterialRequestNotEditableException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.MaterialRequestService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for MaterialRequestController.
 */
@WebMvcTest(MaterialRequestController.class)
class MaterialRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private MaterialRequestService materialRequestService;

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

    private MaterialRequestCreateRequest validCreateRequest() {
        MaterialRequestLineItemRequest line = new MaterialRequestLineItemRequest();
        line.setItemId(1L);
        line.setQuantityRequested(50);

        MaterialRequestCreateRequest request = new MaterialRequestCreateRequest();
        request.setSiteWarehouseId(2L);
        request.setLines(List.of(line));
        return request;
    }

    private MaterialRequestResponse sampleResponse(Long id, MaterialRequestStatus status) {
        MaterialRequestResponse response = new MaterialRequestResponse();
        response.setId(id);
        response.setSiteWarehouseId(2L);
        response.setStatus(status);
        response.setLines(List.of());
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/inventory/material-requests
    // ---------------------------------------------------------------

    @Nested
    class CreateTests {

        @Test
        void shouldReturn201WithCreatedRequestForValidBody() throws Exception {
            when(materialRequestService.create(any(MaterialRequestCreateRequest.class)))
                    .thenReturn(sampleResponse(14L, MaterialRequestStatus.SUBMITTED));

            mockMvc.perform(post("/api/inventory/material-requests")
                            .with(authenticatedJwt("MATERIAL_REQUEST_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(14))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"));
        }

        @Test
        void shouldReturn400WhenLinesListIsEmpty() throws Exception {
            MaterialRequestCreateRequest request = validCreateRequest();
            request.setLines(List.of());

            mockMvc.perform(post("/api/inventory/material-requests")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenSiteWarehouseIsNotTypeSite() throws Exception {
            when(materialRequestService.create(any(MaterialRequestCreateRequest.class)))
                    .thenThrow(new InvalidStockOperationException(
                            "Material requests can only be created against a SITE warehouse (warehouseId: 1 is type MAIN)"));

            mockMvc.perform(post("/api/inventory/material-requests")
                            .with(authenticatedJwt("MATERIAL_REQUEST_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403WhenCallerLacksMaterialRequestCreatePermission() throws Exception {
            mockMvc.perform(post("/api/inventory/material-requests")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/inventory/material-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // PUT /api/inventory/material-requests/{id}
    // ---------------------------------------------------------------

    @Nested
    class UpdateTests {

        private MaterialRequestUpdateRequest validUpdateRequest() {
            MaterialRequestLineItemRequest line = new MaterialRequestLineItemRequest();
            line.setItemId(1L);
            line.setQuantityRequested(75);

            MaterialRequestUpdateRequest request = new MaterialRequestUpdateRequest();
            request.setLines(List.of(line));
            return request;
        }

        @Test
        void shouldReturn200WithUpdatedRequestForValidBody() throws Exception {
            when(materialRequestService.update(eq(14L), any(MaterialRequestUpdateRequest.class)))
                    .thenReturn(sampleResponse(14L, MaterialRequestStatus.SUBMITTED));

            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .with(authenticatedJwt("MATERIAL_REQUEST_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(14));
        }

        @Test
        void shouldReturn400WhenLinesListIsEmpty() throws Exception {
            MaterialRequestUpdateRequest request = validUpdateRequest();
            request.setLines(List.of());

            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenRequestNotFound() throws Exception {
            when(materialRequestService.update(eq(999L), any(MaterialRequestUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("MaterialRequest", 999L));

            mockMvc.perform(put("/api/inventory/material-requests/{id}", 999L)
                            .with(authenticatedJwt("MATERIAL_REQUEST_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenRequestIsNoLongerEditable() throws Exception {
            when(materialRequestService.update(eq(14L), any(MaterialRequestUpdateRequest.class)))
                    .thenThrow(new MaterialRequestNotEditableException(14L, MaterialRequestStatus.FULFILLED));

            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .with(authenticatedJwt("MATERIAL_REQUEST_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn403WhenCallerLacksMaterialRequestEditPermission() throws Exception {
            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn403WhenCallerHasOnlyCreatePermissionNotEdit() throws Exception {
            // MATERIAL_REQUEST_CREATE must not be sufficient for this endpoint -
            // create and edit are deliberately separate permissions.
            mockMvc.perform(put("/api/inventory/material-requests/{id}", 14L)
                            .with(authenticatedJwt("MATERIAL_REQUEST_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/inventory/material-requests/{id}
    // ---------------------------------------------------------------

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithRequestWhenFound() throws Exception {
            when(materialRequestService.getById(14L))
                    .thenReturn(sampleResponse(14L, MaterialRequestStatus.SUBMITTED));

            mockMvc.perform(get("/api/inventory/material-requests/{id}", 14L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(14));
        }

        @Test
        void shouldReturn404WhenRequestNotFound() throws Exception {
            when(materialRequestService.getById(999L))
                    .thenThrow(new ResourceNotFoundException("MaterialRequest", 999L));

            mockMvc.perform(get("/api/inventory/material-requests/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/inventory/material-requests
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfRequests() throws Exception {
            when(materialRequestService.search(any(), any(), any()))
                    .thenReturn(PageResponse.<MaterialRequestResponse>builder()
                            .content(List.of(sampleResponse(14L, MaterialRequestStatus.SUBMITTED)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/inventory/material-requests").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(14));
        }
    }
}
