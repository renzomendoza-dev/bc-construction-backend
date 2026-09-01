# BC Construction Services — Backend

Multi-module Maven / Spring Boot 4.1 backend. Java 25, Hibernate 7, MapStruct, Lombok, Flyway,
Spring Security 7 with a Keycloak OAuth2 resource server.

## Modules

| Module | Purpose | Depends on |
|---|---|---|
| `user` | `AppUser`, admin user management, auth plumbing (`CurrentUserService`, `UserLookupHelper`, `AuditorAwareImpl`) | — |
| `inventory` | Items, warehouses, stock, purchase receipts, material requests, transfer batches | `user` |
| `equipment` | Equipment asset tracking, checkout/check-in, batch assignment/transfer/return | `user`, `inventory` |
| `sales` | Placeholder — one empty controller, not built out yet | — |
| `app` | Aggregator: the actual bootable Spring Boot application, wires every module together | all of the above |

Each module keeps its **own** exception vocabulary and its own `@RestControllerAdvice` scoped to
`basePackages = "com.bcconstructionservices.<module>.controller"` (e.g. `GlobalExceptionHandler`
in inventory, `EquipmentExceptionHandler` in equipment) — never an unscoped bare
`@RestControllerAdvice`. An unscoped one's catch-all `Exception.class` handler would apply to
every controller in the app once modules are wired together, colliding with every other
module's own catch-all.

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
- **422** — the resource has progressed past an editable/actionable lifecycle stage, in the
  **inventory** module: `ReceiptProcessingException` ("already confirmed"),
  `MaterialRequestNotEditableException`, `TransferBatchNotAwaitingPurchaseException`,
  `TransferBatchNotDeletableException`. This is inventory's "wrong status" convention — do not
  assume it applies to equipment, and do not silently reconcile the two modules without asking;
  the divergence is intentional.

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
  its prior state. It delegates each line to the same service method the single-item endpoint
  already uses (`InventoryService.transferStock`, `EquipmentService.checkOut`/`checkIn`) rather
  than reimplementing the line-level logic.
- Prefer **deriving** a concept (e.g. batch "direction") from other fields already on the
  request rather than storing it as its own field, when it can be derived reliably — see
  `EquipmentAssignmentBatch`'s direction, resolved from `destinationWarehouseId`'s `Warehouse.type`
  plus (per line, at submit time) the referenced equipment's current status, not a stored enum.

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

## Documentation expectations

Every commit that changes a module's API or behavior should update, in the same commit: the
module's `README.md` (create one if the module doesn't have one yet and just gained a notable
feature), the Swagger/OpenAPI annotations (`@Operation`/`@ApiResponses` — verify they're
accurate, not just present), and this file if the change touches a convention described here.
