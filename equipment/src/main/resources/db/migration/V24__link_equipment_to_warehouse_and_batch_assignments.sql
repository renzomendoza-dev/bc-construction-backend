-- V24: equipment/equipment_assignment location -> real Warehouse FK,
-- plus equipment_assignment_batch(_line) for batch checkout/check-in.
--
-- current_warehouse_id/warehouse_id/return_warehouse_id/destination_warehouse_id
-- are plain BIGINT columns on the Java side (no @ManyToOne — Warehouse lives
-- in the inventory module, a different module from equipment's own entities),
-- but real FKs at the DB level here: warehouse already exists (created in
-- inventory's own V6, long before this migration), so unlike
-- transfer_batch.source_material_request_id's "plain column, no FK" (which
-- was purely about migration ORDER), there's no ordering reason to skip the
-- FK, and this table already uses real FKs for its other cross-module
-- reference (current_holder_id/assigned_to_id -> app_user).

-- Equipment: replace free-text current_site with a real FK to warehouse.
-- Existing free-text values can't be reliably mapped to a real Warehouse row,
-- so this starts NULL for every existing row (AVAILABLE equipment already had
-- no tracked location; CHECKED_OUT/IN_USE equipment loses its old site text)
-- - each self-heals to non-null on its next checkout/checkin cycle, which now
-- requires and sets this column.
ALTER TABLE equipment DROP COLUMN current_site;
ALTER TABLE equipment ADD COLUMN current_warehouse_id BIGINT REFERENCES warehouse (id);

CREATE INDEX idx_equipment_current_warehouse ON equipment (current_warehouse_id);

-- equipment_assignment: same treatment for its per-cycle site field, plus a
-- new return_warehouse_id recording where equipment was actually checked
-- back in to (previously untracked entirely - the whole reason for this work
-- - so this column stays null for assignment rows closed before this
-- migration, and populated for every check-in from here on).
ALTER TABLE equipment_assignment DROP COLUMN site;
ALTER TABLE equipment_assignment ADD COLUMN warehouse_id BIGINT REFERENCES warehouse (id);
ALTER TABLE equipment_assignment ADD COLUMN return_warehouse_id BIGINT REFERENCES warehouse (id);

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
