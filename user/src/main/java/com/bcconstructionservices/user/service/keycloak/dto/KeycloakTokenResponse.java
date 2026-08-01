package com.bcconstructionservices.user.service.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from Keycloak's {@code protocol/openid-connect/token}
 * endpoint (client-credentials grant). Internal to
 * {@code KeycloakAdminClient} — never exposed directly as a public API DTO.
 */
public record KeycloakTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn) {
}
