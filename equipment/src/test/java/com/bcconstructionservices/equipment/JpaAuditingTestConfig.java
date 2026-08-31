package com.bcconstructionservices.equipment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Stub auditor for repository slice tests. The real AuditorAwareImpl
 * (which resolves the Long app-local user ID from the JWT) lives in
 * user/app and is intentionally out of scope here — this test slice
 * only needs created_at/updated_at populated, not a real authenticated
 * user. Returns empty (rather than a fake id like 0L) since created_by/
 * updated_by are nullable and now FK-constrained to app_user — a fake id
 * would violate that FK the moment a real app_user table exists.
 *
 * Kept separate from {@link EquipmentTestApplication} (rather than
 * declaring @EnableJpaAuditing directly on it) because that class also
 * backs @WebMvcTest, which has no EntityManagerFactory — @EnableJpaAuditing
 * there fails with "JPA metamodel must not be empty" trying to build
 * jpaMappingContext. Only @Import this into tests that have a real JPA
 * context (e.g. @DataJpaTest).
 */
@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "testAuditorAware")
public class JpaAuditingTestConfig {

    @Bean
    public AuditorAware<Long> testAuditorAware() {
        return Optional::empty;
    }
}
