package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.AdminUserResponse;
import com.bcconstructionservices.user.dto.ErrorResponse;
import com.bcconstructionservices.user.dto.PageResponse;
import com.bcconstructionservices.user.dto.RoleAssignmentRequest;
import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for managing users: listing/detail (including
 * inactive users), activation, and Keycloak realm role assignment. All
 * endpoints require the Keycloak realm role {@code ADMIN}.
 */
@RestController
@RequestMapping(value = "/api/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Admin-only user listing, activation, and role management")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(
            summary = "List all users (admin)",
            description = "Returns a paged list of all users, including inactive ones, optionally filtered "
                    + "by active status. Unlike GET /api/users, this is not limited to active users only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of users",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @Parameter(description = "Filter by active status", example = "true")
            @RequestParam(required = false) Boolean active,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(active, pageable));
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "Get a user's admin detail",
            description = "Returns a user's local profile plus their current Keycloak realm roles, "
                    + "fetched live from Keycloak."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User detail",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminUserResponse> getUserDetail(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }

    @PatchMapping("/{userId}/activate")
    @Operation(
            summary = "Activate a user",
            description = "Sets the user's local active flag to true. Independent of the underlying "
                    + "Keycloak account's enabled/disabled status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User activated"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> activateUser(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt adminJwt) {
        adminUserService.activateUser(userId, adminJwt);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/deactivate")
    @Operation(
            summary = "Deactivate a user",
            description = "Sets the user's local active flag to false. Independent of the underlying "
                    + "Keycloak account's enabled/disabled status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deactivated"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deactivateUser(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt adminJwt) {
        adminUserService.deactivateUser(userId, adminJwt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/roles")
    @Operation(
            summary = "Assign a Keycloak realm role to a user",
            description = "Calls Keycloak's Admin API to add the named realm role to the user's role mappings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role assigned"),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> assignRole(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId,
            @Valid @RequestBody RoleAssignmentRequest request,
            @AuthenticationPrincipal Jwt adminJwt) {
        adminUserService.assignRole(userId, request.getRoleName(), adminJwt);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    @Operation(
            summary = "Revoke a Keycloak realm role from a user",
            description = "Calls Keycloak's Admin API to remove the named realm role from the user's role mappings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Role revoked"),
            @ApiResponse(responseCode = "404", description = "User or role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> revokeRole(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId,
            @Parameter(description = "Name of the realm role to revoke", example = "MANAGER")
            @PathVariable String roleName,
            @AuthenticationPrincipal Jwt adminJwt) {
        adminUserService.revokeRole(userId, roleName, adminJwt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/resync")
    @Operation(
            summary = "Re-sync a user's profile from Keycloak",
            description = "Fetches the user's current profile from Keycloak's Admin API and refreshes "
                    + "the local fullName on demand, without waiting for their next login."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refreshed user detail",
                    content = @Content(schema = @Schema(implementation = AdminUserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminUserResponse> resyncUser(
            @Parameter(description = "Local identifier of the user", example = "42")
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt adminJwt) {
        return ResponseEntity.ok(adminUserService.resyncUser(userId, adminJwt));
    }
}
