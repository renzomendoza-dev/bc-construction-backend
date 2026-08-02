package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ReorderThresholdRequest;
import com.bcconstructionservices.inventory.dto.StockLevelResponse;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.InventoryService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for the PATCH /api/inventory/reorder-threshold
 * endpoint on InventoryController.
 *
 * <p>NOTE: This is a STANDALONE test class because no InventoryControllerTest
 * exists in the project to nest into. If you later create (or already have)
 * an InventoryControllerTest, lift the inner @Nested ReorderThresholdTests
 * class into it and delete this outer scaffolding.
 *
 * <p>ASSUMPTIONS (no InventoryController or GlobalExceptionHandler source was
 * available):
 * <ul>
 *   <li>InventoryController depends on a single InventoryService bean and maps
 *       this endpoint at PATCH /api/inventory/reorder-threshold.</li>
 *   <li>GlobalExceptionHandler's exact validation-error JSON shape is unknown,
 *       so the 400 tests assert the status precisely and only check that the
 *       offending field name appears somewhere in the raw response body
 *       (content().string(containsString(...))). Tighten to exact jsonPath
 *       assertions once the real error-body structure is confirmed.</li>
 *   <li>No Spring Security is assumed to be configured - if it is, add
 *       @AutoConfigureMockMvc(addFilters = false) or @WithMockUser.</li>
 * </ul>
 */
@WebMvcTest(InventoryController.class)
class InventoryControllerReorderThresholdTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private InventoryService inventoryService;

    /**
     * Authenticated JWT, optionally granted the given permission(s) (e.g.
     * "STOCK_SET_REORDER_THRESHOLD"). See EquipmentControllerTest for why
     * authorities are set directly via .authorities(...) rather than a
     * realm_access claim.
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

    @Nested
    class ReorderThresholdTests {

        private static final Long ITEM_ID = 42L;
        private static final Long WAREHOUSE_ID = 3L;
        private static final Long LOCATION_ID = 21L;

        // ---------------------------------------------------------------
        // Helpers
        // ---------------------------------------------------------------

        private ReorderThresholdRequest validRequest() {
            ReorderThresholdRequest req = new ReorderThresholdRequest();
            req.setItemId(ITEM_ID);
            req.setWarehouseId(WAREHOUSE_ID);
            req.setLocationId(LOCATION_ID);
            req.setReorderThreshold(30);
            return req;
        }

        private StockLevelResponse sampleResponse() {
            StockLevelResponse response = new StockLevelResponse();
            response.setItemId(ITEM_ID);
            response.setItemName("Portland Cement 40kg");
            response.setSku("SKU-001");
            response.setWarehouseId(WAREHOUSE_ID);
            response.setWarehouseName("Main Yard Warehouse");
            response.setLocationId(LOCATION_ID);
            response.setLocationCode("A-01-02");
            response.setQuantity(120);
            response.setReorderThreshold(30);
            return response;
        }

        // ---------------------------------------------------------------
        // Happy path
        // ---------------------------------------------------------------

        @Test
        void shouldReturn200WithUpdatedStockLevelForValidRequest() throws Exception {
            when(inventoryService.updateReorderThreshold(any(ReorderThresholdRequest.class)))
                    .thenReturn(sampleResponse());

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt("STOCK_SET_REORDER_THRESHOLD"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemId").value(42))
                    .andExpect(jsonPath("$.warehouseId").value(3))
                    .andExpect(jsonPath("$.reorderThreshold").value(30))
                    .andExpect(jsonPath("$.quantity").value(120));
        }

        @Test
        void shouldReturn403WhenCallerLacksStockSetReorderThresholdPermission() throws Exception {
            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn200WhenReorderThresholdIsZero() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setReorderThreshold(0);

            StockLevelResponse zeroThresholdResponse = sampleResponse();
            zeroThresholdResponse.setReorderThreshold(0);
            when(inventoryService.updateReorderThreshold(any(ReorderThresholdRequest.class)))
                    .thenReturn(zeroThresholdResponse);

            // Zero must be accepted by @PositiveOrZero, not rejected as invalid.
            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt("STOCK_SET_REORDER_THRESHOLD"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reorderThreshold").value(0));
        }

        @Test
        void shouldReturn200WhenLocationIdIsNull() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setLocationId(null);

            StockLevelResponse warehouseLevelResponse = sampleResponse();
            warehouseLevelResponse.setLocationId(null);
            warehouseLevelResponse.setLocationCode(null);
            when(inventoryService.updateReorderThreshold(any(ReorderThresholdRequest.class)))
                    .thenReturn(warehouseLevelResponse);

            // locationId is nullable - a warehouse-level threshold is valid.
            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt("STOCK_SET_REORDER_THRESHOLD"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // ---------------------------------------------------------------
        // Validation failures (400)
        // ---------------------------------------------------------------

        @Test
        void shouldReturn400WhenItemIdIsMissing() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setItemId(null);

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("itemId")));
        }

        @Test
        void shouldReturn400WhenWarehouseIdIsMissing() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setWarehouseId(null);

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("warehouseId")));
        }

        @Test
        void shouldReturn400WhenReorderThresholdIsMissing() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setReorderThreshold(null);

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("reorderThreshold")));
        }

        @Test
        void shouldReturn400WhenReorderThresholdIsNegative() throws Exception {
            ReorderThresholdRequest request = validRequest();
            request.setReorderThreshold(-5);

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("reorderThreshold")));
        }

        // ---------------------------------------------------------------
        // Not found (404)
        // ---------------------------------------------------------------

        @Test
        void shouldReturn404WhenNoMatchingStockRowExists() throws Exception {
            when(inventoryService.updateReorderThreshold(any(ReorderThresholdRequest.class)))
                    .thenThrow(new ResourceNotFoundException(
                            "No inventory stock found for item 42 at warehouse 3 location 21"));

            mockMvc.perform(patch("/api/inventory/reorder-threshold")
                            .with(authenticatedJwt("STOCK_SET_REORDER_THRESHOLD"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound());
        }
    }
}