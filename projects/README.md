# Projects Module

Maven module (`com.bcconstructionservices:projects`) tracking a construction project's running
expenses — labor, materials bought, and other costs — against an optional budget. It is mounted
into the `app` aggregator module and depends on **only** `user` (for resolving `initiatedBy`/
`recordedBy` display names via `UserLookupHelper`) — deliberately no dependency on `inventory` or
`equipment`, so this module stays independently deployable/scalable later if the backend moves
toward a microservice split. `Project` has no site/warehouse link for the same reason.

The `workers` and `inventory` modules both depend on this one (not the other way around) to
auto-generate a `ProjectExpense`: `workers.AttendanceService` a `LABOR` entry when attendance is
recorded, `inventory.TransferBatchService` a `MATERIAL` entry per line on submit. Both call
`ProjectExpenseService.addExpense`/`deleteExpense` directly — a real cross-module service call,
not just a lookup, since that's an actual write-orchestration; see either service's own javadoc
for why a direct call was chosen over an event-driven mechanism. `ProjectLookupHelper` is the
separate, lighter-weight pattern for the common case: resolving a project id to a display name.

## Domain model

| Entity | Purpose |
|---|---|
| `Project` | The aggregation root — `code` (unique), `name`, `description`, `status` (`ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED`), optional `budget`, `startDate`/`endDate`, who initiated it. |
| `ProjectExpense` | A single recorded cost against a project — `category` (`LABOR`, `MATERIAL`, `OTHER`), `description`, `amount`, `expenseDate`, who recorded it. |

`Project` deliberately has **no** `@OneToMany` collection of its expenses — expense rows are
always created independently via `POST /api/projects/{projectId}/expenses`, never cascaded from
the parent, so no inverse collection is needed. This also sidesteps the reentrant-auto-flush
Hibernate bug documented in the root `CLAUDE.md` (an audited entity with a `cascade=ALL`
collection is the trigger; `Project` has neither the cascade collection nor any method that both
mutates it and queries again in the same transaction).

`MATERIAL`-category expenses are entered manually — there is currently no link to `inventory`'s
`PurchaseReceipt`/`PurchaseOrder`. That's a deliberate v1 scope cut, not an oversight.

## API endpoints

All endpoints return `application/json` and validate request bodies with `@Valid`.

### Projects — `/api/projects`
- `POST /api/projects` — create a project (`PROJECT_CREATE`). 409 if `code` already exists.
- `PUT /api/projects/{id}` — full-replacement update of `name`/`description`/`budget`/
  `startDate`/`endDate` (`PROJECT_EDIT`; explicit `null` clears a field). `code`/`status` are
  immutable via this endpoint. 422 once `status` is `COMPLETED`/`CANCELLED`.
- `POST /api/projects/{id}/complete` — `ACTIVE`/`ON_HOLD` → `COMPLETED` (`PROJECT_COMPLETE`).
  Terminal — no further edits or expenses afterward. 422 if already `COMPLETED`/`CANCELLED`.
- `GET /api/projects/{id}` — get by id (unguarded).
- `GET /api/projects` — paginated list, filterable by `status` (unguarded).

There is deliberately no `cancel` endpoint yet — `CANCELLED` exists in the status enum for future
use but nothing currently transitions a project to it.

### Project expenses — `/api/projects/{projectId}`
- `POST /api/projects/{projectId}/expenses` — record an expense (`PROJECT_EXPENSE_CREATE`). 404
  if the project doesn't exist, 422 if it's `COMPLETED`/`CANCELLED`. `amount` is normally positive,
  but a negative value is allowed — it represents a credit/reversal (e.g. `inventory`'s
  auto-generated entry for materials pulled back out of a project site) and reduces its
  category's running total in `GET /summary` accordingly.
- `GET /api/projects/{projectId}/expenses` — paginated list, filterable by `category`
  (unguarded). 404 if the project doesn't exist.
- `GET /api/projects/{projectId}/summary` — aggregated totals per category plus
  `budgetRemaining` (unguarded). 404 if the project doesn't exist.
- `DELETE /api/projects/{projectId}/expenses/{expenseId}` — delete an expense
  (`PROJECT_EXPENSE_DELETE`). 404 if the project or expense doesn't exist (or the expense belongs
  to a different project), 422 if the project is `COMPLETED`/`CANCELLED`. Also called directly by
  `workers.AttendanceService` to remove the expense an `Attendance` record generated, when that
  `Attendance` is itself deleted.

`ProjectSummaryResponse` is always recomputed from every `ProjectExpense` row scoped to the
project id (`ProjectExpenseRepository.findAllByProjectId`, summed in Java by category) — never an
incrementally-maintained running total — matching the aggregation-root convention in the root
`CLAUDE.md`. `budgetRemaining` is `null` when the project has no `budget` set.

## Error handling

`ProjectsExceptionHandler` (scoped to `com.bcconstructionservices.projects.controller`) maps:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `ProjectNotEditableException` | 422 |
| Bean validation failures | 400 (field-level `ValidationErrorResponse`) |
| Malformed JSON | 400 |
| `AccessDeniedException` | 403 |
| Anything else | 500 |

A single `ProjectNotEditableException` covers update/complete/add-expense — all three share the
identical "not `COMPLETED`/`CANCELLED`" predicate, unlike `PurchaseOrder`'s update-vs-close split
in `inventory` where the two predicates genuinely differ.

## Permissions

`PROJECT_CREATE`, `PROJECT_EDIT`, `PROJECT_COMPLETE`, `PROJECT_EXPENSE_CREATE`,
`PROJECT_EXPENSE_DELETE` — one per mutating action. Plain `GET` endpoints are unguarded (any
authenticated caller), matching the rest of the backend's convention.

## Database migrations

Flyway migration `V27__create_project_tables.sql` (module-local — the full version sequence is
shared and global across all modules) creates `project` and `project_expense`, each with a
status/category `CHECK` constraint and a real FK to `app_user` for `initiated_by`/`recorded_by`.

Dev-only demo data (3 sample projects — one `ACTIVE`, one `ON_HOLD`, one `COMPLETED` with
expenses tracked against its budget — plus a handful of `ProjectExpense` rows for each) seeds
via `app/src/main/resources/db/dev-data/V28__seed_dev_projects_data.sql`, only loaded when the
`dev` Spring profile's `flyway.locations` override is active — never in prod.

## Testing

Tests use an in-memory H2 database (test-scoped dependency in `pom.xml`). Run with:

```bash
../mvnw -pl projects test
```
