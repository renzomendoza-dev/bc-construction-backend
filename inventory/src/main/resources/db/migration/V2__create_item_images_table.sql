-- V2: item_image
-- Images for an item, ordered by sort_order. Owned by item at the JPA level
-- (cascade=ALL, orphanRemoval=true on Item.images) - Hibernate implements
-- this by issuing explicit child-then-parent DELETE statements itself when
-- you delete through the ORM, NOT via a DB-level FK cascade. The real
-- Hibernate-expected schema (per schema.sql) has no ON DELETE clause on
-- this FK at all, so it isn't added here either.

CREATE TABLE item_image (
    id          BIGSERIAL                   PRIMARY KEY,
    item_id     BIGINT                       NOT NULL,
    image_url   VARCHAR(255)                 NOT NULL,
    sort_order  INTEGER                      NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT fk_item_image_item
        FOREIGN KEY (item_id) REFERENCES item (id)
);

CREATE INDEX idx_item_image_item_id ON item_image (item_id);
