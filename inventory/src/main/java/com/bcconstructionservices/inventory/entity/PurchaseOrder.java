package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order placed with a supplier before anything has physically arrived —
 * an earlier stage than PurchaseReceipt, which assumes goods are already in
 * hand. One PurchaseOrder can have multiple PurchaseReceipts created against
 * it over time (PurchaseReceipt.purchaseOrderId), covering partial delivery.
 *
 * <p>DRAFT is freely editable (lines can be added/removed/adjusted).
 * Submitting locks line items — once a supplier has the order, changing
 * quantities without telling them is misleading, same reasoning a real
 * purchase order has. PARTIALLY_RECEIVED/RECEIVED are derived automatically
 * from confirmed receipts against this PO (see PurchaseReceiptService);
 * CLOSED is a manual terminal state for when the remaining shortfall isn't
 * coming (supplier discontinued an item, order was over-cautious, etc.) —
 * deliberately distinct from RECEIVED so "fully delivered" and "abandoned
 * short" aren't conflated into one terminal status.
 */
@Entity
@Table(name = "purchase_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "notes")
    private String notes;

    @CreatedBy
    @Column(name = "initiated_by", updatable = false)
    private Long initiatedBy;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

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
