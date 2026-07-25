ALTER TABLE stock_movement ADD COLUMN created_by BIGINT;
ALTER TABLE stock_movement ADD CONSTRAINT fk_stock_movement_created_by FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE purchase_receipt ADD COLUMN created_by BIGINT;
ALTER TABLE purchase_receipt ADD COLUMN confirmed_by BIGINT;
ALTER TABLE purchase_receipt ADD CONSTRAINT fk_purchase_receipt_created_by FOREIGN KEY (created_by) REFERENCES app_user(id);
ALTER TABLE purchase_receipt ADD CONSTRAINT fk_purchase_receipt_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES app_user(id);