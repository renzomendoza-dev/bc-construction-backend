package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.mapper.UserMapper;
import com.bcconstructionservices.user.service.UserSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Note on security testing approach: @WebMvcTest auto-configures Spring Security's
 * test slice (SecurityAutoConfiguration), which by default requires authentication
 * for all requests. We do NOT need to @Import a real OAuth2 resource server
 * SecurityConfig here because SecurityMockMvcRequestPostProcessors.jwt() from
 * spring-security-test injects a fully-formed JwtAuthenticationToken directly into
 * the request's SecurityContext for that request, independent of whether the actual
 * oauth2ResourceServer().jwt() filter chain is wired up in this slice context. This
 * is the standard/idiomatic pattern for testing JWT-secured controllers under
 * @WebMvcTest without pulling in the full application security configuration.
 * Unauthenticated requests (no jwt() post-processor) are rejected by the default
 * "authenticated()" rule from the auto-configured test security filter chain,
 * resulting in a 401.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSyncService userSyncService;

    @MockitoBean
    private UserMapper userMapper;

    @Test
    void getCurrentUser_authenticatedRequest_returns200WithUserResponse() throws Exception {
        // Arrange
        UUID keycloakId = UUID.randomUUID();
        Instant now = Instant.now();

        AppUser appUser = AppUser.builder()
                .id(1L)
                .keycloakId(keycloakId)
                .fullName("Jane Doe")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        UserResponse userResponse = new UserResponse(
                1L,
                keycloakId,
                "Jane Doe",
                true,
                now,
                now
        );

        when(userSyncService.syncFromToken(any(Jwt.class))).thenReturn(appUser);
        when(userMapper.toResponse(appUser)).thenReturn(userResponse);

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(builder -> builder.claim("sub", keycloakId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.keycloakId").value(keycloakId.toString()))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getCurrentUser_unauthenticatedRequest_returns401() throws Exception {
        // Arrange
        // No jwt() post-processor applied -> request has no authentication

        // Act & Assert
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}