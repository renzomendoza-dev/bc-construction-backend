package com.bcconstructionservices.workers;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
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

@SpringBootApplication(scanBasePackages = "com.bcconstructionservices.workers")
@EntityScan(basePackages = {
        "com.bcconstructionservices.workers.entity",
        "com.bcconstructionservices.user.entity",
        // Project (projects module) is persisted directly by repository-slice
        // tests exercising attendance.project_id's real FK — workers.entity
        // itself only ever holds a plain Long id for it, never a @ManyToOne,
        // so this scan entry exists purely for the tests.
        "com.bcconstructionservices.projects.entity"
})
public class WorkersTestApplication {

    /**
     * Mirrors the other modules' TestSecurityConfig: no issuer-uri/jwk-set-uri
     * is configured for this test slice, so the real
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
