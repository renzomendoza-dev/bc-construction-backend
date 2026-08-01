package com.bcconstructionservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Keycloak realm role, as exposed to admin clients (e.g. for a role picker).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleResponse {

    @Schema(description = "Keycloak-internal id of the realm role.", example = "9c5b1e3a-1234-4a1b-9abc-1234567890ab")
    private String id;

    @Schema(description = "Realm role name.", example = "ADMIN")
    private String name;

    @Schema(description = "Realm role description, if one was set in Keycloak.", example = "Full administrative access")
    private String description;
}
