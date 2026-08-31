-- V12: equipment, equipment_assignment
-- current_holder_id/created_by/updated_by/assigned_to_id all reference
-- app_user (FK, no ON DELETE clause -> default NO ACTION, matching
-- stock_movement.created_by/purchase_receipt.created_by/confirmed_by's
-- existing convention).

CREATE TABLE equipment (
    id                BIGSERIAL PRIMARY KEY,
    asset_tag         VARCHAR(50) NOT NULL UNIQUE,
    name              VARCHAR(150) NOT NULL,
    category          VARCHAR(100),
    serial_number     VARCHAR(100),
    status            VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CONSTRAINT equipment_status_check
            CHECK (status IN ('AVAILABLE','CHECKED_OUT','IN_USE','IN_REPAIR','RETIRED','LOST')),
    current_holder_id BIGINT REFERENCES app_user(id),
    current_site      VARCHAR(150),
    checked_out_at    TIMESTAMP(6) WITH TIME ZONE,
    purchase_price    NUMERIC(14,2),
    purchase_date     DATE,
    purchase_vendor   VARCHAR(150),
    created_by        BIGINT REFERENCES app_user(id),
    updated_by        BIGINT REFERENCES app_user(id),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_equipment_status ON equipment(status);
CREATE INDEX idx_equipment_current_holder ON equipment(current_holder_id);

CREATE TABLE equipment_assignment (
    id             BIGSERIAL PRIMARY KEY,
    equipment_id   BIGINT NOT NULL REFERENCES equipment(id),
    assigned_to_id BIGINT NOT NULL REFERENCES app_user(id),
    site           VARCHAR(150),
    checked_out_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    checked_in_at  TIMESTAMP(6) WITH TIME ZONE,
    condition_out  VARCHAR(500),
    condition_in   VARCHAR(500),
    created_by     BIGINT REFERENCES app_user(id),
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_assignment_equipment ON equipment_assignment(equipment_id);
CREATE INDEX idx_assignment_open ON equipment_assignment(equipment_id, checked_in_at);
