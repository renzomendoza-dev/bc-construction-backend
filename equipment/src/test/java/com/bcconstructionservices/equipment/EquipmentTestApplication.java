package com.bcconstructionservices.equipment;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
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
