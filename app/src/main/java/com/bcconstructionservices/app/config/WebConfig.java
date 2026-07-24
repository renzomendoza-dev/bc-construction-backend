package com.bcconstructionservices.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    /**
     * @param allowedOrigins comma-separated origins from app.cors.allowed-origins;
     *                       Spring splits the value into the String[] and trims
     *                       each entry. Defaults to the common React (3000) and
     *                       Vite (5173) dev server ports.
     */
    public WebConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
            String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // IMPORTANT: exact origins, not "*". Credentials are enabled below, and
        // the CORS spec forbids a wildcard origin on credentialed requests -
        // Spring fails fast rather than silently misbehaving. If wildcard-ish
        // matching is genuinely needed (e.g. "https://*.example.com"), use
        // config.setAllowedOriginPatterns(...) instead - but note a bare "*"
        // pattern with credentials reflects ANY caller's origin back, which
        // effectively removes origin protection.
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // Cache preflight for an hour so the browser doesn't send an OPTIONS
        // request before every single API call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Scoped to the API surface only. Swagger UI is served same-origin, so
        // it needs no CORS policy; a cross-origin preflight to any other path
        // gets rejected, which is the desired default.
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui.html");
    }

}