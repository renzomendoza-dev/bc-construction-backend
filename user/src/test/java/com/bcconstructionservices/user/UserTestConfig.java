package com.bcconstructionservices.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;
import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com.bcconstructionservices.user.service\\..*",
                        // @WebMvcTest's controller-narrowing does not apply on top of this
                        // class's own @ComponentScan — every controller in the module gets
                        // scanned and registered together regardless of which one a given
                        // @WebMvcTest names, failing on the others' missing collaborators.
                        // Each @WebMvcTest must @Import its one target controller explicitly
                        // instead of relying on scanning to find it.
                        "com.bcconstructionservices.user.controller\\..*"
                }
        )
)
public class UserTestConfig {

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    // Matches the real SecurityConfig: this is a stateless Bearer-token API,
                    // so CSRF protection is meaningless here. Without disabling it, unauthenticated
                    // PATCH/POST/DELETE requests get rejected by CsrfFilter itself with a flat 403
                    // (bypassing ExceptionTranslationFilter's anonymous-aware 401 logic), instead of
                    // the intended 401 from .authenticated().
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
            return http.build();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            // Never actually invoked in tests: SecurityMockMvcRequestPostProcessors.jwt()
            // injects the Jwt directly into the SecurityContext, bypassing real decoding.
            // This bean exists purely to satisfy OAuth2ResourceServerConfigurer's wiring
            // requirement at context-build time.
            return mock(JwtDecoder.class);
        }
    }
}