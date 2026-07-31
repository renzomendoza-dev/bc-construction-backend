CREATE TABLE equipment (
                           id                BIGSERIAL PRIMARY KEY,
                           asset_tag         VARCHAR(50) NOT NULL UNIQUE,
                           name              VARCHAR(150) NOT NULL,
                           category          VARCHAR(100),
                           serial_number     VARCHAR(100),
                           status            VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                               CONSTRAINT equipment_status_check
                                   CHECK (status IN ('AVAILABLE','CHECKED_OUT','IN_USE','IN_REPAIR','RETIRED','LOST')),
                           current_holder_id BIGINT,
                           current_site      VARCHAR(150),
                           checked_out_at    TIMESTAMP,
                           purchase_price    NUMERIC(12,2),
                           purchase_date     DATE,
                           purchase_vendor   VARCHAR(150),
                           created_by        BIGINT,
                           updated_by        BIGINT,
                           created_at        TIMESTAMP NOT NULL DEFAULT now(),
                           updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_equipment_status ON equipment(status);
CREATE INDEX idx_equipment_current_holder ON equipment(current_holder_id);

CREATE TABLE equipment_assignment (
                                      id             BIGSERIAL PRIMARY KEY,
                                      equipment_id   BIGINT NOT NULL REFERENCES equipment(id),
                                      assigned_to_id BIGINT NOT NULL,
                                      site           VARCHAR(150),
                                      checked_out_at TIMESTAMP NOT NULL,
                                      checked_in_at  TIMESTAMP,
                                      condition_out  VARCHAR(500),
                                      condition_in   VARCHAR(500),
                                      created_by     BIGINT,
                                      created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_assignment_equipment ON equipment_assignment(equipment_id);
CREATE INDEX idx_assignment_open ON equipment_assignment(equipment_id, checked_in_at);