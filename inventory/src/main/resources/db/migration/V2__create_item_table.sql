-- V2: item
-- Master catalog of inventory items (products/materials tracked in the system).

CREATE TABLE item (
    id                  BIGSERIAL                   PRIMARY KEY,
    sku                 VARCHAR(255)                 NOT NULL,
    name                VARCHAR(255)                 NOT NULL,
    category            VARCHAR(255),
    unit_of_measure     VARCHAR(255),
    selling_price       NUMERIC(14,2),
    default_cost_price  NUMERIC(14,2),
    active              BOOLEAN                      NOT NULL,
    created_at          TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT uq_item_sku UNIQUE (sku)
);
