package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.*;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_receipt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PurchaseReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /**
     * Warehouse this receipt's stock is received into once confirmed.
     * Required at creation time even though stock isn't applied until confirmation,
     * since a receipt can only ever be received into one warehouse.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @NotNull
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    /**
     * Relative path to scanned receipt file. No binary data stored.
     */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "notes")
    private String notes;

    /**
     * Set when this receipt is purchasing the shortfall for a TransferBatch
     * that failed to submit on insufficient stock (status AWAITING_PURCHASE);
     * null for a receipt with no such origin. Real FK — unlike
     * TransferBatch.sourceMaterialRequestId, there's no migration-order
     * constraint here (transfer_batch already existed when this column was
     * added), and this table already uses real FKs for its other
     * cross-references (supplier, warehouse, created_by, confirmed_by).
     */
    @Column(name = "fulfills_transfer_batch_id")
    private Long fulfillsTransferBatchId;

    /**
     * Set when this receipt is (at least partially) fulfilling a
     * PurchaseOrder placed with the same supplier in advance; null for a
     * receipt with no such origin. Independent of fulfillsTransferBatchId —
     * a receipt can carry either, both, or neither. Real FK, same reasoning
     * as fulfillsTransferBatchId.
     */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Whether this receipt has been applied to inventory yet. Receipts start as
     * drafts (false) when created and can only be confirmed once; confirming
     * twice would double-apply stock, so this flag guards that.
     */
    @NotNull
    @Column(name = "confirmed", nullable = false)
    @Builder.Default
    private boolean confirmed = false;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @OneToMany(mappedBy = "purchaseReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseReceiptLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}