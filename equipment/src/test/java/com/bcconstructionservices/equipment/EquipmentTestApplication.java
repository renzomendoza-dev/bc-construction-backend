package com.bcconstructionservices.equipment;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;
import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication(scanBasePackages = "com.bcconstructionservices.equipment")
public class EquipmentTestApplication {

    /**
     * Mirrors user module's UserTestConfig.TestSecurityConfig: no
     * issuer-uri/jwk-set-uri is configured for this test slice, so the real
     * OAuth2ResourceServerAutoConfiguration can't build a JwtDecoder on its
     * own. Provide a minimal filter chain + mocked JwtDecoder purely to
     * satisfy wiring — SecurityMockMvcRequestPostProcessors.jwt() injects
     * the Jwt directly into the SecurityContext, bypassing real decoding.
     */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    // Matches the real SecurityConfig: stateless Bearer-token API, so CSRF is
                    // meaningless here. Without disabling it, unauthenticated PATCH/POST
                    // requests get rejected by CsrfFilter with a flat 403 instead of the
                    // intended 401 from .authenticated().
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
            return http.build();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
