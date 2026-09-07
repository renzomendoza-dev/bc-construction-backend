# BC Construction Services — Backend

Multi-module Maven / Spring Boot 4.1 backend. Java 25, Hibernate 7, MapStruct, Lombok, Flyway,
Spring Security 7 with a Keycloak OAuth2 resource server.

## Modules

| Module | Purpose | Depends on |
|---|---|---|
| `user` | `AppUser`, admin user management, auth plumbing (`CurrentUserService`, `UserLookupHelper`, `AuditorAwareImpl`) | — |
| `inventory` | Items, warehouses, stock, purchase receipts, material requests, transfer batches | `user`, `projects` |
| `equipment` | Equipment asset tracking, checkout/check-in, batch assignment/transfer/return | `user`, `inventory` |
| `sales` | Placeholder — one empty controller, not built out yet | — |
| `projects` | Project running-expense tracking: `Project` (aggregation root, no site/warehouse link) and `ProjectExpense` (LABOR/MATERIAL/OTHER) | `user` |
| `workers` | Field-labor roster (`Worker`, independent of `AppUser`/Keycloak) and daily `Attendance`, which auto-creates a LABOR `ProjectExpense` | `user`, `projects` |
| `app` | Aggregator: the actual bootable Spring Boot application, wires every module together | all of the above |

`projects` is deliberately kept independent of every other business module (`user` is its only
dependency) as part of a broader push to keep modules loosely coupled and independently scalable
— don't add a dependency **to** `projects` without asking first, even for something that seems
like an obvious link. `workers` and `inventory` depending **on** `projects` (the reverse
direction) is the exception that proves the rule: both are real, deliberate write-orchestration
dependencies (see "Cross-module write orchestration" below), not casual coupling, and `projects`
itself stays unaware either one exists.

Each module keeps its **own** exception vocabulary and its own `@RestControllerAdvice` scoped to
`basePackages = "com.bcconstructionservices.<module>.controller"` (e.g. `GlobalExceptionHandler`
in inventory, `EquipmentExceptionHandler` in equipment, `ProjectsExceptionHandler` in projects) —
never an unscoped bare `@RestControllerAdvice`. An unscoped one's catch-all `Exception.class`
handler would apply to every controller in the app once modules are wired together, colliding
with every other module's own catch-all.

**Name every `@Component`/`@Service`/`@Repository`/`@RestControllerAdvice`/`@Configuration`
class uniquely across the whole reactor, not just within its own module.** Spring's default
bean name is derived from the simple class name, not the package — two same-named annotated
classes in different modules (e.g. `inventory.exception.GlobalExceptionHandler` and a
hypothetical `projects.exception.GlobalExceptionHandler`) throw
`ConflictingBeanDefinitionException` the moment both are wired into the same `app` context,
even though each module's own tests pass fine in isolation (no test boots the full multi-module
`app` context, so this class of bug is invisible until a real app startup). This is exactly why
`inventory`'s handler is the plain `GlobalExceptionHandler` but every module added after it
(`EquipmentExceptionHandler`, `ProjectsExceptionHandler`) uses a module-prefixed name instead —
don't reuse the generic name for a new module's handler (or any other annotated class) without
checking it's not already taken somewhere else in the reactor.

## Database & Flyway migrations

**The Flyway version sequence is global across every module.** `classpath:db/migration` is a
classpath-wide location — Flyway applies every module's migrations together in one ordered
sequence, not per-module. Before adding a migration, check the current max version number
across **all** of these before picking the next one:
```
find . -path "*/db/migration/*.sql" -o -path "*/db/dev-data/*.sql"
```
(`app/src/main/resources/db/dev-data` holds dev-only seed data, wired via a `flyway.locations`
override in `application-dev.yaml` — never add that location to the prod profile.)

Cross-module DB foreign keys are fine (all modules share one physical database) even when the
referencing Java entity can't hold a `@ManyToOne` to the referenced module's entity — see below.

**Write migration SQL portable to H2's PostgreSQL-compatibility mode, not just real Postgres** —
every module's test suite runs its migrations against H2 in `MODE=PostgreSQL` (see the Testing
section), and `ddl-auto: validate` means the test schema must match the real one exactly, so
there's no option to diverge. Two real syntax gaps hit while building `workers`' `V32`:
- A partial unique index (`CREATE UNIQUE INDEX ... WHERE active = true`) — real Postgres accepts
  it, H2's PostgreSQL mode rejects it with a syntax error. No portable equivalent; the constraint
  has to be enforced at the application layer only (see `WorkerProjectAssignment`'s own javadoc
  for the resulting "no DB-level enforcement" trade-off).
- Comma-separated multi-column `ALTER TABLE t ADD COLUMN a, ADD COLUMN b` — also rejected by H2's
  PostgreSQL mode. Split into one `ALTER TABLE ... ADD COLUMN` statement per column instead;
  Postgres accepts that form too, so there's no downside to always writing it this way.

Run the module's tests (which apply every migration against a real H2 instance) after writing a
new migration, before assuming it's portable — don't just eyeball the SQL for Postgres validity.

## Cross-module entity references

A module never takes a JPA `@ManyToOne` to another module's entity — e.g. `equipment.Equipment`
doesn't hold a `Warehouse` (that's `inventory`'s entity). Instead:

- Store a plain `Long` id column (e.g. `Equipment.currentWarehouseId`).
- Give it a **real FK constraint at the DB level** if there's no migration-ordering reason not
  to (the referenced table already exists by the time this migration runs).
- Resolve its display name in a MapStruct mapper via a small `@Component` in the *referenced*
  module: a `@Named`-qualified method like `UserLookupHelper.resolveUserName(Long)` or
  `WarehouseLookupHelper.resolveWarehouseName(Long)`, returning null for a null/missing id. Add
  it to the mapper's `@Mapper(uses = {...})` list. This is the established pattern for every
  cross-module id → display-name need — don't invent a different one.
- This does mean the referencing module gains a real Maven dependency on the referenced module
  (e.g. `equipment` depends on `inventory` for exactly this).

## Cross-module write orchestration (-> projects)

The id + LookupHelper pattern above is for a *read*: resolving a display name. Two modules now
need something more — creating a row must also create/delete a `ProjectExpense` on a different
module's table, atomically, in the same transaction: `workers.AttendanceService` (a LABOR entry
per `Attendance`) and `inventory.TransferBatchService` (a MATERIAL entry per line, on submit).
Established pattern for this kind of cross-module *write*, used identically by both: take a real
Maven dependency on the owning module (`projects`) and call its existing service method directly
(`ProjectExpenseService.addExpense`/`deleteExpense`) — reusing that service's own validation
(project exists, project is `ACTIVE`/`ON_HOLD`) rather than duplicating it. An event-driven
alternative (`ApplicationEventPublisher`) was considered and rejected the first time this came up
(for `workers`) and the reasoning still holds for `inventory`: there's no message broker or async
requirement in this single-JVM modular monolith, and a direct call already mirrors what the
module boundary would become if these were ever split into separate services — an HTTP call
standing in for this Java one, same shape, less machinery today. Don't reach for an event bus
here just because the backend is meant to be microservice-ready eventually; that's premature
abstraction until there's an actual async/multi-consumer need.

A consequence: exceptions owned by the *called* module (`projects`) can legitimately bubble up
through the *calling* module's own controllers (e.g. `projects.exception.ProjectNotEditableException`
from inside `workers.controller.AttendanceController` or `inventory.controller.TransferBatchController`).
The calling module's own `@RestControllerAdvice` needs an explicit `@ExceptionHandler` for each
such exception it might see, mapped to the same status code the owning module uses — it won't be
caught automatically, and letting it fall through to the generic `Exception.class` handler turns
a 422/404 into a 500. See `WorkersExceptionHandler` and inventory's `GlobalExceptionHandler` for
the pattern (both handle the identical pair: `projects.exception.ResourceNotFoundException` and
`ProjectNotEditableException`).

When a reverse traceability id is needed (e.g. so deleting/re-checking the "many" side can find
what it created on the "one" side), store it on the *dependent* module's own table (e.g.
`Attendance.projectExpenseId`, `TransferLineItem.projectExpenseId`), not as a new column on the
owning module's entity — this keeps the FK pointing the same direction as the Maven dependency
and adds no schema-level dependency back onto the (deliberately more independent) owning module.
Note this means `ProjectExpense` itself carries no hint of who generated it — that lookup always
starts from the dependent side (a specific `Attendance` or `TransferLineItem` row), never from
the expense row outward.

**Real bug this shape caused, fixed — get delete order right**: that traceability column is a
plain `Long`, not a JPA relation, so Hibernate has no object-graph metadata to sequence deletes
across it — and the FK is a real, non-deferrable one (Postgres checks it immediately). The
original `AttendanceService.deleteAttendance` deleted the `ProjectExpense` first, which failed
with a foreign-key violation (surfaced as an undifferentiated 500) since the `Attendance` row
still referenced it. **The fix**: validate first (`ProjectExpenseService.assertExpenseDeletable`
— checks existence/project-match/editable without deleting, so a locked project still leaves
both rows untouched exactly as before), then delete the *dependent* row, then delete the
`ProjectExpense`. Any future code that deletes across one of these traceability columns (e.g. if
deleting a `TransferBatch` with generated expenses is ever supported) needs the same order.

## HTTP status code conventions (read before guessing one)

This has been a recurring point of friction — codes have **drifted between modules**, and it's
a deliberate, documented divergence in places, not an oversight:

- **404** — resource doesn't exist. `ResourceNotFoundException("EntityName", id)` in inventory;
  entity-specific `XNotFoundException` classes in equipment.
- **400** — the request is well-formed JSON but structurally invalid: wrong type of a
  referenced resource for this operation (`InvalidWarehouseTypeException`), origin/destination
  resolving to the same thing (`InvalidStockOperationException`'s same-warehouse check,
  `EquipmentAlreadyAtWarehouseException`), a batch's internal fields contradicting each other
  (`InvalidEquipmentBatchRequestException`), or bean validation failures.
- **409** — a *conflict discovered at operation time*, not a lifecycle issue:
  `InsufficientStockException` in inventory (mid-transfer stock conflict). In the **equipment**
  module specifically, 409 is also used for "equipment not in the right status for this
  operation" (`InvalidEquipmentStatusException`, `NoOpenAssignmentException`,
  `DuplicateAssetTagException`) — because `checkOut()`'s 409 predates the batch/transfer work,
  and staying consistent *within* equipment was judged more important than matching inventory.
  The **workers** module uses 409 the same way inventory does: `DuplicateAttendanceException`
  for a second attendance record on the same worker/day — a conflict discovered at operation
  time, not a wrong-status issue. Workers also uses 400 for `InactiveWorkerException` (recording
  attendance against a retired worker) — "the right kind of resource, wrong state for this
  operation," same bucket as inventory's `InactiveResourceException`.
- **422** — the resource has progressed past an editable/actionable lifecycle stage, in the
  **inventory** module: `ReceiptProcessingException` ("already confirmed"),
  `MaterialRequestNotEditableException`, `TransferBatchNotAwaitingPurchaseException`,
  `TransferBatchNotDeletableException`. This is inventory's "wrong status" convention — do not
  assume it applies to equipment, and do not silently reconcile the two modules without asking;
  the divergence is intentional. The **projects** module follows this same 422 convention
  (`ProjectNotEditableException`, for update/complete/add-expense once a project is
  `COMPLETED`/`CANCELLED`) — it was judged closer in nature to inventory's domain than
  equipment's.

When adding a new "wrong state" case, match the convention of the module you're in, and if a
choice is genuinely ambiguous, say so explicitly and pick one rather than guessing — the
OpenAPI spec is how the frontend verifies backend behavior, and an undocumented edge case here
has cost real frontend debugging time before.

## Permissions

One `@PreAuthorize("hasRole('X')")` string per mutating action, named `<MODULE>_<ACTION>`
(`EQUIPMENT_CHECKOUT`, `TRANSFER_BATCH_DELETE`, `EQUIPMENT_ASSIGNMENT_BATCH_SUBMIT`, etc.) — a
distinct permission per action, never reused across create/edit/delete/submit-type endpoints
even when they're related. Plain `GET` endpoints are typically left unguarded (any authenticated
caller). The OpenAPI spec never exposes `@PreAuthorize` role names (only the generic `bearerAuth`
scheme) — always state the exact string explicitly when it's relevant, never leave it to be
inferred from the spec.

## "Batch" entities (TransferBatch, EquipmentAssignmentBatch)

Recurring shape for "process many of X in one action instead of one-by-one," alongside (not
replacing) the existing single-item endpoints:

- `DRAFT` → `SUBMITTED` → `COMPLETED` status lifecycle.
- `submit()` is one transaction: if any line fails, nothing is applied and the batch stays in
  its prior state. It delegates each line to the service method that owns the underlying
  mutation rather than reimplementing the line-level logic — `EquipmentService.checkOut`/
  `checkIn` (the same method the single-item endpoint uses) for `EquipmentAssignmentBatch`, but
  a warehouse-total variant for `TransferBatch`: `InventoryService.transferWarehouseStock`, not
  the single-location `transferStock` the single-item `POST /api/inventory/transfer` endpoint
  uses. A `TransferBatch` line only ever specifies warehouses (no `locationId` field exists on
  it), so "does the origin warehouse have enough" has to mean summed across every
  `StorageLocation` in it, not one specific bucket — see `transferWarehouseStock`'s own javadoc
  for the debit order across multiple locations and how movement records stay accurate.
- Prefer **deriving** a concept (e.g. batch "direction") from other fields already on the
  request rather than storing it as its own field, when it can be derived reliably — see
  `EquipmentAssignmentBatch`'s direction, resolved from `destinationWarehouseId`'s `Warehouse.type`
  plus (per line, at submit time) the referenced equipment's current status, not a stored enum.
  `TransferBatch` derives a direction the same way for its own, separate purpose (auto-drafting a
  MATERIAL `ProjectExpense` when `projectId` is set): destination `Warehouse.type == SITE` means
  dispatch (positive amount), origin `Warehouse.type == SITE` means pull-out (negative amount) —
  see `TransferBatchService.generateProjectExpense`. Also not a stored field, computed fresh at
  submit time from the same warehouses already loaded for the stock transfer itself.

`POST /api/attendance/batch` (`workers`) is a lighter-weight variant of this shape worth
distinguishing: it processes many `Attendance` rows in one transaction but has **no persisted
batch entity at all** — no `DRAFT`/`SUBMITTED`/`COMPLETED` lifecycle, nothing to look up by a
batch id afterward. Don't add one unless a real need for it shows up; the all-or-nothing
transaction plus a `created`/`skipped` response is enough for what this endpoint does. It also
diverges from `TransferBatch`/`EquipmentAssignmentBatch` on one more point: a single expected,
non-error condition (a worker already having a record for that date) is *skipped and reported*
rather than aborting the batch — atomicity there applies only to genuine failures, not to that
case. See `AttendanceService.createBatch`'s own javadoc.

## Aggregation-root entities (MaterialRequest, PurchaseOrder) — recompute status cumulatively

A different recurring shape: a parent that's fulfilled incrementally by *multiple* child
records over time (`MaterialRequest` by however many `TransferBatch`es reference it via
`sourceMaterialRequestId`; `PurchaseOrder` by however many `PurchaseReceipt`s reference it via
`purchaseOrderId`), landing on `PARTIALLY_*`/fully-fulfilled status as those children complete.

**Known bug, don't copy it**: `TransferBatchService.updateLinkedMaterialRequestStatus` only
compares the *current* batch's transferred quantities against what the request's lines still
need — it does not sum across every batch previously submitted against that same request. Two
separate partial-fulfillment batches over time can therefore compute the wrong status. This
wasn't fixed in place (out of scope when found) but was **not** repeated:
`PurchaseOrderService.updateStatusFromReceipts` sums *every* `CONFIRMED` `PurchaseReceipt`
against the order, every time, via a repository query scoped to the parent id rather than the
just-processed child's lines. Do this (query-scoped-to-parent, not lines-just-processed) for any
new entity in this shape.

## Reentrant auto-flush: audited entity + cascade=ALL collection + a later query in the same method

A real, reproducible Hibernate bug (not a guess — reproduced locally by adding
`@EnableJpaAuditing` plus a stub `AuditorAware` to a `@DataJpaTest`, and it matched a production
stack trace exactly): if a method (a) mutates a field on an entity that is
`@EntityListeners(AuditingEntityListener.class)`-audited **and** has a `cascade=ALL` `@OneToMany`
collection (e.g. `PurchaseOrder.lines`, `PurchaseReceipt.lines`, `EquipmentAssignmentBatch.lines`),
then (b) triggers **any** further repository query before the transaction ends — the eventual
flush fires that entity's `@PreUpdate` callback, which calls `AuditorAwareImpl.getCurrentAuditor()`
(`app/.../config/AuditorAwareImpl.java`). When its per-request cache is cold, that method queries
`UserRepository`, and *that* query's own auto-flush check reentrantly re-enters the flush already
in progress for the same entity — Hibernate's flush isn't reentrant-safe for this, and throws
`HibernateException: Found shared references to a collection: <Entity>.<collection>` (surfaces as
a 500). This reproduces on **any** flush of the dirty entity while the cache is cold — reordering
statements inside the method does not help; an explicit early `flush()` throws the exact same way.

Only entities updated this way are at risk — a fresh `INSERT` (this codebase's ID strategy is
always `GenerationType.IDENTITY`) runs synchronously inside `save()`/`persist()`, so `createDraft()`-
style methods never hit this regardless of what runs afterward.

**The fix**: inject `AuditorAware<Long>` (the plain `org.springframework.data.domain` interface —
depending on it does not create a dependency on the `app` module; Spring wires in whatever bean
implements it) and call `auditorAware.getCurrentAuditor();` once, discarding the result, as the
first thing before the entity is mutated — this pre-warms the same per-request cache
`AuditorAwareImpl` already has, so the later reentrant call is a cache hit, not a query. Already
applied to `PurchaseOrderService.update/submit/close`, `PurchaseReceiptService.confirmPurchaseReceipt`,
and `EquipmentAssignmentBatchService.submit` — see `submit()`'s javadoc in `PurchaseOrderService`
for the full root-cause writeup, and `PurchaseOrderServiceIntegrationTest` for a real repro.
`CurrentUserService.getCurrentUserId()` does **not** help here — it queries `UserRepository`
directly with no caching of its own, unlike `AuditorAwareImpl`.

When adding a new method that updates one of these three (or any future) audited+cascade=ALL
entity and then queries again afterward, apply the same one-line warm-up call.

## Testing

- Service-layer: Mockito unit tests (`@ExtendWith(MockitoExtension.class)`), manual per-test
  stubbing (not blanket `@BeforeEach` stubs) to avoid tripping strict-stubbing checks.
- Mapper tests: instantiate the generated `*Impl` directly (no Spring context), wrap as a
  `Mockito.spy`, and inject `*LookupHelper` delegates via `ReflectionTestUtils.setField`.
- Repository tests: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` (module-local
  H2 in Postgres mode, not the auto-substituted embedded DB) + a module-specific
  `JpaAuditingTestConfig`. A module's `*TestApplication`'s `@EntityScan` needs every entity
  package a repository test actually persists — including another module's entity, if a test
  persists it directly to satisfy a real FK (e.g. equipment's tests persisting `Warehouse` rows).
- Controller tests: `@WebMvcTest`, service/mapper mocked via `@MockitoBean`, a local
  `authenticatedJwt(String... permissions)` helper building a JWT with `ROLE_<permission>`
  authorities.
- **Building a module whose dependency changed**: use `-am` (`./mvnw -pl equipment -am test`),
  not just `-pl equipment` — otherwise Maven reuses a stale local-repo jar for the dependency
  (e.g. `inventory`) and compilation fails with "cannot find symbol" for a class that very much
  exists.
- `app`'s `ComponentScanBeanNameUniquenessTest` replicates Spring's component-scan bean naming
  across every module's classes (no context boot, no DB) specifically to catch a
  `ConflictingBeanDefinitionException` from two modules' same-named `@Component`-family classes
  before it reaches real app startup — see that test's own javadoc and the naming-uniqueness
  rule above. No `@DataJpaTest`/`@WebMvcTest` slice test catches this, since none of them load
  every module together the way the real app does.

## Documentation expectations

Every commit that changes a module's API or behavior should update, in the same commit: the
module's `README.md` (create one if the module doesn't have one yet and just gained a notable
feature), the Swagger/OpenAPI annotations (`@Operation`/`@ApiResponses` — verify they're
accurate, not just present), and this file if the change touches a convention described here.
