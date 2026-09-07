-- V32: worker_project_assignment, attendance.time_in/time_out
-- WorkerProjectAssignment tracks which project a worker's crew currently
-- belongs to — one active assignment per worker at a time. Deliberately NOT
-- a DB-level partial unique index (CREATE UNIQUE INDEX ... WHERE active =
-- true, which real Postgres supports) — H2's PostgreSQL-compatibility mode
-- (used by every module's test suite) rejects that syntax, and ddl-auto:
-- validate requires the test schema to exactly match this one. Enforced at
-- the application layer only (WorkerProjectAssignmentService#assign's
-- existsByWorkerIdAndActiveTrue pre-check, 409 on violation) — unlike
-- uq_attendance_worker_date (V29), which backs its own app-level check with
-- a real DB constraint since a plain unique index has no portability issue.
--
-- project_id is a plain column, not a JPA relation — Project lives in the
-- projects module (see Attendance's own javadoc for the identical
-- reasoning); real FK since workers already depends on projects and project
-- already exists as of V27.
--
-- time_in/time_out are nullable — only ever set via the new batch attendance
-- endpoint; a single POST /api/attendance record leaves them null exactly as
-- before (that endpoint's request/behavior is unchanged).

CREATE TABLE worker_project_assignment (
    id            BIGSERIAL                     PRIMARY KEY,
    worker_id     BIGINT                        NOT NULL REFERENCES worker (id),
    project_id    BIGINT                        NOT NULL REFERENCES project (id),
    active        BOOLEAN                       NOT NULL DEFAULT true,
    created_by    BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE   NOT NULL
);

CREATE INDEX idx_worker_project_assignment_worker_id ON worker_project_assignment (worker_id);
CREATE INDEX idx_worker_project_assignment_project_id ON worker_project_assignment (project_id);

ALTER TABLE attendance ADD COLUMN time_in TIME;
ALTER TABLE attendance ADD COLUMN time_out TIME;
