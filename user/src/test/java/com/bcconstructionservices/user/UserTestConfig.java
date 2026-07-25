package com.bcconstructionservices.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
                pattern = "com.bcconstructionservices.user.service\\..*"
        )
)
public class UserTestConfig {

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
            // Never actually invoked in tests: SecurityMockMvcRequestPostProcessors.jwt()
            // injects the Jwt directly into the SecurityContext, bypassing real decoding.
            // This bean exists purely to satisfy OAuth2ResourceServerConfigurer's wiring
            // requirement at context-build time.
            return mock(JwtDecoder.class);
        }
    }
}