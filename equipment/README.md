# Equipment Module

Maven module (`com.bcconstructionservices:equipment`) providing equipment asset tracking,
checkout/check-in, and batch assignment/return for the backend. It is mounted into the `app`
aggregator module and depends on `user` for auditing/holder resolution and on `inventory` for
`Warehouse` — every location a piece of equipment can be at is a `Warehouse` row (`SITE` while
checked out, `MAIN` once returned), the same model material inventory already uses.

## Domain model

| Entity | Purpose |
|---|---|
| `Equipment` | A physical asset — asset tag, name, category, serial number, `status`, current holder, and current warehouse. `currentWarehouseId` is always populated for equipment registered after V24 (see Rollout notes below). |
| `EquipmentAssignment` | An immutable history record of one checkout/check-in cycle: who took it, which warehouse it went to, which warehouse it came back to, condition notes out/in. |
| `EquipmentAssignmentBatch` / `EquipmentAssignmentBatchLine` | A batch move of one or more pieces of equipment: out to a `SITE` warehouse (assign-out), directly to a *different* `SITE` warehouse (transfer), or back to a `MAIN` warehouse (return) — the equipment-tracking analogue of inventory's `TransferBatch`. |

`EquipmentStatus`: `AVAILABLE`, `CHECKED_OUT`, `IN_USE`, `IN_REPAIR`, `RETIRED`, `LOST`,
`MAINTENANCE`.

`EquipmentAssignmentBatchStatus`: `DRAFT` → `SUBMITTED` → `COMPLETED`. There's no
`AWAITING_PURCHASE`-style blocked state here — equipment isn't purchased through this flow.

Cross-module references to `Warehouse` (and to `AppUser` for holders) are plain `Long` id
columns, not JPA associations — `Warehouse` lives in the `inventory` module — but are real FKs
at the DB level (both tables already exist in the same physical database well before these
migrations run, so there's no ordering reason to skip the FK). Display names
(`currentWarehouseName`, `holderName`, etc.) are resolved via `WarehouseLookupHelper`
(inventory module) and `UserLookupHelper` (user module), the same `@Named`-qualified
MapStruct-lookup pattern used throughout this codebase for exactly this kind of cross-module
id-to-display-name resolution.

## API endpoints

### Equipment — `/api/equipment`
- `POST /api/equipment` — register new equipment at a `MAIN`-type warehouse (`warehouseId` required)
- `PATCH /api/equipment/{id}` — update name/category/serial/purchase info (status, holder, and warehouse are not editable here)
- `GET /api/equipment` — list, optionally filtered by status
- `GET /api/equipment/{id}` — get by id
- `POST /api/equipment/{id}/checkout` — check out to a user at a `SITE`-type warehouse (`siteWarehouseId`); also handles a direct site-to-site transfer if the equipment is already `CHECKED_OUT`/`IN_USE` elsewhere
- `POST /api/equipment/{id}/checkin` — check in to a `MAIN`-type warehouse (`destinationWarehouseId`)
- `GET /api/equipment/overdue?days=` — equipment checked out longer than the given number of days

### Equipment assignment batches — `/api/equipment/assignment-batches`
- `POST /api/equipment/assignment-batches` — create a draft batch (assign-out, transfer, or return; see below)
- `POST /api/equipment/assignment-batches/{id}/submit` — submit a draft batch, applying every line in one transaction
- `GET /api/equipment/assignment-batches/{id}` — get by id
- `GET /api/equipment/assignment-batches` — list, optionally filtered by status

The single-item checkout/check-in endpoints stay available for one-off cases — the batch flow
is additive (multi-select equipment + "Batch Assign"/"Batch Return"), not a replacement, same
coexistence pattern as inventory's single-location Transfer modal staying alongside Transfer
Batches.

## Batch direction: derived, not stored

A batch's direction is never its own field. `destinationWarehouseId`'s resolved `Warehouse.type`
only decides whether `holderId` is required, not which of three directions the batch turns out
to be:

- **`SITE`** → `holderId` required. Covers **both** assign-out (that line's equipment is
  currently `AVAILABLE`) **and** direct site-to-site transfer (that equipment is already
  `CHECKED_OUT`/`IN_USE` at a *different* site) — which one a given line actually is depends on
  that specific equipment's status, resolved per line inside `EquipmentService.checkOut` itself
  at submit time, not at draft creation.
- **`MAIN`** → `holderId` must be omitted/null. Always a return; every line's equipment must
  currently be `CHECKED_OUT` or `IN_USE`.

`submit()` only needs to know "does this batch have a holder" to pick `checkOut` vs. `checkIn`
per line — `checkOut` internally branches assign-out vs. transfer (and closes the old
`EquipmentAssignment` + opens a new one for a transfer, rather than mutating one row in place),
`checkIn` still only ever means return. Both are the same single-item logic used by the
endpoints above, not a reimplementation. A transfer line targeting the warehouse that equipment
is already at is rejected with 400 (`EquipmentAlreadyAtWarehouseException`). The whole submit is
one transaction: if any line fails, nothing is applied and the batch stays in its prior state.

## Error handling

`EquipmentExceptionHandler` maps domain exceptions to HTTP status codes:

| Exception | Status |
|---|---|
| `EquipmentNotFoundException` | 404 |
| `WarehouseNotFoundException` | 404 |
| `EquipmentAssignmentBatchNotFoundException` | 404 |
| `InvalidCheckoutUserException` | 404 |
| `DuplicateAssetTagException` | 409 |
| `InvalidEquipmentStatusException` | 409 |
| `NoOpenAssignmentException` | 409 |
| `InvalidWarehouseTypeException` | 400 |
| `InvalidEquipmentBatchRequestException` | 400 |
| `EquipmentAlreadyAtWarehouseException` | 400 |
| Bean validation failures | 400 |

**A deliberate note on 409 vs. 422**: this module maps "equipment not in the right status for
this operation" to 409 (`InvalidEquipmentStatusException`), extending the convention the
single-item `checkOut()` already established, rather than the 422 the inventory module uses for
its own analogous "lifecycle already progressed" cases (e.g. `MaterialRequestNotEditableException`,
`TransferBatchNotAwaitingPurchaseException`). Consistency **within** this module was judged to
matter more than consistency across modules, since equipment's own existing behavior was already
409 before the batch endpoint existed.

## Rollout: existing equipment's location after V24

`current_site` (free text) was replaced with `current_warehouse_id` (FK to `warehouse`) in V24.
Existing free-text values can't be reliably mapped to a real `Warehouse` row, so this migration
does **not** attempt fuzzy-matching or a forced reconciliation — `current_warehouse_id` starts
NULL for every pre-existing row (both `AVAILABLE` equipment, which already had no tracked
location, and `CHECKED_OUT`/`IN_USE` equipment, which loses its old site text). Each row
self-heals to non-null the next time it goes through checkout or check-in, since both now
require and set this field. Equipment registered after V24 always has it populated from
creation.

## Testing

Tests use an in-memory H2 database (test-scoped dependency in `pom.xml`). Because `equipment`
now depends on `inventory`, `EquipmentTestApplication`'s Flyway configuration picks up
inventory's migrations too (needed for the `warehouse` table these FKs reference) — repository
slice tests that persist a `Warehouse` row do so directly against the real `Warehouse` entity
(scanned via `EquipmentTestApplication`'s `@EntityScan`, added purely for test purposes —
`equipment`'s own entities never hold a JPA relationship to it). Run with:

```bash
../mvnw -pl equipment test
```
