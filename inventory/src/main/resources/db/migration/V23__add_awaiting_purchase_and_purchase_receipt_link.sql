-- V23: TransferBatch AWAITING_PURCHASE status + PurchaseReceipt.fulfills_transfer_batch_id
-- Links Material Requests to Purchasing when stock is short: a transfer_batch
-- that fails to submit on insufficient stock is marked AWAITING_PURCHASE
-- (widening its existing status CHECK constraint); a purchase_receipt can
-- then declare "this purchase is for that shortfall" via
-- fulfills_transfer_batch_id, a real FK (unlike transfer_batch's own
-- source_material_request_id, there's no migration-order constraint here -
-- transfer_batch already exists by V17 - and purchase_receipt already uses
-- real FKs for its other cross-references).

ALTER TABLE transfer_batch DROP CONSTRAINT chk_transfer_batch_status;
ALTER TABLE transfer_batch ADD CONSTRAINT chk_transfer_batch_status
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'COMPLETED', 'AWAITING_PURCHASE'));

ALTER TABLE purchase_receipt ADD COLUMN fulfills_transfer_batch_id BIGINT;
ALTER TABLE purchase_receipt ADD CONSTRAINT fk_purchase_receipt_fulfills_transfer_batch
    FOREIGN KEY (fulfills_transfer_batch_id) REFERENCES transfer_batch (id) ON DELETE RESTRICT;

CREATE INDEX idx_purchase_receipt_fulfills_transfer_batch_id ON purchase_receipt (fulfills_transfer_batch_id);
