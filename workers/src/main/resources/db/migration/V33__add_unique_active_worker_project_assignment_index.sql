-- V33: at most one ACTIVE worker_project_assignment per worker, enforced by
-- the database. V32 left this to the application only because tests then ran
-- on H2, which rejects partial indexes; they now run on real Postgres.
-- WorkerProjectAssignmentService.assign still pre-checks for a clean 409, and
-- maps a violation of this index (two concurrent assigns) to the same 409.
--
-- Fails if a worker already has more than one active assignment - deactivate
-- the extras first (PATCH /api/worker-assignments/{id}/deactivate).

CREATE UNIQUE INDEX uq_worker_project_assignment_active_worker
    ON worker_project_assignment (worker_id)
    WHERE active = true;
