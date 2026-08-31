package com.bcconstructionservices.equipment.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch move of one or more pieces of equipment either out to a SITE
 * warehouse (assign-out) or back to a MAIN warehouse (return) — the
 * equipment-tracking analogue of inventory's TransferBatch. submit()
 * delegates per line to EquipmentService.checkOut/checkIn, the same
 * single-item logic already used and tested elsewhere, rather than
 * reimplementing status transitions here.
 *
 * <p>Direction isn't a separate stored field: it's derived from
 * destinationWarehouseId's resolved Warehouse.type (SITE = assign-out, MAIN
 * = return) at draft-creation time, and holderId's presence is validated
 * against that (required for SITE, must be null for MAIN).
 */
@Entity
@Table(name = "equipment_assignment_batch")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentAssignmentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EquipmentAssignmentBatchStatus status = EquipmentAssignmentBatchStatus.DRAFT;

    /**
     * Warehouse (plain id, cross-module — see Equipment.currentWarehouseId's
     * javadoc) this batch moves equipment to: a SITE warehouse for an
     * assign-out batch, a MAIN warehouse for a return batch.
     */
    @Column(name = "destination_warehouse_id", nullable = false)
    private Long destinationWarehouseId;

    /**
     * App-local user id taking custody, for an assign-out batch. Null for a
     * return batch — returning just clears custody on each line's equipment,
     * it doesn't assign it to anyone.
     */
    @Column(name = "holder_id")
    private Long holderId;

    @CreatedBy
    @Column(name = "initiated_by", updatable = false)
    private Long initiatedBy;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EquipmentAssignmentBatchLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
