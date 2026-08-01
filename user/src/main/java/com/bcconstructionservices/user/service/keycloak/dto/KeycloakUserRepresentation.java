package com.bcconstructionservices.user.service.keycloak.dto;

/**
 * Keycloak Admin API's wire format for a user profile. Internal to
 * {@code KeycloakAdminClient} — never exposed directly as a public API DTO.
 */
public record KeycloakUserRepresentation(
        String id,
        String username,
        String firstName,
        String lastName,
        boolean enabled) {
}
