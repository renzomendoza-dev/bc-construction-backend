package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.AdminUserResponse;
import com.bcconstructionservices.user.dto.PageResponse;
import com.bcconstructionservices.user.dto.RoleAssignmentRequest;
import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.exception.UserNotFoundException;
import com.bcconstructionservices.user.service.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @PreAuthorize("hasRole('ADMIN')")} is only enforced in this test
 * slice because {@code UserTestConfig.TestSecurityConfig} has
 * {@code @EnableMethodSecurity}. {@code SecurityMockMvcRequestPostProcessors
 * .jwt()} uses a default {@code JwtGrantedAuthoritiesConverter} (scope-based),
 * NOT the app module's {@code KeycloakJwtAuthenticationConverter} (which this
 * module can't reference without a circular dependency) — so admin/non-admin
 * is simulated directly via {@code .authorities(...)} rather than a
 * {@code realm_access} claim.
 */
@WebMvcTest(AdminUserController.class)
@Import(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // NOT @Autowired: under Jackson 3.x (Spring Boot 4.1's default), the registered
    // bean is tools.jackson.databind.json.JsonMapper, not
    // com.fasterxml.jackson.databind.ObjectMapper. This test only needs to serialize
    // simple request DTOs to JSON strings for MockMvc bodies, so a plain local
    // instance is sufficient and sidesteps the bean-registration mismatch entirely.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AdminUserService adminUserService;

    private static RequestPostProcessor asAdmin() {
        return jwt().jwt(builder -> builder.claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor asNonAdmin() {
        return jwt().jwt(builder -> builder.claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ---------------------------------------------------------------
    // GET /api/admin/users
    // ---------------------------------------------------------------

    @Test
    void listUsers_admin_returns200() throws Exception {
        PageResponse<UserResponse> page = PageResponse.<UserResponse>builder()
                .content(List.of(UserResponse.builder().id(1L).fullName("Jane Doe").build()))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(adminUserService.listUsers(isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listUsers_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // GET /api/admin/users/{userId}
    // ---------------------------------------------------------------

    @Test
    void getUserDetail_admin_returns200() throws Exception {
        AdminUserResponse response = AdminUserResponse.builder()
                .id(42L).fullName("Jane Doe").active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .realmRoles(List.of("ADMIN"))
                .build();
        when(adminUserService.getUserDetail(42L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/users/{userId}", 42L).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42L))
                .andExpect(jsonPath("$.realmRoles[0]").value("ADMIN"));
    }

    @Test
    void getUserDetail_notFound_returns404() throws Exception {
        when(adminUserService.getUserDetail(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/api/admin/users/{userId}", 999L).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserDetail_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users/{userId}", 42L).with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserDetail_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users/{userId}", 42L))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // PATCH /api/admin/users/{userId}/activate
    // ---------------------------------------------------------------

    @Test
    void activateUser_admin_returns204() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/activate", 42L).with(asAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void activateUser_nonAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/activate", 42L).with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void activateUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/activate", 42L))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // PATCH /api/admin/users/{userId}/deactivate
    // ---------------------------------------------------------------

    @Test
    void deactivateUser_admin_returns204() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/deactivate", 42L).with(asAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateUser_nonAdmin_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{userId}/deactivate", 42L).with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/admin/users/{userId}/roles
    // ---------------------------------------------------------------

    @Test
    void assignRole_admin_returns204() throws Exception {
        RoleAssignmentRequest request = RoleAssignmentRequest.builder().roleName("MANAGER").build();

        mockMvc.perform(post("/api/admin/users/{userId}/roles", 42L)
                        .with(asAdmin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void assignRole_blankRoleName_returns400() throws Exception {
        String invalidBody = "{\"roleName\":\"\"}";

        mockMvc.perform(post("/api/admin/users/{userId}/roles", 42L)
                        .with(asAdmin())
                        .contentType("application/json")
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignRole_nonAdmin_returns403() throws Exception {
        RoleAssignmentRequest request = RoleAssignmentRequest.builder().roleName("MANAGER").build();

        mockMvc.perform(post("/api/admin/users/{userId}/roles", 42L)
                        .with(asNonAdmin())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // DELETE /api/admin/users/{userId}/roles/{roleName}
    // ---------------------------------------------------------------

    @Test
    void revokeRole_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}/roles/{roleName}", 42L, "MANAGER").with(asAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokeRole_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{userId}/roles/{roleName}", 42L, "MANAGER").with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // POST /api/admin/users/{userId}/resync
    // ---------------------------------------------------------------

    @Test
    void resyncUser_admin_returns200() throws Exception {
        AdminUserResponse response = AdminUserResponse.builder()
                .id(42L).fullName("Refreshed Name").active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .realmRoles(List.of())
                .build();
        when(adminUserService.resyncUser(eq(42L), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/users/{userId}/resync", 42L).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Refreshed Name"));
    }

    @Test
    void resyncUser_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/resync", 42L).with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void resyncUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/resync", 42L))
                .andExpect(status().isUnauthorized());
    }
}
