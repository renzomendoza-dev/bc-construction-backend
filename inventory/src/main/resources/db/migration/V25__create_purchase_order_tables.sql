-- V25: purchase_order, purchase_order_line, and purchase_receipt.purchase_order_id
-- An order placed with a supplier before anything has arrived — an earlier
-- stage than purchase_receipt. initiated_by references app_user, matching
-- purchase_receipt.created_by's existing convention for "who did this".

CREATE TABLE purchase_order (
    id            BIGSERIAL PRIMARY KEY,
    supplier_id   BIGINT NOT NULL REFERENCES supplier (id) ON DELETE RESTRICT,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT purchase_order_status_check
            CHECK (status IN ('DRAFT', 'SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED')),
    notes         VARCHAR(500),
    initiated_by  BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_purchase_order_supplier_id ON purchase_order (supplier_id);
CREATE INDEX idx_purchase_order_status ON purchase_order (status);

CREATE TABLE purchase_order_line (
    id               BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_order (id),
    item_id          BIGINT NOT NULL REFERENCES item (id) ON DELETE RESTRICT,
    quantity         INTEGER NOT NULL,
    notes            VARCHAR(500)
);

CREATE INDEX idx_purchase_order_line_purchase_order_id ON purchase_order_line (purchase_order_id);

-- Independent of fulfills_transfer_batch_id (a receipt can carry either,
-- both, or neither) — real FK, purchase_order already exists by this point.
ALTER TABLE purchase_receipt ADD COLUMN purchase_order_id BIGINT REFERENCES purchase_order (id);

CREATE INDEX idx_purchase_receipt_purchase_order_id ON purchase_receipt (purchase_order_id);
