-- V4: supplier

CREATE TABLE supplier (
    id            BIGSERIAL                    PRIMARY KEY,
    name          VARCHAR(255)                  NOT NULL,
    contact_info  VARCHAR(255),
    active        BOOLEAN                       NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE    NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE    NOT NULL
);
