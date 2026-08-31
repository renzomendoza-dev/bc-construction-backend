-- V19: material_request
-- A site's request for materials to be pulled from a MAIN warehouse.
-- site_warehouse_id references warehouse directly (a "site" is just a
-- Warehouse row with type = SITE, see V16) - that it must specifically be
-- type SITE is validated in MaterialRequestService, not here, since a CHECK
-- constraint can't easily validate a joined row's column.

CREATE TABLE material_request (
    id                 BIGSERIAL                    PRIMARY KEY,
    site_warehouse_id  BIGINT                        NOT NULL,
    requested_by       BIGINT,
    date_needed        DATE,
    status             VARCHAR(25)                   NOT NULL DEFAULT 'DRAFT',
    notes              VARCHAR(500),
    created_at         TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT fk_material_request_site_warehouse
        FOREIGN KEY (site_warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT chk_material_request_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'PARTIALLY_FULFILLED', 'FULFILLED'))
);

CREATE INDEX idx_material_request_site_warehouse_id ON material_request (site_warehouse_id);
CREATE INDEX idx_material_request_status ON material_request (status);
