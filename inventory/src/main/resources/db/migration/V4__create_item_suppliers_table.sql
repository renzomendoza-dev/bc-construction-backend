-- V4: item_supplier
-- One row per (item, supplier) pair, recording that supplier's SKU/cost for
-- the item. item_id/supplier_id reference master data that must not be
-- deleted out from under a link -> ON DELETE RESTRICT on both sides.

CREATE TABLE item_supplier (
    id            BIGSERIAL      PRIMARY KEY,
    item_id       BIGINT         NOT NULL,
    supplier_id   BIGINT         NOT NULL,
    supplier_sku  VARCHAR(255),
    unit_cost     NUMERIC(38,2),
    CONSTRAINT fk_item_supplier_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_supplier_supplier
        FOREIGN KEY (supplier_id) REFERENCES supplier (id) ON DELETE RESTRICT,
    CONSTRAINT uq_item_supplier_item_supplier UNIQUE (item_id, supplier_id)
);

CREATE INDEX idx_item_supplier_item_id ON item_supplier (item_id);
CREATE INDEX idx_item_supplier_supplier_id ON item_supplier (supplier_id);
