package com.bcconstructionservices.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Cross-cutting Spring Security configuration for BC Construction Services.
 * <p>
 * This application is a pure OAuth2 Resource Server: it never issues tokens
 * and never stores credentials. Keycloak is the sole identity provider and
 * the sole source of truth for authentication (login/passwords) and
 * authorization (realm roles). On every request, this app only validates
 * the incoming Bearer JWT (signature, issuer, expiry) and derives the
 * caller's authorities from claims already present in that token.
 * <p>
 * There are intentionally no endpoints here for registration, login,
 * password reset, or token issuance — those flows belong entirely to
 * Keycloak and must never be reimplemented in this backend.
 * <p>
 * JWT validation relies on Spring Boot's auto-configured {@code JwtDecoder},
 * driven by the standard {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * property (backed internally by {@code keycloak.issuer-uri} or equivalent
 * environment-specific config). This is the conventional approach for
 * Spring Boot 3.x/4.x resource servers — Spring Boot automatically builds
 * the decoder from the issuer's OIDC discovery document, so no manual
 * {@code JwtDecoder} bean is defined here unless issuer-specific overrides
 * become necessary later.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/actuator/health"
    };

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter) {
        this.keycloakJwtAuthenticationConverter = keycloakJwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)));

        return http.build();
    }
}