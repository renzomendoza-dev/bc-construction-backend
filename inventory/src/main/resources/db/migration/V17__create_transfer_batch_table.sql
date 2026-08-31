-- V17: transfer_batch
-- A batch move of one or more items from one warehouse (origin) to another
-- (destination). A "site" is just a warehouse with type = SITE (see V16),
-- so origin/destination both reference the same warehouse table regardless
-- of MAIN/SITE. origin_warehouse_id/destination_warehouse_id are real FKs
-- (Warehouse lives in this same module); source_material_request_id is a
-- plain nullable column, not a real FK, since material_request doesn't
-- exist until a later migration (V19) - matches this codebase's existing
-- convention of plain Long references for cross-aggregate pointers (e.g.
-- stock_movement.created_by -> app_user).

CREATE TABLE transfer_batch (
    id                          BIGSERIAL                    PRIMARY KEY,
    origin_warehouse_id         BIGINT                        NOT NULL,
    destination_warehouse_id    BIGINT                        NOT NULL,
    status                      VARCHAR(20)                   NOT NULL DEFAULT 'DRAFT',
    initiated_by                BIGINT,
    source_material_request_id  BIGINT,
    notes                       VARCHAR(500),
    created_at                  TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at                  TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT fk_transfer_batch_origin_warehouse
        FOREIGN KEY (origin_warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_batch_destination_warehouse
        FOREIGN KEY (destination_warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT chk_transfer_batch_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'COMPLETED')),
    CONSTRAINT chk_transfer_batch_origin_destination_distinct
        CHECK (origin_warehouse_id <> destination_warehouse_id)
);

CREATE INDEX idx_transfer_batch_origin_warehouse_id ON transfer_batch (origin_warehouse_id);
CREATE INDEX idx_transfer_batch_destination_warehouse_id ON transfer_batch (destination_warehouse_id);
CREATE INDEX idx_transfer_batch_status ON transfer_batch (status);
