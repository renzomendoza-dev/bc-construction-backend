package com.bcconstructionservices.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA Auditing, backed by {@link AuditorAwareImpl}.
 * <p>
 * Any entity in any module that wants automatic {@code created_by} /
 * {@code updated_by} (and optionally {@code created_at} / {@code updated_at})
 * population should:
 * <pre>
 *   {@literal @}EntityListeners(AuditingEntityListener.class)   // on the entity class or a shared @MappedSuperclass
 *   class SomeEntity {
 *
 *       {@literal @}CreatedBy
 *       private Long createdBy;
 *
 *       {@literal @}LastModifiedBy
 *       private Long updatedBy;
 *
 *       {@literal @}CreatedDate
 *       private Instant createdAt;      // omit if timestamps are already set manually, e.g. via @PrePersist/@PreUpdate
 *
 *       {@literal @}LastModifiedDate
 *       private Instant updatedAt;      // omit if timestamps are already set manually, e.g. via @PrePersist/@PreUpdate
 *   }
 * </pre>
 * {@code createdBy} / {@code updatedBy} are populated with the local
 * {@code app_user.id} (a {@code Long}) resolved by {@link AuditorAwareImpl},
 * never the raw Keycloak subject UUID.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}