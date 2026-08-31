-- V16: add warehouse.type
-- Distinguishes a MAIN distribution warehouse from a construction SITE. A
-- "site" is deliberately just a Warehouse row with type = SITE - this lets
-- every existing mechanism (inventory_stock, stock_movement, transferStock,
-- movement history, low-stock checks) work for sites with zero changes to
-- that code. Defaulted to MAIN so existing warehouse rows classify as the
-- type they already behave as.

ALTER TABLE warehouse ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'MAIN'
    -- Written as an OR chain rather than IN (...) - H2's constant-set IN
    -- evaluator (ConditionInConstantSet) throws when checking a constraint
    -- added via ALTER TABLE against a fresh insert; an OR chain avoids that
    -- code path entirely while being semantically identical, and both
    -- PostgreSQL and H2 handle it the same way.
    CONSTRAINT chk_warehouse_type CHECK (type = 'MAIN' OR type = 'SITE');
