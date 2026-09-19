package com.bcconstructionservices.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Points every Spring context in a test JVM at one real, throwaway Postgres
 * (Zonky embedded binaries, no Docker) instead of H2, which can't catch
 * Postgres-only failures like the missing-CAST 500s in CLAUDE.md. Registered
 * via META-INF/spring.factories, so no test class needs to opt in. All contexts
 * in the JVM share the instance, the same way they shared one named H2 database.
 */
public class EmbeddedPostgresEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static EmbeddedPostgres postgres;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        EmbeddedPostgres db = instance();
        environment.getPropertySources().addFirst(new MapPropertySource("embeddedPostgres", Map.of(
                "spring.datasource.url", db.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "",
                "spring.datasource.driver-class-name", "org.postgresql.Driver",
                // Default for every @DataJpaTest, so none can silently swap in another database.
                "spring.test.database.replace", "none")));
    }

    private static synchronized EmbeddedPostgres instance() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.start();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to start embedded Postgres for tests", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // JVM is exiting; the temp data directory is cleaned up either way.
                }
            }));
        }
        return postgres;
    }
}
