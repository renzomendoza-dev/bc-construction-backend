-- V8: inventory_stock
-- Current on-hand quantity for an item at a warehouse (and optionally a
-- specific storage_location within it). item_id/warehouse_id/location_id
-- all reference master/location data that must not be deleted out from
-- under a stock row -> ON DELETE RESTRICT on all three.

CREATE TABLE inventory_stock (
    id                 BIGSERIAL                    PRIMARY KEY,
    item_id            BIGINT                        NOT NULL,
    warehouse_id       BIGINT                        NOT NULL,
    location_id        BIGINT,
    quantity           INTEGER                       NOT NULL,
    reorder_threshold  INTEGER,
    updated_at         TIMESTAMP(6) WITH TIME ZONE    NOT NULL,
    CONSTRAINT fk_inventory_stock_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_stock_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_stock_location
        FOREIGN KEY (location_id) REFERENCES storage_location (id) ON DELETE RESTRICT,
    -- NOTE: standard SQL unique-constraint semantics treat NULL as distinct
    -- from NULL, so this constraint does NOT prevent multiple rows with the
    -- same item_id+warehouse_id and a NULL location_id. If "at most one
    -- warehouse-level stock row per item+warehouse" needs to be a hard
    -- DB-level invariant, add a partial unique index instead of/in addition
    -- to this one:
    --
    -- CREATE UNIQUE INDEX uq_inventory_stock_item_warehouse_null_location
    --     ON inventory_stock (item_id, warehouse_id)
    --     WHERE location_id IS NULL;
    CONSTRAINT uq_inventory_stock_item_warehouse_location UNIQUE (item_id, warehouse_id, location_id)
);

CREATE INDEX idx_inventory_stock_item_id ON inventory_stock (item_id);
CREATE INDEX idx_inventory_stock_warehouse_id ON inventory_stock (warehouse_id);
CREATE INDEX idx_inventory_stock_location_id ON inventory_stock (location_id);
