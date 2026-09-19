-- V24: equipment_assignment_batch, equipment_assignment_batch_line
-- Batch checkout/check-in for one or more pieces of equipment in one
-- transaction, the equipment-tracking analogue of inventory's transfer_batch.

CREATE TABLE equipment_assignment_batch (
    id                       BIGSERIAL PRIMARY KEY,
    status                   VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT equipment_assignment_batch_status_check
            CHECK (status IN ('DRAFT', 'SUBMITTED', 'COMPLETED')),
    destination_warehouse_id BIGINT NOT NULL REFERENCES warehouse (id),
    holder_id                BIGINT REFERENCES app_user (id),
    initiated_by             BIGINT REFERENCES app_user (id),
    notes                    VARCHAR(500),
    created_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_equipment_assignment_batch_status ON equipment_assignment_batch (status);
CREATE INDEX idx_equipment_assignment_batch_destination_warehouse
    ON equipment_assignment_batch (destination_warehouse_id);

CREATE TABLE equipment_assignment_batch_line (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        BIGINT NOT NULL REFERENCES equipment_assignment_batch (id),
    equipment_id    BIGINT NOT NULL REFERENCES equipment (id),
    condition_notes VARCHAR(500)
);

CREATE INDEX idx_equipment_assignment_batch_line_batch ON equipment_assignment_batch_line (batch_id);
