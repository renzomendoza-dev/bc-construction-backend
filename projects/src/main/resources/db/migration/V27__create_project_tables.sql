-- V27: project, project_expense
-- A construction project whose running cost (labor + material + other) is
-- tracked via project_expense rows. project_expense.category is a single
-- discriminator column rather than separate tables per cost type, matching
-- this codebase's "derive, don't over-model" convention. initiated_by/
-- recorded_by reference app_user directly (real FK, no migration-ordering
-- issue since app_user already exists as of V1) — the projects module has
-- no other cross-module references (deliberately independent, see
-- Project's own javadoc).

CREATE TABLE project (
    id            BIGSERIAL                     PRIMARY KEY,
    code          VARCHAR(50)                   NOT NULL UNIQUE,
    name          VARCHAR(255)                  NOT NULL,
    description   VARCHAR(2000),
    status        VARCHAR(20)                   NOT NULL DEFAULT 'ACTIVE',
    budget        NUMERIC(14, 2),
    start_date    DATE                          NOT NULL,
    end_date      DATE,
    initiated_by  BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT chk_project_status
        CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_project_status ON project (status);

CREATE TABLE project_expense (
    id            BIGSERIAL                     PRIMARY KEY,
    project_id    BIGINT                        NOT NULL REFERENCES project (id),
    category      VARCHAR(20)                   NOT NULL,
    description   VARCHAR(500)                  NOT NULL,
    amount        NUMERIC(14, 2)                NOT NULL,
    expense_date  DATE                          NOT NULL,
    recorded_by   BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT chk_project_expense_category
        CHECK (category IN ('LABOR', 'MATERIAL', 'OTHER'))
);

CREATE INDEX idx_project_expense_project_id ON project_expense (project_id);
CREATE INDEX idx_project_expense_category ON project_expense (category);
