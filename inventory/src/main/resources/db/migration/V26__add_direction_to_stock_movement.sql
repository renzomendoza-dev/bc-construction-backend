-- V26: stock_movement.direction
-- Net effect of a movement row on its OWN warehouse's stock level (IN/OUT/
-- WITHIN), set explicitly by InventoryService at construction time. Added
-- because fromLocationId/toLocationId nullability alone can't reliably
-- distinguish a cross-warehouse TRANSFER's origin row from its destination
-- row once the origin side can debit the no-location bucket (both rows can
-- then have fromLocationId/toLocationId both null) — see
-- MovementDirection's own javadoc.
--
-- Added nullable first, backfilled from existing movement_type/location
-- data (correct for any pre-existing row, not just the current dev-data
-- seed), then made NOT NULL — the standard safe way to add a NOT NULL
-- column to a table that may already have rows.

ALTER TABLE stock_movement ADD COLUMN direction VARCHAR(20);

UPDATE stock_movement SET direction = CASE
    WHEN movement_type IN ('IN', 'ADJUSTMENT') THEN 'IN'
    WHEN movement_type = 'OUT' THEN 'OUT'
    WHEN movement_type = 'TRANSFER' AND from_location_id IS NOT NULL AND to_location_id IS NOT NULL THEN 'WITHIN'
    WHEN movement_type = 'TRANSFER' AND to_location_id IS NOT NULL THEN 'IN'
    -- Covers both "from set, to null" (unambiguously OUT) and "from/to both
    -- null" (ambiguous from these columns alone; doesn't occur in the
    -- current dev-data seed, so this is just a safe tie-break, not a
    -- meaningful inference).
    ELSE 'OUT'
END;

ALTER TABLE stock_movement ALTER COLUMN direction SET NOT NULL;

ALTER TABLE stock_movement ADD CONSTRAINT chk_stock_movement_direction
    CHECK (direction IN ('IN', 'OUT', 'WITHIN'));
