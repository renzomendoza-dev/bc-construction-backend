package com.bcconstructionservices.equipment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "equipment_assignment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "assigned_to_id", nullable = false)
    private Long assignedToId;

    /**
     * Warehouse (plain id, cross-module — see Equipment.currentWarehouseId's
     * javadoc) this assignment sent the equipment to at checkout. Set once,
     * at checkout, and never changed afterward.
     */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    /**
     * Warehouse this assignment's equipment was actually checked back in to.
     * Null until check-in; recorded here (not just on Equipment.currentWarehouseId)
     * so the return destination survives even after a later checkout overwrites
     * Equipment's current state — otherwise this assignment's own history would
     * be lost.
     */
    @Column(name = "return_warehouse_id")
    private Long returnWarehouseId;

    @Column(name = "checked_out_at", nullable = false)
    private Instant checkedOutAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "condition_out", length = 500)
    private String conditionOut;

    @Column(name = "condition_in", length = 500)
    private String conditionIn;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
