package com.bcconstructionservices.projects.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A construction project whose running cost (labor + material + other) is
 * tracked via {@link ProjectExpense} rows. Deliberately holds NO collection
 * of its expenses (no {@code @OneToMany}) — expenses are always created
 * independently, referencing an existing project by id, never cascaded from
 * this entity, so there's no reason to pay for the inverse side. This also
 * sidesteps the reentrant-auto-flush bug class documented in the repo-root
 * CLAUDE.md ("Reentrant auto-flush"): that bug requires an audited entity to
 * ALSO carry a cascade=ALL collection, and this one doesn't.
 *
 * <p>Deliberately independent of every other module (no {@code siteWarehouseId}
 * or similar cross-module reference yet) — kept that way for now to keep this
 * module's coupling minimal; add one later as a plain {@code Long} id, per
 * this codebase's established cross-module-reference convention, if a
 * project-to-site link turns out to be needed.
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /**
     * ACTIVE -&gt; ON_HOLD are both editable/expense-postable states;
     * COMPLETED/CANCELLED are terminal (see ProjectNotEditableException).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Without this, Hibernate 7 + H2Dialect infer a native ENUM JDBC type for
    // @Enumerated(STRING) fields, which doesn't match this column's actual
    // shape: plain VARCHAR + a hand-written CHECK constraint added via
    // Flyway — the same mismatch documented on Warehouse.type.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ACTIVE;

    /**
     * Optional planned budget, compared against the sum of this project's
     * expenses by ProjectService's summary computation. Null means "no
     * budget tracked" rather than zero.
     */
    @Column(name = "budget")
    private BigDecimal budget;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreatedBy
    @Column(name = "initiated_by", updatable = false)
    private Long initiatedBy;

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
