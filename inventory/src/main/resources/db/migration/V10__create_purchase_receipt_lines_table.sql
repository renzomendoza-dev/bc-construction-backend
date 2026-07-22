-- V10: purchase_receipt_line
-- Line items for a purchase receipt. Owned by purchase_receipt at the JPA
-- level (cascade=ALL, orphanRemoval=true on PurchaseReceipt.lines) -
-- Hibernate implements this by issuing explicit child-then-parent DELETE
-- statements itself when you delete through the ORM, NOT via a DB-level FK
-- cascade. The real Hibernate-expected schema (per schema.sql) has no ON
-- DELETE clause on this FK at all, so it isn't added here either. item_id
-- references master data that must not be deleted out from under purchase
-- history -> ON DELETE RESTRICT.

CREATE TABLE purchase_receipt_line (
    id                   BIGSERIAL      PRIMARY KEY,
    purchase_receipt_id  BIGINT         NOT NULL,
    item_id              BIGINT         NOT NULL,
    quantity             INTEGER        NOT NULL,
    unit_cost            NUMERIC(38,2)  NOT NULL,
    line_total           NUMERIC(38,2),
    CONSTRAINT fk_purchase_receipt_line_receipt
        FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipt (id),
    CONSTRAINT fk_purchase_receipt_line_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT
);

CREATE INDEX idx_purchase_receipt_line_purchase_receipt_id ON purchase_receipt_line (purchase_receipt_id);
CREATE INDEX idx_purchase_receipt_line_item_id ON purchase_receipt_line (item_id);
