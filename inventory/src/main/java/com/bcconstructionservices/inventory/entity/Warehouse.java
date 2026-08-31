package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "warehouse")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Distinguishes a MAIN distribution warehouse from a construction SITE.
     * A site is deliberately just a Warehouse row with type = SITE, not a
     * separate entity — this lets every existing stock/movement mechanism
     * work for sites unchanged.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Without this, Hibernate 7 + H2Dialect infer a native ENUM JDBC type for
    // @Enumerated(STRING) fields (H2 supports native ENUM columns), which
    // doesn't match this column's actual shape: plain VARCHAR + a hand-written
    // CHECK constraint added via Flyway. That mismatch made H2 reject every
    // insert with "Check constraint invalid" even for valid values. Forcing
    // VARCHAR here binds it as a plain string, matching the real column.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false)
    @Builder.Default
    private WarehouseType type = WarehouseType.MAIN;

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
