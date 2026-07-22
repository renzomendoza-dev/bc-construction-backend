-- V6: storage_location
-- Bin/shelf-level locations within a warehouse. code is only unique within
-- its own warehouse (e.g. two warehouses can each have an "A-01-01").
-- warehouse_id references master data -> ON DELETE RESTRICT.

CREATE TABLE storage_location (
    id            BIGSERIAL     PRIMARY KEY,
    warehouse_id  BIGINT        NOT NULL,
    code          VARCHAR(255)  NOT NULL,
    CONSTRAINT fk_storage_location_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT uq_storage_location_warehouse_code UNIQUE (warehouse_id, code)
);

CREATE INDEX idx_storage_location_warehouse_id ON storage_location (warehouse_id);
