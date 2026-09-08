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

// Scans com.bcconstructionservices.projects too (not just workers) — needed
// so a real @SpringBootTest context can wire the actual ProjectLookupHelper/
// ProjectService/ProjectExpenseService/ProjectRepository beans workers'
// services call directly (see the repo-root CLAUDE.md's "Cross-module write
// orchestration"), rather than every test either mocking them or bypassing
// them via direct EntityManager persistence — the exact gap that let a real
// GET /api/attendance/calendar 500 ship without any test catching it (see
// AttendanceServiceCalendarIntegrationTest). Safe for @DataJpaTest slices:
// those restrict to JPA-only beans regardless of scan breadth, so this
// doesn't spin up projects' controllers/services there.
@SpringBootApplication(scanBasePackages = {
        "com.bcconstructionservices.workers",
        "com.bcconstructionservices.projects"
})
@EntityScan(basePackages = {
        "com.bcconstructionservices.workers.entity",
        "com.bcconstructionservices.user.entity",
        "com.bcconstructionservices.projects.entity"
})
// Deliberately NO @EnableJpaRepositories here (see CrossModuleJpaRepositoriesTestConfig
// for why it can't live on this shared class): Boot's auto-configured JPA
// repository scanning only activates when a DataSource/EntityManagerFactory
// bean is actually present, so it's silently a no-op for @WebMvcTest (which
// never wires one) — but an explicit @EnableJpaRepositories annotation is
// unconditional and fires regardless of test slice, so putting it directly on
// this class broke every @WebMvcTest in the module with
// NoSuchBeanDefinitionException: No bean named 'entityManagerFactory'
// available (confirmed via the actual full-reactor stack trace, not guessed).
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
