package com.bcconstructionservices.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transfer_line_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_batch_id", nullable = false)
    private TransferBatch transferBatch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /**
     * Populated when this line represents a counted/audited pull-out; null
     * for a dispatch line where there's nothing to count against.
     */
    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "notes")
    private String notes;

    /**
     * Traces this line back to the MATERIAL ProjectExpense it auto-generated
     * on submit, if the batch's projectId was set. Null until
     * submit actually runs (a DRAFT line has no expense yet), and stays null
     * forever if the batch was never project-linked. See TransferBatch's own
     * javadoc for why this traceability id lives here rather than as a
     * reverse column on ProjectExpense.
     */
    @Column(name = "project_expense_id")
    private Long projectExpenseId;
}
