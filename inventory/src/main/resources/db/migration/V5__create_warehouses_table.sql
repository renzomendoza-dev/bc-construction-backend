-- V5: warehouse

CREATE TABLE warehouse (
    id          BIGSERIAL                   PRIMARY KEY,
    code        VARCHAR(255)                 NOT NULL,
    name        VARCHAR(255)                 NOT NULL,
    active      BOOLEAN                      NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT uq_warehouse_code UNIQUE (code)
);
