package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.*;
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

import java.time.Instant;

@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = true)
    private StorageLocation fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = true)
    private StorageLocation toLocation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType type;

    /**
     * Net effect of this row on ITS OWN warehouse's stock level — set
     * explicitly by InventoryService at construction time, never inferred
     * later. See {@link MovementDirection}'s own javadoc for why inferring
     * this from fromLocation/toLocation nullability doesn't work in general.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    // Without this, Hibernate 7 + H2Dialect infer a native ENUM JDBC type for
    // @Enumerated(STRING) fields, which doesn't match this column's actual
    // shape: plain VARCHAR + a hand-written CHECK constraint added via
    // Flyway — the same mismatch documented on Warehouse.type. Forcing
    // VARCHAR here binds it as a plain string, matching the real column.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", nullable = false)
    private MovementDirection direction;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reason")
    private String reason;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
