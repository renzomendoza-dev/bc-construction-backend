-- V20: material_request_line_item

CREATE TABLE material_request_line_item (
    id                    BIGSERIAL      PRIMARY KEY,
    material_request_id  BIGINT         NOT NULL,
    item_id               BIGINT        NOT NULL,
    quantity_requested    INTEGER       NOT NULL,
    notes                 VARCHAR(500),
    CONSTRAINT fk_material_request_line_item_material_request
        FOREIGN KEY (material_request_id) REFERENCES material_request (id),
    CONSTRAINT fk_material_request_line_item_item
        FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE RESTRICT,
    CONSTRAINT chk_material_request_line_item_quantity_positive
        CHECK (quantity_requested > 0)
);

CREATE INDEX idx_material_request_line_item_material_request_id ON material_request_line_item (material_request_id);
CREATE INDEX idx_material_request_line_item_item_id ON material_request_line_item (item_id);
