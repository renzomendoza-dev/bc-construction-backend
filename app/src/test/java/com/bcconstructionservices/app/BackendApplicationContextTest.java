package com.bcconstructionservices.app;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real application — every module wired together — against the
 * embedded Postgres from test-support. The only test that does: every other
 * suite loads one module's slice, so cross-module startup failures (duplicate
 * bean names, migration-order problems, dev seed data that no longer fits the
 * schema, Hibernate validate mismatches) otherwise only show up on a real start.
 *
 * <p>The dev profile is active so db/dev-data seeds are applied on top of the
 * schema migrations. Keycloak settings are placeholders: nothing contacts
 * Keycloak until a request needs a token.
 */
@SpringBootTest(properties = {
        "keycloak.issuer-uri=http://localhost:1/realms/test",
        "keycloak.admin.client-id=test",
        "keycloak.admin.client-secret=test"
})
@ActiveProfiles("dev")
class BackendApplicationContextTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesEverySchemaMigrationAndDevSeedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(applied).allSatisfy(migration -> assertThat(migration.getState().isFailed()).isFalse());
        assertThat(Arrays.stream(applied).map(MigrationInfo::getScript))
                .anyMatch(script -> script.startsWith("V21__seed_dev_inventory_data"))
                .anyMatch(script -> script.startsWith("V30__seed_dev_workers_data"));
    }
}
