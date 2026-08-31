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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A site's request for materials to be pulled from a MAIN warehouse. `site`
 * must reference a Warehouse of type SITE — enforced in MaterialRequestService,
 * not at the DB level, since a CHECK constraint can't validate a joined row's
 * column. Fulfillment happens by creating a TransferBatch with
 * sourceMaterialRequestId set and submitting it — there is no fulfill()
 * method on this entity/service.
 */
@Entity
@Table(name = "material_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_warehouse_id", nullable = false)
    private Warehouse site;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "date_needed")
    private LocalDate dateNeeded;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MaterialRequestStatus status = MaterialRequestStatus.DRAFT;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "materialRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaterialRequestLineItem> lineItems = new ArrayList<>();

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
