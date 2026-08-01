package com.bcconstructionservices.user.exception;

public class KeycloakRoleNotFoundException extends RuntimeException {
    public KeycloakRoleNotFoundException(String roleName) {
        super("Keycloak realm role not found: " + roleName);
    }
}
