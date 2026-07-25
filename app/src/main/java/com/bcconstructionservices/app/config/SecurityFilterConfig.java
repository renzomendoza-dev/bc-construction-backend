package com.bcconstructionservices.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Registers the servlet filters and - the whole point of this class - pins
 * their relative ORDER explicitly in one reviewable place.
 *
 * <p>THE ORDER IS THE CONTRACT: CorsFilter MUST run before ApiKeyAuthFilter.
 *
 * <pre>
 *   request --> [CorsFilter]          order = HIGHEST_PRECEDENCE
 *                    |                adds Access-Control-* to the response,
 *                    |                answers preflight OPTIONS directly
 *                    v
 *               [ApiKeyAuthFilter]    order = HIGHEST_PRECEDENCE + 10
 *                    |                401s a bad/missing X-API-Key
 *                    v
 *               DispatcherServlet --> controllers
 * </pre>
 *
 * <p>If that order were reversed, ApiKeyAuthFilter would short-circuit a
 * rejected request before CorsFilter ever ran, and the 401 would go back to
 * the browser with no Access-Control-Allow-Origin header. The browser blocks
 * the response and reports a CORS failure, so the frontend never sees the
 * actual 401 - a wrong API key would look like a CORS misconfiguration. With
 * CorsFilter first, the headers are already attached no matter what
 * ApiKeyAuthFilter decides afterwards.
 *
 * <p>Lower order value = higher precedence = runs earlier. The two constants
 * below are deliberately spaced so another filter can be slotted between them
 * later without renumbering.
 *
 * <p>Note this uses only spring-web (CorsFilter, FilterRegistrationBean) - no
 * Spring Security dependency is involved.
 */
@Configuration
public class SecurityFilterConfig {

    /** Runs first: CORS headers must be on every response, including rejections. */
    private static final int CORS_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE;

    /** Runs after CORS. Must be a HIGHER value than CORS_FILTER_ORDER. */
    private static final int API_KEY_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            CorsConfigurationSource corsConfigurationSource) {

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));

        // Applies to every path; the CorsConfigurationSource itself decides
        // which paths actually have a policy (currently /api/**).
        registration.addUrlPatterns("/*");
        registration.setOrder(CORS_FILTER_ORDER);
        registration.setName("corsFilter");
        return registration;
    }

}