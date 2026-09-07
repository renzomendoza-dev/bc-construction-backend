package com.bcconstructionservices.workers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One worker's presence on one project on one day. {@code worker} is a real
 * {@code @ManyToOne} (same module as Worker), but {@code projectId} is a
 * plain {@code Long} — {@code Project} lives in a different module, so this
 * follows the established cross-module-reference convention (plain id + a
 * {@code ProjectLookupHelper} for display-name resolution) rather than a JPA
 * relation.
 * <p>
 * Recording an Attendance row auto-creates a LABOR {@code ProjectExpense} on
 * the referenced project (amount = rateSnapshot * daysPresent) via a direct
 * call to {@code ProjectExpenseService.addExpense} — {@code projectExpenseId}
 * traces back to it, so deleting this Attendance row can find and delete the
 * expense it generated. That id is stored here (on the workers side) rather
 * than as a reverse column on ProjectExpense, deliberately: it keeps the FK
 * pointing the same direction as the Maven dependency (workers -&gt; projects)
 * and adds no schema-level dependency from projects back onto workers,
 * preserving projects' documented independence.
 */
@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @NotNull
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @NotNull
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @NotNull
    @Column(name = "days_present", nullable = false)
    private BigDecimal daysPresent;

    /**
     * Copied from {@code worker.dailyRate} at the moment this row is
     * created — see Worker's own javadoc for why.
     */
    @NotNull
    @Column(name = "rate_snapshot", nullable = false)
    private BigDecimal rateSnapshot;

    @Column(name = "notes")
    private String notes;

    /**
     * Only ever set via the batch attendance endpoint (AttendanceService#createBatch),
     * which derives daysPresent from these — a single POST /api/attendance
     * record leaves both null, exactly as before this field existed.
     */
    @Column(name = "time_in")
    private LocalTime timeIn;

    @Column(name = "time_out")
    private LocalTime timeOut;

    @Column(name = "project_expense_id")
    private Long projectExpenseId;

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
