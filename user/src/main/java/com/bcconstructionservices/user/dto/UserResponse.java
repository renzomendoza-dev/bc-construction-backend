package com.bcconstructionservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned from {@code GET /users/me}.
 */
@Schema(
        description = "Represents the current authenticated user's local app profile " +
                "(BC Construction Services), distinct from their Keycloak identity record. " +
                "Identity, authentication, and role information live in Keycloak; this object " +
                "only reflects application-local profile data."
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    @Schema(description = "Local database identifier of the user's app profile.", example = "42")
    private Long id;

    @Schema(
            description = "Keycloak subject (\"sub\" claim) that links this local profile to the corresponding Keycloak identity.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    private UUID keycloakId;

    @Schema(description = "Display name synced from Keycloak's token claims on login.", example = "Jane Dela Cruz")
    private String fullName;

    @Schema(description = "Whether this app profile is active. Independent of the Keycloak account's enabled/disabled status.", example = "true")
    private boolean active;

    @Schema(description = "Timestamp when this local profile was first created.", example = "2026-01-15T08:30:00Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when this local profile was last updated.", example = "2026-07-20T14:12:00Z")
    private Instant updatedAt;
}