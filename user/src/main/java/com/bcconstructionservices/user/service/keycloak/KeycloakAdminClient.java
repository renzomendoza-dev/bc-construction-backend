package com.bcconstructionservices.user.service.keycloak;

import com.bcconstructionservices.user.exception.KeycloakAdminApiException;
import com.bcconstructionservices.user.exception.KeycloakRoleNotFoundException;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakRoleRepresentation;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakTokenResponse;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakUserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Minimal hand-rolled client for the handful of Keycloak Admin REST API calls
 * this application needs (list/lookup realm roles, read/modify a user's realm
 * role mappings, read a user's profile for manual re-sync).
 * <p>
 * Deliberately built on Spring's synchronous {@link RestClient} rather than
 * the official {@code org.keycloak:keycloak-admin-client} library — that
 * library pulls in RESTEasy/Jakarta RS client machinery with real
 * compatibility risk against this codebase's very new Spring Boot 4.1 / Java
 * 25 / Spring Security 7 combination, for a feature that only needs a handful
 * of well-documented endpoints.
 * <p>
 * Authenticates via the client-credentials grant against a confidential
 * Keycloak client configured with a service account (see
 * {@code keycloak.admin.client-id}/{@code client-secret}); the resulting
 * access token is cached in memory until shortly before it expires.
 */
@Service
public class KeycloakAdminClient {

    private static final String REALMS_SEGMENT = "/realms/";

    private final RestClient restClient;
    private final String tokenUri;
    private final String adminBaseUri;
    private final String clientId;
    private final String clientSecret;

    private volatile CachedToken cachedToken;

    public KeycloakAdminClient(
            RestClient.Builder restClientBuilder,
            @Value("${keycloak.issuer-uri}") String issuerUri,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = issuerUri + "/protocol/openid-connect/token";

        int realmsIndex = issuerUri.indexOf(REALMS_SEGMENT);
        if (realmsIndex < 0) {
            throw new IllegalStateException(
                    "keycloak.issuer-uri is expected to contain '/realms/': " + issuerUri);
        }
        String server = issuerUri.substring(0, realmsIndex);
        String realm = issuerUri.substring(realmsIndex + REALMS_SEGMENT.length());
        this.adminBaseUri = server + "/admin/realms/" + realm;
    }

    public List<KeycloakRoleRepresentation> listRealmRoles() {
        return execute(() -> restClient.get()
                .uri(adminBaseUri + "/roles")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeycloakRoleRepresentation>>() {
                }));
    }

    public List<KeycloakRoleRepresentation> getUserRealmRoles(UUID keycloakId) {
        return execute(() -> restClient.get()
                .uri(adminBaseUri + "/users/{id}/role-mappings/realm", keycloakId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeycloakRoleRepresentation>>() {
                }));
    }

    public void assignRealmRole(UUID keycloakId, String roleName) {
        KeycloakRoleRepresentation role = getRoleByName(roleName);
        execute(() -> {
            restClient.post()
                    .uri(adminBaseUri + "/users/{id}/role-mappings/realm", keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public void revokeRealmRole(UUID keycloakId, String roleName) {
        KeycloakRoleRepresentation role = getRoleByName(roleName);
        execute(() -> {
            restClient.method(HttpMethod.DELETE)
                    .uri(adminBaseUri + "/users/{id}/role-mappings/realm", keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public KeycloakUserRepresentation getUserProfile(UUID keycloakId) {
        return execute(() -> restClient.get()
                .uri(adminBaseUri + "/users/{id}", keycloakId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(KeycloakUserRepresentation.class));
    }

    private KeycloakRoleRepresentation getRoleByName(String roleName) {
        try {
            return restClient.get()
                    .uri(adminBaseUri + "/roles/{roleName}", roleName)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(KeycloakRoleRepresentation.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new KeycloakRoleNotFoundException(roleName);
        } catch (RestClientException e) {
            throw new KeycloakAdminApiException("Failed to look up Keycloak role: " + roleName, e);
        }
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (KeycloakRoleNotFoundException e) {
            throw e;
        } catch (RestClientException e) {
            throw new KeycloakAdminApiException("Keycloak Admin API call failed", e);
        }
    }

    private String bearer() {
        return "Bearer " + getAccessToken();
    }

    private synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && cachedToken.expiresAt().isAfter(now)) {
            return cachedToken.accessToken();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        KeycloakTokenResponse response;
        try {
            response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
        } catch (RestClientException e) {
            throw new KeycloakAdminApiException("Failed to obtain a Keycloak admin access token", e);
        }
        if (response == null) {
            throw new KeycloakAdminApiException("Keycloak token endpoint returned an empty response");
        }

        cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(response.expiresIn() - 10));
        return cachedToken.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
