package com.bcconstructionservices.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void convert_withMultipleRealmRoles_mapsToUppercasedRoleAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "user-123")
                .claim("realm_access", Map.of("roles", List.of("admin", "staff")))
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "ROLE_STAFF");
    }

    @Test
    void convert_withMixedCaseRole_uppercasesRoleAuthority() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "user-123")
                .claim("realm_access", Map.of("roles", List.of("StAfF")))
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_STAFF");
    }

    @Test
    void convert_withMissingRealmAccessClaim_doesNotThrowAndHasNoRoleAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "user-123")
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .isEmpty();
    }

    @Test
    void convert_withEmptyRealmRolesList_hasNoRoleAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "user-123")
                .claim("realm_access", Map.of("roles", Collections.emptyList()))
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .isEmpty();
    }

    @Test
    void convert_returnsJwtAuthenticationTokenWrappingOriginalJwt() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "user-123")
                .claim("realm_access", Map.of("roles", List.of("admin")))
                .build();

        // Act
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Assert
        assertThat(result).isInstanceOf(JwtAuthenticationToken.class);
        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) result;
        assertThat(jwtAuthenticationToken.getToken()).isEqualTo(jwt);
    }
}