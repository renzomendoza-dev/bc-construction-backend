-- V9: stock_movement
-- Append-only ledger of every stock change (IN/OUT/TRANSFER/ADJUSTMENT).
-- movement_type is VARCHAR + CHECK rather than a native Postgres ENUM, to
-- keep it simple to alter later (adding a value is just an ALTER ...
-- DROP/ADD CONSTRAINT, no ALTER TYPE ... ADD VALUE ordering/transaction
-- quirks). item_id/warehouse_id/from_location_id/to_location_id all
-- reference master/location data that must not be deleted out from under
-- movement history -> ON DELETE RESTRICT on all four. created_by references
-- the app_user who initiated the movement.
--
-- direction is the net effect of a movement row on its OWN warehouse's stock
-- level (IN/OUT/WITHIN), set explicitly by InventoryService at construction
-- time (not derived from movement_type/location columns alone -
-- fromLocationId/toLocationId nullability can't reliably distinguish a
-- cross-warehouse TRANSFER's origin row from its destination row once the
-- origin side can debit the no-location bucket - see MovementDirection's own
-- javadoc), so every insert must supply it; no default.

CREATE TABLE stock_movement (
    id                BIGSERIAL                     PRIMARY KEY,
    item_id           BIGINT                         NOT NULL,
    warehouse_id      BIGINT                         NOT NULL,
    from_location_id  BIGINT,
    to_location_id    BIGINT,
    movement_type     VARCHAR(255)                   NOT NULL,
    direction         VARCHAR(20)                    NOT NULL,
    quantity          INTEGER                        NOT NULL,
    reason            VARCHAR(255),
    created_at        TIMESTAMP(6) WITH TIME ZONE     NOT NULL,
    created_by        BIGINT,
    CONSTRAINT fk_stock_movement_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_from_location
        FOREIGN KEY (from_location_id) REFERENCES storage_location (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_to_location
        FOREIGN KEY (to_location_id) REFERENCES storage_location (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_created_by
        FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT chk_stock_movement_type
        CHECK (movement_type IN ('IN', 'OUT', 'TRANSFER', 'ADJUSTMENT')),
    CONSTRAINT chk_stock_movement_direction
        CHECK (direction IN ('IN', 'OUT', 'WITHIN'))
);

CREATE INDEX idx_stock_movement_item_id ON stock_movement (item_id);
CREATE INDEX idx_stock_movement_warehouse_id ON stock_movement (warehouse_id);
CREATE INDEX idx_stock_movement_from_location_id ON stock_movement (from_location_id);
CREATE INDEX idx_stock_movement_to_location_id ON stock_movement (to_location_id);
