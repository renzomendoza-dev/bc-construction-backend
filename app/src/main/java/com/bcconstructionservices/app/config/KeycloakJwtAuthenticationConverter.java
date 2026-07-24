package com.bcconstructionservices.app.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a validated Keycloak-issued {@link Jwt} into a Spring Security
 * {@link AbstractAuthenticationToken}, deriving granted authorities from
 * Keycloak's realm roles.
 * <p>
 * Keycloak is treated as the sole source of truth for identity and roles.
 * This converter does not look up or store roles locally in any way — it
 * purely reads the {@code realm_access.roles} claim already embedded in the
 * token by Keycloak at issuance time, and maps each role to a Spring
 * {@code GrantedAuthority} of the form {@code ROLE_<ROLE_NAME_UPPERCASED>}
 * (e.g. Keycloak role {@code admin} becomes {@code ROLE_ADMIN}).
 * <p>
 * These role-derived authorities are combined with the default
 * scope/scp-based authorities produced by {@link JwtGrantedAuthoritiesConverter},
 * so both realm roles and OAuth2 scopes remain available for authorization
 * decisions (e.g. {@code @PreAuthorize}).
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                        defaultAuthoritiesConverter.convert(jwt).stream(),
                        extractRealmRoleAuthorities(jwt).stream())
                .collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoleAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }

        Object rolesClaim = realmAccess.get(ROLES_CLAIM);
        if (!(rolesClaim instanceof List<?> rawRoles)) {
            return List.of();
        }

        return rawRoles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}