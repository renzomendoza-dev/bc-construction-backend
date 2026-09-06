-- V29: worker, attendance
-- Worker is an independent field-labor roster, deliberately not tied to
-- app_user/Keycloak (field laborers don't need app logins). Attendance
-- records one worker's presence on one project on one day; recording it
-- also auto-creates a LABOR ProjectExpense on that project (see
-- AttendanceService), traced back via attendance.project_expense_id. That
-- FK points at the projects module's project_expense table — legal since
-- workers already has a real Maven dependency on projects, and
-- project_expense already exists as of V27 (this migration is numbered
-- after it).

CREATE TABLE worker (
    id            BIGSERIAL                     PRIMARY KEY,
    name          VARCHAR(255)                  NOT NULL,
    position      VARCHAR(100),
    daily_rate    NUMERIC(12, 2)                NOT NULL,
    active        BOOLEAN                       NOT NULL DEFAULT true,
    created_by    BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL
);

CREATE INDEX idx_worker_active ON worker (active);

CREATE TABLE attendance (
    id                  BIGSERIAL                     PRIMARY KEY,
    worker_id           BIGINT                        NOT NULL REFERENCES worker (id),
    project_id          BIGINT                        NOT NULL REFERENCES project (id),
    attendance_date     DATE                          NOT NULL,
    days_present        NUMERIC(4, 2)                 NOT NULL,
    rate_snapshot       NUMERIC(12, 2)                NOT NULL,
    notes               VARCHAR(1000),
    project_expense_id  BIGINT REFERENCES project_expense (id),
    recorded_by         BIGINT REFERENCES app_user (id),
    created_at          TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    CONSTRAINT uq_attendance_worker_date UNIQUE (worker_id, attendance_date)
);

CREATE INDEX idx_attendance_worker_id ON attendance (worker_id);
CREATE INDEX idx_attendance_project_id ON attendance (project_id);
CREATE INDEX idx_attendance_date ON attendance (attendance_date);
