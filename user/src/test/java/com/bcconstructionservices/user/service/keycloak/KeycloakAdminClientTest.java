package com.bcconstructionservices.user.service.keycloak;

import com.bcconstructionservices.user.exception.KeycloakAdminApiException;
import com.bcconstructionservices.user.exception.KeycloakRoleNotFoundException;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakRoleRepresentation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link KeycloakAdminClient} against a {@link MockRestServiceServer}
 * bound to its {@link RestClient.Builder} — no real Keycloak instance involved.
 */
class KeycloakAdminClientTest {

    private static final String ISSUER_URI = "http://keycloak.test/realms/myrealm";
    private static final String TOKEN_URI = ISSUER_URI + "/protocol/openid-connect/token";
    private static final String ADMIN_BASE_URI = "http://keycloak.test/admin/realms/myrealm";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    private void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder, ISSUER_URI, "test-client-id", "test-client-secret");
    }

    private void expectTokenFetch(String accessToken, long expiresInSeconds) {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresInSeconds + "}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void listRealmRoles_happyPath_returnsRoles() {
        setUp();
        expectTokenFetch("tok1", 300);
        server.expect(requestTo(ADMIN_BASE_URI + "/roles"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok1"))
                .andRespond(withSuccess(
                        "[{\"id\":\"r1\",\"name\":\"ADMIN\",\"description\":\"Full access\"}]",
                        MediaType.APPLICATION_JSON));

        List<KeycloakRoleRepresentation> roles = client.listRealmRoles();

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).name()).isEqualTo("ADMIN");
        server.verify();
    }

    @Test
    void accessToken_isCachedAcrossCalls_tokenEndpointHitOnlyOnce() {
        setUp();
        expectTokenFetch("tok1", 300);
        server.expect(requestTo(ADMIN_BASE_URI + "/roles"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_BASE_URI + "/roles"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.listRealmRoles();
        client.listRealmRoles();

        server.verify();
    }

    @Test
    void assignRealmRole_roleNotFound_throwsKeycloakRoleNotFoundException() {
        setUp();
        expectTokenFetch("tok1", 300);
        server.expect(requestTo(ADMIN_BASE_URI + "/roles/BOGUS"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        UUID keycloakId = UUID.randomUUID();
        assertThatThrownBy(() -> client.assignRealmRole(keycloakId, "BOGUS"))
                .isInstanceOf(KeycloakRoleNotFoundException.class);
        server.verify();
    }

    @Test
    void listRealmRoles_upstream500_throwsKeycloakAdminApiException() {
        setUp();
        expectTokenFetch("tok1", 300);
        server.expect(requestTo(ADMIN_BASE_URI + "/roles"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.listRealmRoles())
                .isInstanceOf(KeycloakAdminApiException.class);
        server.verify();
    }

    @Test
    void assignRealmRole_happyPath_postsRoleMapping() {
        setUp();
        expectTokenFetch("tok1", 300);
        UUID keycloakId = UUID.randomUUID();
        server.expect(requestTo(ADMIN_BASE_URI + "/roles/MANAGER"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"r2\",\"name\":\"MANAGER\",\"description\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_BASE_URI + "/users/" + keycloakId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok1"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.assignRealmRole(keycloakId, "MANAGER");

        server.verify();
    }

    @Test
    void revokeRealmRole_happyPath_deletesRoleMapping() {
        setUp();
        expectTokenFetch("tok1", 300);
        UUID keycloakId = UUID.randomUUID();
        server.expect(requestTo(ADMIN_BASE_URI + "/roles/MANAGER"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"r2\",\"name\":\"MANAGER\",\"description\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN_BASE_URI + "/users/" + keycloakId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.revokeRealmRole(keycloakId, "MANAGER");

        server.verify();
    }

    @Test
    void getUserProfile_happyPath_returnsProfile() {
        setUp();
        expectTokenFetch("tok1", 300);
        UUID keycloakId = UUID.randomUUID();
        server.expect(requestTo(ADMIN_BASE_URI + "/users/" + keycloakId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"" + keycloakId + "\",\"username\":\"jdoe\",\"firstName\":\"Jane\","
                                + "\"lastName\":\"Doe\",\"enabled\":true}",
                        MediaType.APPLICATION_JSON));

        var profile = client.getUserProfile(keycloakId);

        assertThat(profile.firstName()).isEqualTo("Jane");
        assertThat(profile.lastName()).isEqualTo("Doe");
        server.verify();
    }
}
