package com.bcconstructionservices.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One piece of equipment in an EquipmentAssignmentBatch. Equipment lives in
 * this same module, so — unlike the batch's cross-module warehouse/holder
 * ids — this is a real JPA association.
 */
@Entity
@Table(name = "equipment_assignment_batch_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentAssignmentBatchLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private EquipmentAssignmentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    /**
     * conditionOut if this line's batch is an assign-out, conditionIn if a
     * return — which one applies is contextual on the batch's direction,
     * matching the single-item conditionOut/conditionIn fields this
     * ultimately feeds into (see EquipmentAssignmentBatchService.submit).
     */
    @Column(name = "condition_notes", length = 500)
    private String conditionNotes;
}
