package com.bcconstructionservices.user.controller;

import com.bcconstructionservices.user.dto.RoleResponse;
import com.bcconstructionservices.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only endpoint for listing available Keycloak realm roles, e.g. to
 * populate a role picker. Requires the Keycloak realm role {@code ADMIN}.
 */
@RestController
@RequestMapping(value = "/api/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Admin - Roles", description = "Admin-only listing of available Keycloak realm roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(
            summary = "List available Keycloak realm roles",
            description = "Fetches every realm role defined in Keycloak, e.g. to populate an admin role picker."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Realm roles",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoleResponse.class))))
    })
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(adminUserService.listAvailableRoles());
    }
}
