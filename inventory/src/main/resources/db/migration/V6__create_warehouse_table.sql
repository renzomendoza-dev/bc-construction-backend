-- V6: warehouse
-- type distinguishes a MAIN distribution warehouse from a construction SITE.
-- A "site" is deliberately just a Warehouse row with type = SITE - this lets
-- every mechanism built against Warehouse (inventory_stock, stock_movement,
-- transferStock, movement history, low-stock checks) work for sites with
-- zero extra code. Written as an OR chain rather than IN (...) - H2's
-- constant-set IN evaluator (ConditionInConstantSet) throws when checking a
-- CHECK constraint against certain insert shapes; an OR chain avoids that
-- code path entirely while being semantically identical, and both Postgres
-- and H2 handle it the same way.

CREATE TABLE warehouse (
    id          BIGSERIAL                    PRIMARY KEY,
    code        VARCHAR(255)                  NOT NULL,
    name        VARCHAR(255)                  NOT NULL,
    active      BOOLEAN                       NOT NULL,
    type        VARCHAR(20)                   NOT NULL DEFAULT 'MAIN'
        CONSTRAINT chk_warehouse_type CHECK (type = 'MAIN' OR type = 'SITE'),
    created_at  TIMESTAMP(6) WITH TIME ZONE    NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE    NOT NULL,
    CONSTRAINT uq_warehouse_code UNIQUE (code)
);
