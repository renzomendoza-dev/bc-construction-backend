package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.PurchaseOrderCreateRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderLineRequest;
import com.bcconstructionservices.inventory.dto.PurchaseOrderResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderSuggestionsResponse;
import com.bcconstructionservices.inventory.dto.PurchaseOrderUpdateRequest;
import com.bcconstructionservices.inventory.entity.PurchaseOrderStatus;
import com.bcconstructionservices.inventory.exception.PurchaseOrderHasReceiptsException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotDeletableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotEditableException;
import com.bcconstructionservices.inventory.exception.PurchaseOrderNotOpenException;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.PurchaseOrderService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for PurchaseOrderController.
 */
@WebMvcTest(PurchaseOrderController.class)
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PurchaseOrderService purchaseOrderService;

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

    private PurchaseOrderCreateRequest validCreateRequest() {
        PurchaseOrderLineRequest line = new PurchaseOrderLineRequest();
        line.setItemId(42L);
        line.setQuantity(100);

        PurchaseOrderCreateRequest request = new PurchaseOrderCreateRequest();
        request.setSupplierId(5L);
        request.setLines(List.of(line));
        return request;
    }

    private PurchaseOrderUpdateRequest validUpdateRequest() {
        PurchaseOrderLineRequest line = new PurchaseOrderLineRequest();
        line.setItemId(42L);
        line.setQuantity(75);

        PurchaseOrderUpdateRequest request = new PurchaseOrderUpdateRequest();
        request.setLines(List.of(line));
        return request;
    }

    private PurchaseOrderResponse sampleResponse(Long id, PurchaseOrderStatus status) {
        PurchaseOrderResponse response = new PurchaseOrderResponse();
        response.setId(id);
        response.setSupplierId(5L);
        response.setStatus(status);
        response.setLines(List.of());
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/purchase-orders
    // ---------------------------------------------------------------

    @Nested
    class CreateDraftTests {

        @Test
        void shouldReturn201WithCreatedOrderForValidRequest() throws Exception {
            when(purchaseOrderService.createDraft(any(PurchaseOrderCreateRequest.class)))
                    .thenReturn(sampleResponse(12L, PurchaseOrderStatus.DRAFT));

            mockMvc.perform(post("/api/purchase-orders")
                            .with(authenticatedJwt("PURCHASE_ORDER_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(12))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void shouldReturn400WhenLinesListIsEmpty() throws Exception {
            PurchaseOrderCreateRequest request = validCreateRequest();
            request.setLines(List.of());

            mockMvc.perform(post("/api/purchase-orders")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404WhenSupplierNotFound() throws Exception {
            when(purchaseOrderService.createDraft(any(PurchaseOrderCreateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Supplier", 5L));

            mockMvc.perform(post("/api/purchase-orders")
                            .with(authenticatedJwt("PURCHASE_ORDER_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksCreatePermission() throws Exception {
            mockMvc.perform(post("/api/purchase-orders")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/purchase-orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // PUT /api/purchase-orders/{id}
    // ---------------------------------------------------------------

    @Nested
    class UpdateTests {

        @Test
        void shouldReturn200WithUpdatedOrderForValidBody() throws Exception {
            when(purchaseOrderService.update(eq(12L), any(PurchaseOrderUpdateRequest.class)))
                    .thenReturn(sampleResponse(12L, PurchaseOrderStatus.DRAFT));

            mockMvc.perform(put("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(12));
        }

        @Test
        void shouldReturn422WhenOrderIsNotDraft() throws Exception {
            when(purchaseOrderService.update(eq(12L), any(PurchaseOrderUpdateRequest.class)))
                    .thenThrow(new PurchaseOrderNotEditableException(12L, PurchaseOrderStatus.SUBMITTED));

            mockMvc.perform(put("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(purchaseOrderService.update(eq(999L), any(PurchaseOrderUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("PurchaseOrder", 999L));

            mockMvc.perform(put("/api/purchase-orders/{id}", 999L)
                            .with(authenticatedJwt("PURCHASE_ORDER_EDIT"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksEditPermission() throws Exception {
            mockMvc.perform(put("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/purchase-orders/{id}/submit
    // ---------------------------------------------------------------

    @Nested
    class SubmitTests {

        @Test
        void shouldReturn200WithSubmittedOrder() throws Exception {
            when(purchaseOrderService.submit(12L)).thenReturn(sampleResponse(12L, PurchaseOrderStatus.SUBMITTED));

            mockMvc.perform(post("/api/purchase-orders/{id}/submit", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_SUBMIT")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"));
        }

        @Test
        void shouldReturn422WhenOrderIsNotDraft() throws Exception {
            when(purchaseOrderService.submit(12L))
                    .thenThrow(new PurchaseOrderNotEditableException(12L, PurchaseOrderStatus.SUBMITTED));

            mockMvc.perform(post("/api/purchase-orders/{id}/submit", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_SUBMIT")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(purchaseOrderService.submit(999L)).thenThrow(new ResourceNotFoundException("PurchaseOrder", 999L));

            mockMvc.perform(post("/api/purchase-orders/{id}/submit", 999L)
                            .with(authenticatedJwt("PURCHASE_ORDER_SUBMIT")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksSubmitPermission() throws Exception {
            mockMvc.perform(post("/api/purchase-orders/{id}/submit", 12L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/purchase-orders/{id}/close
    // ---------------------------------------------------------------

    @Nested
    class CloseTests {

        @Test
        void shouldReturn200WithClosedOrder() throws Exception {
            when(purchaseOrderService.close(12L)).thenReturn(sampleResponse(12L, PurchaseOrderStatus.CLOSED));

            mockMvc.perform(post("/api/purchase-orders/{id}/close", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_CLOSE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        void shouldReturn422WhenAlreadyReceivedOrClosed() throws Exception {
            when(purchaseOrderService.close(12L))
                    .thenThrow(new PurchaseOrderNotOpenException(12L, PurchaseOrderStatus.RECEIVED));

            mockMvc.perform(post("/api/purchase-orders/{id}/close", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_CLOSE")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(purchaseOrderService.close(999L)).thenThrow(new ResourceNotFoundException("PurchaseOrder", 999L));

            mockMvc.perform(post("/api/purchase-orders/{id}/close", 999L)
                            .with(authenticatedJwt("PURCHASE_ORDER_CLOSE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCallerLacksClosePermission() throws Exception {
            mockMvc.perform(post("/api/purchase-orders/{id}/close", 12L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // DELETE /api/purchase-orders/{id}
    // ---------------------------------------------------------------

    @Nested
    class DeleteTests {

        @Test
        void shouldReturn204WhenOrderIsDraft() throws Exception {
            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_DELETE")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("PurchaseOrder", 999L))
                    .when(purchaseOrderService).delete(999L);

            mockMvc.perform(delete("/api/purchase-orders/{id}", 999L)
                            .with(authenticatedJwt("PURCHASE_ORDER_DELETE")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn422WhenOrderIsNotDraft() throws Exception {
            doThrow(new PurchaseOrderNotDeletableException(12L, PurchaseOrderStatus.SUBMITTED))
                    .when(purchaseOrderService).delete(12L);

            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_DELETE")))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void shouldReturn409WhenOrderHasReceiptsReferencingIt() throws Exception {
            doThrow(new PurchaseOrderHasReceiptsException(12L))
                    .when(purchaseOrderService).delete(12L);

            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_DELETE")))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn403WhenCallerLacksDeletePermission() throws Exception {
            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn403WhenCallerHasOnlyClosePermissionNotDelete() throws Exception {
            // PURCHASE_ORDER_CLOSE must not be sufficient for this endpoint -
            // close and delete are deliberately separate permissions.
            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L)
                            .with(authenticatedJwt("PURCHASE_ORDER_CLOSE")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(delete("/api/purchase-orders/{id}", 12L))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/purchase-orders/{id}
    // ---------------------------------------------------------------

    @Nested
    class GetByIdTests {

        @Test
        void shouldReturn200WithOrderWhenFound() throws Exception {
            when(purchaseOrderService.getById(12L)).thenReturn(sampleResponse(12L, PurchaseOrderStatus.DRAFT));

            mockMvc.perform(get("/api/purchase-orders/{id}", 12L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(12));
        }

        @Test
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(purchaseOrderService.getById(999L)).thenThrow(new ResourceNotFoundException("PurchaseOrder", 999L));

            mockMvc.perform(get("/api/purchase-orders/{id}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/purchase-orders
    // ---------------------------------------------------------------

    @Nested
    class SearchTests {

        @Test
        void shouldReturn200WithPageOfOrders() throws Exception {
            when(purchaseOrderService.search(any(), any(), any()))
                    .thenReturn(com.bcconstructionservices.inventory.dto.PageResponse.<PurchaseOrderResponse>builder()
                            .content(List.of(sampleResponse(12L, PurchaseOrderStatus.DRAFT)))
                            .page(0).size(20).totalElements(1).totalPages(1).build());

            mockMvc.perform(get("/api/purchase-orders").with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(12));
        }
    }

    // ---------------------------------------------------------------
    // GET /api/purchase-orders/suggestions
    // ---------------------------------------------------------------

    @Nested
    class SuggestionsTests {

        @Test
        void shouldReturn200WithSuggestions() throws Exception {
            when(purchaseOrderService.getSuggestions(5L))
                    .thenReturn(PurchaseOrderSuggestionsResponse.builder()
                            .supplierId(5L)
                            .suggestions(List.of())
                            .build());

            mockMvc.perform(get("/api/purchase-orders/suggestions").param("supplierId", "5")
                            .with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supplierId").value(5));
        }

        @Test
        void shouldReturn404WhenSupplierNotFound() throws Exception {
            when(purchaseOrderService.getSuggestions(999L))
                    .thenThrow(new ResourceNotFoundException("Supplier", 999L));

            mockMvc.perform(get("/api/purchase-orders/suggestions").param("supplierId", "999")
                            .with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // Authentication required
    // ---------------------------------------------------------------

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/purchase-orders"))
                .andExpect(status().isUnauthorized());
    }
}
