package com.bcconstructionservices.user.service.keycloak.dto;

/**
 * Keycloak Admin API's wire format for a realm role. Internal to
 * {@code KeycloakAdminClient} — never exposed directly as a public API DTO.
 */
public record KeycloakRoleRepresentation(String id, String name, String description) {
}
