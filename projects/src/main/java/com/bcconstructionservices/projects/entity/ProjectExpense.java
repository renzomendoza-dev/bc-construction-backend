package com.bcconstructionservices.projects.entity;

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
import jakarta.persistence.PrePersist;
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
 * One recorded cost against a Project — a single shape with a
 * {@code category} discriminator (LABOR/MATERIAL/OTHER) rather than three
 * separate entities, matching this codebase's preference for deriving/
 * folding a concept into one field when a separate type per case would
 * otherwise duplicate the same structure (amount, description, date, who
 * recorded it). MATERIAL entries are recorded manually here for now —
 * deliberately NOT linked to inventory's PurchaseReceipt/PurchaseOrder yet,
 * to keep this module independent (see Project's own javadoc).
 */
@Entity
@Table(name = "project_expense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ProjectExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", nullable = false)
    private ExpenseCategory category;

    @NotNull
    @Column(name = "description", nullable = false)
    private String description;

    @NotNull
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /**
     * When the cost was actually incurred — not necessarily the same day
     * it's recorded (createdAt covers that).
     */
    @NotNull
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @CreatedBy
    @Column(name = "recorded_by", updatable = false)
    private Long recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
