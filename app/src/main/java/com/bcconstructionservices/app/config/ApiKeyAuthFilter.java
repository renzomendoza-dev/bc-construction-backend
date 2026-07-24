package com.bcconstructionservices.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rejects requests that don't present a valid {@code X-API-Key} header.
 *
 * <p>Deliberately NOT annotated with @Component: it's instantiated and
 * registered by {@link SecurityFilterConfig} via a FilterRegistrationBean.
 * Doing both would register the filter TWICE (once by component scanning into
 * the servlet container, once by the registration bean), so it would run twice
 * per request. This project uses the registration bean, because it gives
 * explicit control over URL patterns and, critically, ordering relative to
 * the CorsFilter.
 *
 * <p>ORDERING: this filter runs AFTER the CorsFilter (see SecurityFilterConfig).
 * That matters because this filter short-circuits the chain on a bad key -
 * it never reaches DispatcherServlet, so Spring MVC's CORS handling would
 * never run on a 401 response. With CorsFilter ahead of it, the
 * Access-Control-Allow-Origin header is already on the response before this
 * filter can reject it, so the browser surfaces a real 401 to the frontend
 * instead of masking it as an opaque CORS error.
 *
 * <p>SCOPE OF PROTECTION: this is a shared static secret, which is reasonable
 * for a single trusted frontend or internal service, but it is materially
 * weaker than real authentication - there's no per-client identity, no
 * revocation of one caller without rotating for everyone, and no expiry. The
 * key travels on every request, so this must only be used over HTTPS in any
 * deployed environment; over plain HTTP it's readable by anything on the
 * network path. If per-user access or audit trails are ever needed, replace
 * this with Spring Security + JWT/OAuth rather than extending it.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private static final String UNAUTHORIZED_BODY =
            "{\"error\": \"Unauthorized\", \"message\": \"Missing or invalid API key\"}";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    /**
     * Paths that bypass the API key check entirely.
     *
     * <p>The filter is ALSO scoped to /api/* by its registration (see
     * SecurityFilterConfig), so in the current setup these are a second layer
     * rather than the only thing keeping Swagger reachable. They're kept so
     * the filter stays correct on its own if that URL pattern is ever widened.
     *
     * <p>The OPTIONS bypass is handled in doFilterInternal rather than here,
     * so the reason for it stays next to the code it protects.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // Root redirect to Swagger UI.
        // MUST be an exact equals - path.startsWith("/") would match every
        // request on the server and silently disable authentication entirely.
        if ("/".equals(path)) {
            return true;
        }

        // Swagger UI (/swagger-ui.html plus its /swagger-ui/** webjar assets)
        // and the OpenAPI descriptor (/api-docs, /api-docs/swagger-config, ...).
        return path.startsWith("/swagger-ui") || path.startsWith("/api-docs");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // --- CORS preflight bypass: no API key check, any path. -------------
        //
        // A browser's preflight OPTIONS request does NOT carry custom headers -
        // it only ANNOUNCES them via Access-Control-Request-Headers - so it can
        // never present X-API-Key. Checking the key here would 401 every
        // preflight, the browser would never send the real request, and CORS
        // would look broken from the frontend while curl/Postman (which don't
        // preflight) kept working.
        //
        // With the current ordering, CorsFilter runs first and TERMINATES true
        // preflight requests itself, so this branch is mostly a safety net -
        // it still matters for non-preflight OPTIONS (an OPTIONS request with
        // no Origin header isn't a CORS request, so CorsFilter passes it down
        // the chain to here), and it keeps this filter correct on its own if
        // the filter ordering is ever changed.
        //
        // Safe to allow: OPTIONS is a metadata/discovery method that exposes no
        // inventory data, and the actual GET/POST/PATCH/DELETE that follows a
        // preflight is still fully checked below.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (!isValid(providedKey)) {
            rejectUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Constant-time comparison via MessageDigest.isEqual, rather than
     * String.equals. String.equals short-circuits on the first differing
     * character, so response timing leaks how many leading characters of a
     * guess were correct - enough to recover a key byte-by-byte given enough
     * requests. (Length remains observable, an acceptable leak for a
     * fixed-length key.)
     */
    private boolean isValid(String providedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                providedKey.getBytes(StandardCharsets.UTF_8),
                expectedApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Advertise the expected scheme so clients get a usable hint.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + API_KEY_HEADER + "\"");
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}