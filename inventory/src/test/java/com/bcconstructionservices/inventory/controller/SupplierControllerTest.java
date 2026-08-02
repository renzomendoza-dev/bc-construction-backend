package com.bcconstructionservices.inventory.controller;

import com.bcconstructionservices.inventory.dto.ItemSupplierRequest;
import com.bcconstructionservices.inventory.dto.ItemSupplierResponse;
import com.bcconstructionservices.inventory.dto.SupplierCreateRequest;
import com.bcconstructionservices.inventory.dto.SupplierResponse;
import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.inventory.service.SupplierService;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice tests for SupplierController.
 *
 * <p>ASSUMPTIONS (no SupplierController, SupplierService, or
 * GlobalExceptionHandler source was provided):
 * <ul>
 *   <li>SupplierController depends on a single {@code SupplierService} bean
 *       that handles both supplier CRUD and item-supplier linking, with
 *       methods createSupplier(SupplierCreateRequest),
 *       getSupplierById(Long), linkItemToSupplier(ItemSupplierRequest), and
 *       getSuppliersForItem(Long) — inferred directly from the endpoint
 *       list (all under /api/suppliers), not confirmed against real
 *       source. If linking is actually handled by a separate
 *       ItemSupplierService, split LinkItemTests / GetSuppliersForItemTests
 *       out to mock that bean instead.</li>
 *   <li>GlobalExceptionHandler's exact JSON shape for validation errors is
 *       unknown, so field-error assertions check the HTTP status precisely
 *       (400) and only assert that the invalid field's name appears
 *       somewhere in the raw response body, same treatment as
 *       ItemControllerTest — see that class's Javadoc for the full
 *       rationale.</li>
 *   <li>No Spring Security is assumed to be configured — if it is, add
 *       {@code @AutoConfigureMockMvc(addFilters = false)} or appropriate
 *       {@code @WithMockUser} setup.</li>
 * </ul>
 */
@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SupplierService supplierService;

    /**
     * Authenticated JWT, optionally granted the given permission(s) (e.g.
     * "SUPPLIER_CREATE"). See EquipmentControllerTest for why authorities
     * are set directly via .authorities(...) rather than a realm_access claim.
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

    private SupplierCreateRequest validCreateRequest() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setName("Luzon Steel Trading");
        request.setContactInfo("sales@luzonsteel.ph / +63 917 555 0142");
        return request;
    }

    private SupplierResponse sampleSupplierResponse() {
        SupplierResponse response = new SupplierResponse();
        response.setId(7L);
        response.setName("Luzon Steel Trading");
        response.setContactInfo("sales@luzonsteel.ph / +63 917 555 0142");
        response.setActive(true);
        response.setCreatedAt(Instant.parse("2025-11-05T02:00:00Z"));
        response.setUpdatedAt(Instant.parse("2025-11-05T02:00:00Z"));
        return response;
    }

    private ItemSupplierRequest validLinkRequest() {
        ItemSupplierRequest request = new ItemSupplierRequest();
        request.setItemId(42L);
        request.setSupplierId(7L);
        request.setSupplierSku("LST-CEM-40");
        request.setUnitCost(new BigDecimal("238.25"));
        return request;
    }

    private ItemSupplierResponse sampleLinkResponse() {
        ItemSupplierResponse response = new ItemSupplierResponse();
        response.setId(101L);
        response.setItemId(42L);
        response.setItemName("Portland Cement 40kg");
        response.setSupplierId(7L);
        response.setSupplierName("Luzon Steel Trading");
        response.setSupplierSku("LST-CEM-40");
        response.setUnitCost(new BigDecimal("238.25"));
        return response;
    }

    // ---------------------------------------------------------------
    // POST /api/suppliers
    // ---------------------------------------------------------------

    @Nested
    class CreateSupplierTests {

        @Test
        void shouldReturn201WithCreatedSupplierForValidRequest() throws Exception {
            when(supplierService.createSupplier(any(SupplierCreateRequest.class)))
                    .thenReturn(sampleSupplierResponse());

            mockMvc.perform(post("/api/suppliers")
                            .with(authenticatedJwt("SUPPLIER_CREATE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.name").value("Luzon Steel Trading"));
        }

        @Test
        void shouldReturn400WhenNameIsBlank() throws Exception {
            SupplierCreateRequest request = validCreateRequest();
            request.setName("");

            mockMvc.perform(post("/api/suppliers")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("name")));
        }

        @Test
        void shouldReturn403WhenCallerLacksSupplierCreatePermission() throws Exception {
            mockMvc.perform(post("/api/suppliers")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn401WhenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/suppliers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/suppliers/{supplierId}
    // ---------------------------------------------------------------

    @Nested
    class GetSupplierByIdTests {

        @Test
        void shouldReturn200WithSupplierWhenFound() throws Exception {
            when(supplierService.getSupplierById(7L)).thenReturn(sampleSupplierResponse());

            mockMvc.perform(get("/api/suppliers/{supplierId}", 7L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.name").value("Luzon Steel Trading"));
        }

        @Test
        void shouldReturn404WhenSupplierNotFound() throws Exception {
            when(supplierService.getSupplierById(999L))
                    .thenThrow(new ResourceNotFoundException("Supplier not found: 999"));

            mockMvc.perform(get("/api/suppliers/{supplierId}", 999L).with(authenticatedJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/suppliers/link-item
    // ---------------------------------------------------------------

    @Nested
    class LinkItemTests {

        @Test
        void shouldReturn400WhenItemIdIsMissing() throws Exception {
            ItemSupplierRequest request = validLinkRequest();
            request.setItemId(null);

            mockMvc.perform(post("/api/suppliers/link-item")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("itemId")));
        }

        @Test
        void shouldReturn400WhenUnitCostIsNegative() throws Exception {
            ItemSupplierRequest request = validLinkRequest();
            request.setUnitCost(new BigDecimal("-5.00"));

            mockMvc.perform(post("/api/suppliers/link-item")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("unitCost")));
        }

        @Test
        void shouldReturn201WithLinkedItemSupplierForValidRequest() throws Exception {
            when(supplierService.linkItemToSupplier(any(ItemSupplierRequest.class)))
                    .thenReturn(sampleLinkResponse());

            mockMvc.perform(post("/api/suppliers/link-item")
                            .with(authenticatedJwt("SUPPLIER_LINK_ITEM"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLinkRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemId").value(42))
                    .andExpect(jsonPath("$.supplierId").value(7))
                    .andExpect(jsonPath("$.unitCost").value(238.25));
        }

        @Test
        void shouldReturn403WhenCallerLacksSupplierLinkItemPermission() throws Exception {
            mockMvc.perform(post("/api/suppliers/link-item")
                            .with(authenticatedJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLinkRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/suppliers/for-item/{itemId}
    // ---------------------------------------------------------------

    @Nested
    class GetSuppliersForItemTests {

        @Test
        void shouldReturn200WithListOfSuppliersForItem() throws Exception {
            when(supplierService.getSuppliersForItem(42L)).thenReturn(List.of(sampleLinkResponse()));

            mockMvc.perform(get("/api/suppliers/for-item/{itemId}", 42L).with(authenticatedJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].supplierId").value(7))
                    .andExpect(jsonPath("$[0].supplierName").value("Luzon Steel Trading"));
        }
    }
}