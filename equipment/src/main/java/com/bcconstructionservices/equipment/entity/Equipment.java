package com.bcconstructionservices.equipment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "equipment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_tag", nullable = false, unique = true, length = 50)
    private String assetTag;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    @Column(name = "current_holder_id")
    private Long currentHolderId;

    /**
     * References Warehouse (inventory module) by plain id, not a JPA
     * association — Warehouse lives in a different module. Real DB-level
     * FK regardless (see V24), since equipment already does this same
     * cross-module-FK-without-a-Java-relationship thing for current_holder_id
     * -> app_user. SITE-typed while checked out, MAIN once returned; always
     * populated for equipment created after V24, but nullable to
     * accommodate rows that existed before this column did (their old
     * free-text current_site couldn't be reliably mapped to a real
     * Warehouse row) — those self-heal to non-null on their next
     * checkout/checkin cycle, which now requires and sets this field.
     */
    @Column(name = "current_warehouse_id")
    private Long currentWarehouseId;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "purchase_price", precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_vendor", length = 150)
    private String purchaseVendor;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}