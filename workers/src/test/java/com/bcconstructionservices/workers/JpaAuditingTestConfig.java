package com.bcconstructionservices.workers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Stub auditor for repository slice tests — mirrors equipment's
 * JpaAuditingTestConfig. Kept separate from WorkersTestApplication (rather
 * than declaring @EnableJpaAuditing directly on it) because that class also
 * backs @WebMvcTest, which has no EntityManagerFactory. Only @Import this
 * into tests that have a real JPA context (e.g. @DataJpaTest).
 */
@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "testAuditorAware")
public class JpaAuditingTestConfig {

    @Bean
    public AuditorAware<Long> testAuditorAware() {
        return Optional::empty;
    }
}
