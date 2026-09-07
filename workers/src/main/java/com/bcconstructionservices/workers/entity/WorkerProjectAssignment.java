package com.bcconstructionservices.workers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.time.Instant;

/**
 * Which project a worker's crew currently belongs to — feeds the attendance
 * calendar/batch form (which workers to list for a given project) rather
 * than gating anything: {@code AttendanceService} doesn't check this before
 * recording attendance for a worker, same as it never has.
 * <p>
 * At most one active assignment per worker at a time — enforced only at the
 * application layer ({@code WorkerProjectAssignmentService.assign}'s
 * {@code existsByWorkerIdAndActiveTrue} pre-check, 409 on violation), not by
 * a DB constraint: a partial unique index (the natural DB-level enforcement)
 * isn't portable to H2's PostgreSQL-compatibility mode, which every
 * module's test suite runs against — see V32's own comment. A worker moving
 * crews means deactivating the old assignment first, then creating a new
 * one; {@code assign} rejects (409) rather than silently reassigning,
 * matching {@code EquipmentService.checkOut}'s precedent of rejecting an
 * already-checked-out item instead of an implicit transfer.
 * <p>
 * {@code worker} is a real {@code @ManyToOne} (same module), but
 * {@code projectId} is a plain {@code Long} — same cross-module-reference
 * convention as {@code Attendance.projectId}.
 */
@Entity
@Table(name = "worker_project_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class WorkerProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private Long projectId;

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
