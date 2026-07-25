
ALTER TABLE stock_movement RENAME COLUMN type TO movement_type;

ALTER TABLE stock_movement DROP CONSTRAINT chk_stock_movement_type;
ALTER TABLE stock_movement ADD CONSTRAINT chk_stock_movement_type
    CHECK (movement_type IN ('IN', 'OUT', 'TRANSFER', 'ADJUSTMENT'));