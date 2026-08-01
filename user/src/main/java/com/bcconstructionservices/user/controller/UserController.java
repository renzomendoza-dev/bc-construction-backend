package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.mapper.UserMapper;
import com.bcconstructionservices.user.repository.UserRepository;
import com.bcconstructionservices.user.service.UserSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the current authenticated user's local app profile.
 * <p>
 * This controller does not return the user's full Keycloak account details
 * (credentials, roles, or any other identity/auth data) — Keycloak remains
 * the sole source of truth for that. It returns only the local
 * {@code app_user} row associated with the caller's Keycloak identity,
 * syncing it (e.g. refreshing {@code fullName}) from the current JWT on
 * every call so the local copy never goes stale.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User")
public class UserController {

    private final UserSyncService userSyncService;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    /**
     * Returns every active local user profile, e.g. for populating an
     * assignment or equipment checkout picker.
     * <p>
     * Deliberately unpaginated and unfiltered beyond {@code active}: the
     * local user table is expected to stay small (one row per person who has
     * ever logged in), so a picker can just load the whole list client-side.
     *
     * @return active local user profiles, mapped to {@link UserResponse}
     */
    @GetMapping
    @Operation(
            summary = "List active users",
            description = "Returns every active local user profile (id, keycloakId, fullName, " +
                    "active, createdAt, updatedAt). Intended for populating pickers — e.g. " +
                    "selecting who to check equipment out to — not as a full identity directory; " +
                    "credentials, email, and roles remain Keycloak-only."
    )
    public List<UserResponse> findAll() {
        return userRepository.findByActiveTrue().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    /**
     * Returns the current authenticated user's local app profile, syncing it
     * from their Keycloak-issued JWT first.
     *
     * @param jwt the validated JWT of the currently authenticated user
     * @return the synced local app profile, mapped to {@link UserResponse}
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current user's local app profile",
            description = "Syncs and returns the authenticated user's local app-specific profile " +
                    "(id, keycloakId, fullName, active, createdAt, updatedAt), derived from their " +
                    "Keycloak-issued JWT. This is not the user's full Keycloak identity record — " +
                    "credentials, email, and roles are managed exclusively by Keycloak and are not " +
                    "included in this response."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Current user's local app profile returned successfully."),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token; rejected by Spring Security before reaching this endpoint.")
    })
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        AppUser appUser = userSyncService.syncFromToken(jwt);
        UserResponse response = userMapper.toResponse(appUser);
        return ResponseEntity.ok(response);
    }
}