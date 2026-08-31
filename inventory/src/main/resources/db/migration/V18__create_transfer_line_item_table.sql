-- V18: transfer_line_item
-- Line items for a transfer_batch (V17). item_id references master data
-- that must not be deleted out from under transfer history -> ON DELETE
-- RESTRICT. Owned by transfer_batch at the JPA level (cascade=ALL,
-- orphanRemoval=true on TransferBatch.lineItems) - Hibernate implements this
-- with explicit child-then-parent DELETEs, not a DB-level FK cascade, so no
-- ON DELETE clause is added on that FK either (matches purchase_receipt_line's
-- treatment of its own parent FK).

CREATE TABLE transfer_line_item (
    id                 BIGSERIAL      PRIMARY KEY,
    transfer_batch_id  BIGINT         NOT NULL,
    item_id            BIGINT         NOT NULL,
    expected_quantity  INTEGER,
    quantity           INTEGER        NOT NULL,
    notes              VARCHAR(500),
    CONSTRAINT fk_transfer_line_item_transfer_batch
        FOREIGN KEY (transfer_batch_id) REFERENCES transfer_batch (id),
    CONSTRAINT fk_transfer_line_item_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT,
    CONSTRAINT chk_transfer_line_item_quantity_positive
        CHECK (quantity > 0)
);

CREATE INDEX idx_transfer_line_item_transfer_batch_id ON transfer_line_item (transfer_batch_id);
CREATE INDEX idx_transfer_line_item_item_id ON transfer_line_item (item_id);
