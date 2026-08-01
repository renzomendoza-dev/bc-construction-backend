package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.RoleResponse;
import com.bcconstructionservices.user.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRoleController.class)
@Import(AdminRoleController.class)
class AdminRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void listRoles_admin_returns200() throws Exception {
        when(adminUserService.listAvailableRoles())
                .thenReturn(List.of(RoleResponse.builder().id("r1").name("ADMIN").build()));

        mockMvc.perform(get("/api/admin/roles").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("ADMIN"));
    }

    @Test
    void listRoles_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/roles").with(asNonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isUnauthorized());
    }
}
