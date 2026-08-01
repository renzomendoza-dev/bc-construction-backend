package com.bcconstructionservices.user.exception;

/**
 * Wraps any failure calling Keycloak's Admin REST API (network error,
 * unexpected non-2xx status, malformed response) that isn't specifically a
 * "role not found" — one consistent type for callers to handle regardless of
 * which underlying Keycloak call failed.
 */
public class KeycloakAdminApiException extends RuntimeException {
    public KeycloakAdminApiException(String message) {
        super(message);
    }

    public KeycloakAdminApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
