package com.bcconstructionservices.workers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A field laborer on the roster — deliberately independent of {@code AppUser}:
 * every {@code AppUser} is tied to a Keycloak login (see {@code UserResponse}),
 * and field laborers tracked here don't need app access. Holds no collection
 * of its {@link Attendance} rows (no {@code @OneToMany}) — same reasoning as
 * {@code Project}: attendance is always created independently, never
 * cascaded from this entity, which also sidesteps the reentrant-auto-flush
 * bug class documented in the repo-root CLAUDE.md.
 */
@Entity
@Table(name = "worker")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position")
    private String position;

    /**
     * Snapshotted onto each Attendance row at the moment it's recorded (see
     * Attendance.rateSnapshot) so a later rate change here never retroactively
     * changes the amount of a past ProjectExpense.
     */
    @NotNull
    @Column(name = "daily_rate", nullable = false)
    private BigDecimal dailyRate;

    /**
     * Soft-retire flag — a worker with existing Attendance history is never
     * hard-deletable, matching Item/Warehouse/Supplier's deactivate convention.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

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
