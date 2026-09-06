# Workers Module

Maven module (`com.bcconstructionservices:workers`) tracking a field-labor roster (`Worker`) and
daily attendance (`Attendance`). Recording attendance auto-creates a `LABOR` `ProjectExpense` on
the referenced project, closing the gap where labor cost previously had to be typed in by hand as
a manual expense.

Depends on `user` (auditing — the established `UserLookupHelper` pattern) and `projects` (a real
Maven dependency, not just an id + lookup helper — see "Cross-module design" below).

## Why `Worker` is not `AppUser`

Every `AppUser` requires a `keycloakId` — it's an app-login account tied to Keycloak. Field
laborers tracked here don't need app access, so `Worker` is a fully independent roster entity,
never a `@ManyToOne`/extension of `AppUser`.

## Domain model

| Entity | Purpose |
|---|---|
| `Worker` | A field laborer on the roster — `name`, `position`, `dailyRate` (snapshotted onto each `Attendance` row, so a later rate change never retroactively changes a past expense), `active` flag. |
| `Attendance` | One worker's presence on one project on one day — `daysPresent` (decimal, e.g. `0.5` for a half day), `rateSnapshot`, `notes`, and `projectExpenseId` tracing back to the `LABOR` expense it generated. |

`Worker` holds no collection of its `Attendance` rows (no `@OneToMany`) — same reasoning as
`Project`: attendance is always created independently, never cascaded, which also sidesteps the
reentrant-auto-flush bug documented in the root `CLAUDE.md`.

At most one `Attendance` record per worker per day (`uq_attendance_worker_date`) — a worker is
attributed to one project per day for v1. `daysPresent` being a decimal handles half-days without
needing multiple records per day.

## Cross-module design: a real service dependency, not just a lookup

Every other cross-module reference in this backend (`WarehouseLookupHelper`, `UserLookupHelper`)
resolves a display name for a plain `Long` id — a read. `AttendanceService` needs something more:
creating an `Attendance` row must also create a `ProjectExpense` in a different module's table,
inside the same transaction. That's write-orchestration, not a lookup, so `workers` takes a real
Maven dependency on `projects` and `AttendanceService` calls `ProjectExpenseService.addExpense`/
`deleteExpense` directly — reusing that service's existing validation (project exists, project is
`ACTIVE`/`ON_HOLD`) rather than duplicating it.

An event-driven alternative (`ApplicationEventPublisher`) was considered and rejected for this
version: there's no message broker or async requirement in this single-JVM modular monolith, and
a direct call already mirrors what the module boundary would become if `workers`/`projects` were
ever split into separate services — an HTTP call standing in for this Java one, same shape, less
machinery today.

The reverse traceability link (`Attendance.projectExpenseId`, not a column on `ProjectExpense`)
is a deliberate placement choice — see `Attendance`'s own javadoc: it keeps the FK pointing the
same direction as the Maven dependency (`workers` → `projects`) and adds no schema-level
dependency from `projects` back onto `workers`, preserving `projects`' documented independence.

Because exceptions owned by `projects` (`ResourceNotFoundException`, `ProjectNotEditableException`)
can legitimately bubble up through this module's own controllers, `WorkersExceptionHandler`
explicitly maps both to the same status codes `projects` itself uses for them, rather than
falling through to the generic 500 fallback.

## API endpoints

All endpoints return `application/json` and validate request bodies with `@Valid`.

### Workers — `/api/workers`
- `POST /api/workers` — add a worker (`WORKER_CREATE`).
- `PUT /api/workers/{id}` — full-replacement update of `name`/`position`/`dailyRate`
  (`WORKER_EDIT`). Allowed regardless of the `active` flag.
- `PATCH /api/workers/{id}/deactivate` — soft-retire (`WORKER_DEACTIVATE`). Workers are never
  hard-deletable — a worker with existing attendance history must stay resolvable.
- `GET /api/workers/{id}` — get by id (unguarded).
- `GET /api/workers` — paginated list, filterable by `active` (unguarded).

### Attendance — `/api/attendance`
- `POST /api/attendance` — record one worker's attendance for one day (`ATTENDANCE_CREATE`).
  404 if the worker or project doesn't exist, 400 if the worker is inactive, 409 if this worker
  already has a record for that date, 422 if the project is `COMPLETED`/`CANCELLED`.
- `DELETE /api/attendance/{id}` — delete an attendance record (`ATTENDANCE_DELETE`), and the
  `LABOR` expense it generated, if any. The only fix path for a mis-entered record — no edit
  endpoint, delete and re-add. 422 if the linked project is `COMPLETED`/`CANCELLED`, checked
  (via `ProjectExpenseService.assertExpenseDeletable`, without deleting anything yet) before
  either row is touched, so both are left untouched if that happens. The actual deletes, once
  past that check, go Attendance first, then ProjectExpense — `projectExpenseId` is a plain
  column with a real, non-deferrable FK, so deleting the expense first would violate it while
  this row still references it (a real bug this endpoint shipped with once; see the root-cause
  writeup in the repo-root `CLAUDE.md`'s "Cross-module write orchestration").
- `GET /api/attendance/{id}` — get by id (unguarded).
- `GET /api/attendance` — paginated list, filterable by `workerId`/`projectId`/date range
  (unguarded).

## Error handling

`WorkersExceptionHandler` (scoped to `com.bcconstructionservices.workers.controller`) maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` (own) | 404 |
| `projects.exception.ResourceNotFoundException` | 404 |
| `projects.exception.ProjectNotEditableException` | 422 |
| `DuplicateAttendanceException` | 409 |
| `InactiveWorkerException` | 400 |
| Bean validation failures | 400 (field-level `ValidationErrorResponse`) |
| Malformed JSON | 400 |
| `AccessDeniedException` | 403 |
| Anything else | 500 |

## Permissions

`WORKER_CREATE`, `WORKER_EDIT`, `WORKER_DEACTIVATE`, `ATTENDANCE_CREATE`, `ATTENDANCE_DELETE` —
one per mutating action. Plain `GET` endpoints are unguarded, matching the rest of the backend's
convention.

## Database migrations

Flyway migration `V29__create_worker_and_attendance_tables.sql` (module-local — the full version
sequence is shared and global across all modules) creates `worker` and `attendance`, including a
real FK from `attendance.project_expense_id` to `projects`' `project_expense` table (legal since
`workers` already depends on `projects`, and that migration runs after `project_expense` exists).

Dev-only demo data (4 sample workers, one deactivated; a handful of `Attendance` rows against the
existing `ACTIVE`/`ON_HOLD` seeded projects, with matching hand-inserted `ProjectExpense` rows)
seeds via `app/src/main/resources/db/dev-data/V30__seed_dev_workers_data.sql`, only loaded when
the `dev` Spring profile's `flyway.locations` override is active — never in prod.

## Testing

Tests use an in-memory H2 database (test-scoped dependency in `pom.xml`). Run with:

```bash
../mvnw -pl workers -am test
```

(`-am` matters here — `workers` depends on `projects`, so a stale local-repo `projects` jar would
otherwise cause "cannot find symbol" errors for classes that very much exist.)
