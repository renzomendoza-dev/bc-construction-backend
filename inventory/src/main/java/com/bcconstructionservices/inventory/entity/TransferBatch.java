package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch move of one or more items from one warehouse (origin) to another
 * (destination) — the mechanism behind both a straight site pull-out and
 * fulfilling a MaterialRequest. Origin/destination are plain Warehouse rows;
 * a "site" is just a Warehouse with type = SITE, so no separate Site entity
 * or parallel stock/movement machinery is needed here — TransferBatchService
 * drives the existing InventoryService.transferStock per line item on submit.
 */
@Entity
@Table(name = "transfer_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_warehouse_id", nullable = false)
    private Warehouse originWarehouse;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransferBatchStatus status = TransferBatchStatus.DRAFT;

    /**
     * App-local user id of whoever initiated this batch. Set explicitly by
     * TransferBatchService from CurrentUserService, not via @CreatedBy —
     * unlike StockMovement.createdBy, this needs to be set at draft-creation
     * time (before submit), not necessarily inside a Spring Data auditing
     * write.
     */
    @Column(name = "initiated_by")
    private Long initiatedBy;

    /**
     * Set when this batch fulfills a MaterialRequest; null for a straight
     * pull-out with no originating request. Plain column, not a real FK —
     * material_request is created in a later migration than transfer_batch.
     */
    @Column(name = "source_material_request_id")
    private Long sourceMaterialRequestId;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "transferBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransferLineItem> lineItems = new ArrayList<>();

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
