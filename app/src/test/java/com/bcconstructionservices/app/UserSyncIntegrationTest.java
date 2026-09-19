package com.bcconstructionservices.app;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.service.UserSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local AppUser creation against the real application and Postgres: the
 * UserSyncInterceptor that creates the row on every /api/** request, and
 * UserSyncService's atomic insert under concurrent first requests.
 *
 * <p>Deliberately not @Transactional: both behaviors depend on real commits
 * across separate transactions. Rows created here are deleted afterward,
 * since every context in this module shares one database.
 */
@SpringBootTest(properties = {
        "keycloak.issuer-uri=http://localhost:1/realms/test",
        "keycloak.admin.client-id=test",
        "keycloak.admin.client-secret=test"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class UserSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserSyncService userSyncService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Set<UUID> createdKeycloakIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void deleteCreatedRows() {
        for (UUID keycloakId : createdKeycloakIds) {
            jdbcTemplate.update("DELETE FROM worker WHERE created_by = "
                    + "(SELECT id FROM app_user WHERE keycloak_id = ?)", keycloakId);
            jdbcTemplate.update("DELETE FROM app_user WHERE keycloak_id = ?", keycloakId);
        }
    }

    private UUID newUser() {
        UUID keycloakId = UUID.randomUUID();
        createdKeycloakIds.add(keycloakId);
        return keycloakId;
    }

    private static Jwt tokenFor(UUID keycloakId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakId.toString())
                .claim("name", "New Field Engineer")
                .build();
    }

    private Integer rowsFor(UUID keycloakId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE keycloak_id = ?", Integer.class, keycloakId);
    }

    @Test
    void aBrandNewUsersFirstRequestCreatesTheirAccountAndIsAttributedToThem() throws Exception {
        UUID keycloakId = newUser();

        // The very first request this user ever makes is a write — no prior
        // GET /api/users/me to create their account.
        mockMvc.perform(post("/api/workers")
                        .with(jwt().jwt(tokenFor(keycloakId))
                                .authorities(new SimpleGrantedAuthority("ROLE_WORKER_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Sync Test Worker\", \"dailyRate\": 800}"))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE keycloak_id = ?", Long.class, keycloakId);
        Long createdBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM worker WHERE name = 'Sync Test Worker'", Long.class);
        assertThat(createdBy).isEqualTo(userId);
    }

    @Test
    void concurrentFirstSyncsForTheSameNewUserCreateExactlyOneRowWithoutErrors() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 10; round++) {
                UUID keycloakId = newUser();
                Jwt token = tokenFor(keycloakId);
                CountDownLatch start = new CountDownLatch(1);
                List<Throwable> failures = new CopyOnWriteArrayList<>();
                List<Future<Long>> ids = new ArrayList<>();

                for (int t = 0; t < threads; t++) {
                    ids.add(pool.submit(() -> {
                        start.await();
                        try {
                            AppUser user = userSyncService.syncFromToken(token);
                            return user.getId();
                        } catch (Throwable ex) {
                            failures.add(ex);
                            return null;
                        }
                    }));
                }
                start.countDown();

                List<Long> resolved = new ArrayList<>();
                for (Future<Long> id : ids) {
                    resolved.add(id.get());
                }

                assertThat(failures).as("round %d failures", round).isEmpty();
                assertThat(resolved).as("round %d ids", round).doesNotContainNull().containsOnly(resolved.get(0));
                assertThat(rowsFor(keycloakId)).as("round %d rows", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
