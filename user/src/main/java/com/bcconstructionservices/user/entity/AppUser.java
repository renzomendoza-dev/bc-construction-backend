package com.bcconstructionservices.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Local, app-specific profile for a user of BC Construction Services.
 * <p>
 * This entity is <strong>not</strong> an identity or authentication record. All
 * identity, authentication, and authorization concerns (credentials, email,
 * password, and roles/permissions) are owned and managed exclusively by
 * Keycloak. This row exists purely to hold application-local data associated
 * with a Keycloak identity, keyed by {@link #keycloakId}, which corresponds to
 * the {@code sub} claim of the Keycloak-issued JWT.
 * <p>
 * Fields such as {@link #fullName} are synced from the JWT's claims on login
 * and cached locally so the application can display or join on user data
 * without calling Keycloak's admin API on every request. Roles are never
 * persisted here — they are read at request time from the JWT's
 * {@code realm_access.roles} claim via Spring Security.
 * <p>
 * The {@link #active} flag is an application-level soft-deactivation switch,
 * independent of whether the underlying Keycloak account itself is enabled
 * or disabled.
 */
@Entity
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_user_keycloak_id", columnNames = "keycloak_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The Keycloak subject ("sub" claim) that links this local profile to the
     * corresponding identity in Keycloak.
     */
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private UUID keycloakId;

    /**
     * Display name synced from Keycloak's token claims on every login. Kept
     * locally purely for fast display/joins without calling Keycloak's admin API.
     */
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    /**
     * Application-level soft-deactivation flag. Independent of whether the
     * Keycloak account itself is enabled or disabled.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}