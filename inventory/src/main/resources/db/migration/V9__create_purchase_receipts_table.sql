-- V9: purchase_receipt
-- Header row for a supplier purchase receipt. warehouse_id records which
-- warehouse the receipt's lines will be stocked into once confirmed; it's
-- set at creation time, not derived later, so it's NOT NULL here.
-- confirmed/confirmed_at track whether PurchaseReceiptService.confirmPurchaseReceipt
-- has been run for this receipt yet (confirmed_at is null until it has).
-- supplier_id/warehouse_id reference master data -> ON DELETE RESTRICT on both.

CREATE TABLE purchase_receipt (
    id              BIGSERIAL                    PRIMARY KEY,
    supplier_id     BIGINT                        NOT NULL,
    warehouse_id    BIGINT                        NOT NULL,
    receipt_number  VARCHAR(255),
    purchase_date   DATE                          NOT NULL,
    total_amount    NUMERIC(38,2),
    image_url       VARCHAR(255),
    notes           VARCHAR(255),
    confirmed       BOOLEAN                       NOT NULL,
    confirmed_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at      TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT fk_purchase_receipt_supplier
        FOREIGN KEY (supplier_id) REFERENCES supplier (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_receipt_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse (id) ON DELETE RESTRICT
);

CREATE INDEX idx_purchase_receipt_supplier_id ON purchase_receipt (supplier_id);
CREATE INDEX idx_purchase_receipt_warehouse_id ON purchase_receipt (warehouse_id);
