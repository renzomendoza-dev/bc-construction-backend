-- V34: at most one inventory_stock row per item + warehouse + location,
-- INCLUDING the no-location bucket (location_id IS NULL).
--
-- V8's plain UNIQUE treated NULL as distinct from NULL, so it never stopped
-- two no-location rows for the same item and warehouse. Concurrent first
-- stock-ins did create them (reproduced on real Postgres), after which every
-- adjustment or transfer for that item and warehouse failed with "Query did
-- not return a unique result". NULLS NOT DISTINCT (Postgres 15+) makes NULL
-- count as equal here, and gives InventoryStockRepository.insertIfAbsent a
-- constraint for its ON CONFLICT to target.
--
-- Fails if duplicates already exist. Find them with:
--   SELECT item_id, warehouse_id, count(*) FROM inventory_stock
--   WHERE location_id IS NULL GROUP BY item_id, warehouse_id HAVING count(*) > 1;
-- and merge each set into one row (sum the quantities) before applying this.

ALTER TABLE inventory_stock DROP CONSTRAINT uq_inventory_stock_item_warehouse_location;

ALTER TABLE inventory_stock ADD CONSTRAINT uq_inventory_stock_item_warehouse_location
    UNIQUE NULLS NOT DISTINCT (item_id, warehouse_id, location_id);
