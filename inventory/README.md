# Inventory Module

Maven module (`com.bcconstructionservices:inventory`) providing item catalog, warehouse/stock
location, stock movement, purchase receipt, and supplier management for the backend. It is
mounted into the `app` aggregator module and depends on `user` for auditing (resolving the
current user for `createdBy`/`confirmedBy` fields).

## Domain model

| Entity | Purpose |
|---|---|
| `Item` | Catalog entry — SKU, name, category, unit of measure, selling/default cost price, `active` flag, ordered `ItemImage`s. |
| `ItemImage` | An image attached to an item, with display order. |
| `Warehouse` | A physical warehouse, identified by `code`, with an `active` flag. |
| `StorageLocation` | A location within a `Warehouse` (e.g. a shelf/bin), with its own `code` and `active` flag. |
| `InventoryStock` | The current on-hand `quantity` and `reorderThreshold` for an `Item` at a given `Warehouse` (+ optional `StorageLocation`). |
| `StockMovement` | An immutable audit record of a stock change: `type` (`IN`, `OUT`, `TRANSFER`, `ADJUSTMENT`), quantity, from/to location, reason, and who made it. |
| `Supplier` | A vendor, with contact info and an `active` flag. |
| `ItemSupplier` | Many-to-many link between `Item` and `Supplier`. |
| `PurchaseReceipt` / `PurchaseReceiptLine` | A purchase from a supplier into a warehouse; draft until `confirmed`, at which point its lines post `IN` stock movements. |
| `MaterialRequest` / `MaterialRequestLineItem` | A site's request for materials to be pulled from a `MAIN` warehouse; editable until fulfillment starts, then locked. |
| `TransferBatch` / `TransferLineItem` | A batch move of stock from a `MAIN` warehouse to a `SITE` warehouse; optionally fulfills a `MaterialRequest`. |
| `PurchaseOrder` / `PurchaseOrderLine` | An order placed with a supplier before anything has physically arrived — an earlier stage than `PurchaseReceipt`. Editable while `DRAFT`, locked once `SUBMITTED`; one order can have multiple receipts against it over time. |

`Warehouse.type` (`MAIN` or `SITE`) distinguishes a stocked main/branch warehouse from a
project-site warehouse that only receives stock via transfer batches — material requests can
only be created against a `SITE` warehouse, and transfer batches move stock from `MAIN` to `SITE`.

`TransferBatch` blocked on insufficient stock is trackable, not silent: a `submit` that fails
specifically on `InsufficientStockException` sets the batch's status to `AWAITING_PURCHASE`
instead of leaving it looking like an untouched draft. `PurchaseReceipt.fulfillsTransferBatchId`
cross-references the blocked batch a purchase is resolving (must reference a batch that's
actually `AWAITING_PURCHASE`); confirming that receipt flips the batch back to `DRAFT` so it can
be resubmitted — see "Blocked transfer batches" below.

## API endpoints

All endpoints return `application/json` and validate request bodies with `@Valid`.

### Items — `/api/items`
- `POST /api/items` — create an item
- `PUT /api/items/{itemId}` — update an item
- `GET /api/items/{itemId}` — get by id
- `GET /api/items/sku/{sku}` — get by SKU
- `GET /api/items` — paginated list
- `PATCH /api/items/{itemId}/deactivate` — soft-deactivate
- `POST /api/items/{itemId}/images` — attach an image by URL
- `POST /api/items/{itemId}/images/upload` (multipart) — upload and attach an image file
- `PUT /api/items/{itemId}/images/reorder` — reorder an item's images
- `DELETE /api/items/images/{imageId}` — remove an image

### Inventory / stock — `/api/inventory`
- `GET /api/inventory/stock?itemId=&warehouseId=` — stock level for an item at a warehouse
- `GET /api/inventory` — paginated stock listing
- `GET /api/inventory/low-stock` — items at or below their reorder threshold
- `POST /api/inventory/adjust` — record a single-location `IN`/`OUT`/`ADJUSTMENT` movement
- `POST /api/inventory/transfer` — move stock between warehouses/locations (`TRANSFER`, produces two movement records)
- `GET /api/inventory/movements` — paginated movement history (filterable)
- `PATCH /api/inventory/reorder-threshold` — set the reorder threshold for an item/warehouse

### Warehouses — `/api/warehouses`
- `POST /api/warehouses` — create
- `PUT /api/warehouses/{warehouseId}` — update
- `GET /api/warehouses` — paginated list
- `PATCH /api/warehouses/{warehouseId}/deactivate` — soft-deactivate
- `POST /api/warehouses/locations` — add a storage location
- `GET /api/warehouses/{warehouseId}/locations` — list a warehouse's storage locations
- `PATCH /api/warehouses/locations/{locationId}/deactivate` — soft-deactivate a location

### Suppliers — `/api/suppliers`
- `POST /api/suppliers` — create
- `PUT /api/suppliers/{supplierId}` — update
- `GET /api/suppliers/{supplierId}` — get by id
- `GET /api/suppliers` — paginated list
- `PATCH /api/suppliers/{supplierId}/deactivate` — soft-deactivate
- `POST /api/suppliers/link-item` — link a supplier to an item
- `GET /api/suppliers/for-item/{itemId}` — list suppliers for an item

### Purchase receipts — `/api/purchase-receipts`
- `POST /api/purchase-receipts` — create a draft receipt with line items; optionally set `fulfillsTransferBatchId` to link it to a blocked transfer batch (422 if that batch isn't `AWAITING_PURCHASE`), and/or `purchaseOrderId` to link it to a purchase order (422 if that order is `RECEIVED`/`CLOSED`) — the two are independent, a receipt can carry either, both, or neither
- `POST /api/purchase-receipts/{receiptId}/confirm` — confirm a draft receipt, posting `IN` stock movements for each line, flipping any linked `AWAITING_PURCHASE` transfer batch back to `DRAFT`, and recomputing any linked purchase order's status (`PARTIALLY_RECEIVED`/`RECEIVED`)
- `GET /api/purchase-receipts/{receiptId}` — get by id
- `GET /api/purchase-receipts` — paginated list, filterable by `supplierId`/date range/`fulfillsTransferBatchId`
- `GET /api/purchase-receipts/item/{itemId}/history` — purchase history for an item
- `POST /api/purchase-receipts/{receiptId}/image` (multipart) — upload a receipt image

### Purchase orders — `/api/purchase-orders`
- `POST /api/purchase-orders` — create a draft order with line items
- `PUT /api/purchase-orders/{id}` — full-replacement update of `notes`/`lines`, `DRAFT` only (422 otherwise)
- `POST /api/purchase-orders/{id}/submit` — `DRAFT` → `SUBMITTED`, `DRAFT` only (422 otherwise); locks line items from this point
- `POST /api/purchase-orders/{id}/close` — manually terminate the order regardless of how much has been received (422 if already `RECEIVED`/`CLOSED`)
- `DELETE /api/purchase-orders/{id}` — delete a `DRAFT` order (422 otherwise). Independently rejected with 409 if any `PurchaseReceipt` already references it via `purchaseOrderId` — `createPurchaseReceipt` allows linking a receipt to a `DRAFT` order, so a still-`DRAFT` order can legitimately already have receipt history against it
- `GET /api/purchase-orders/{id}` — get by id, including per-line `receivedQuantity`
- `GET /api/purchase-orders` — paginated list, filterable by `supplierId`/`status`
- `GET /api/purchase-orders/suggestions?supplierId=` — suggested line items for a new order against a supplier; see "Purchase order suggestions" below

### Material requests — `/api/inventory/material-requests`
- `POST /api/inventory/material-requests` — create a request against a `SITE` warehouse (400 if the warehouse isn't type `SITE`)
- `PUT /api/inventory/material-requests/{id}` — full-replacement update of `dateNeeded`/`notes`/`lines` (explicit `null` clears a field); `siteWarehouseId` is immutable and not part of the body. 422 once status is `PARTIALLY_FULFILLED` or `FULFILLED`
- `DELETE /api/inventory/material-requests/{id}` — delete a request, same lock condition as `PUT` (422 once status is `PARTIALLY_FULFILLED` or `FULFILLED`). A request is persisted as `SUBMITTED` from creation — there is no separate draft/submit step — so this is what makes a mistaken or no-longer-needed request removable. Never touches an unsubmitted `TransferBatch`'s `sourceMaterialRequestId` — the batch is just left with no request behind it
- `GET /api/inventory/material-requests/{id}` — get by id
- `GET /api/inventory/material-requests` — paginated list, filterable by `siteWarehouseId`/`status`

There is deliberately no fulfill endpoint here — fulfillment happens by creating a transfer
batch with `sourceMaterialRequestId` set and submitting it, which is what advances this
request's status.

### Transfer batches — `/api/inventory/transfer-batches`
- `POST /api/inventory/transfer-batches` — create a draft batch (optionally against a `MaterialRequest` via `sourceMaterialRequestId`)
- `POST /api/inventory/transfer-batches/{id}/submit` — submit a draft batch, moving stock from the `MAIN` warehouse to the `SITE` warehouse for each line and updating the source request's status, if any. On 409 (insufficient stock), the batch's status is set to `AWAITING_PURCHASE` instead of being left as an untouched draft
- `DELETE /api/inventory/transfer-batches/{id}` — delete a `DRAFT` batch (422 for any other status). Never touches a `sourceMaterialRequestId`'s `MaterialRequest` — the request is just left with no draft transfer against it
- `GET /api/inventory/transfer-batches/{id}` — get by id
- `GET /api/inventory/transfer-batches` — paginated list

## Blocked transfer batches (linking Material Requests to Purchasing)

When `POST /{id}/submit` fails specifically on insufficient stock, the batch is marked
`AWAITING_PURCHASE` rather than silently reverting to `DRAFT` — the only durable trace of a
failed attempt. To resolve it:

1. Create a `PurchaseReceipt` for the shortfall item(s) with `fulfillsTransferBatchId` set to the
   blocked batch's id (422 if that batch isn't currently `AWAITING_PURCHASE`).
2. Confirm that receipt (`POST /{receiptId}/confirm`) — this automatically flips the batch back
   to `DRAFT`.
3. Resubmit the batch (`POST /{id}/submit`) — this step is manual, not automatic.

To find "the receipt resolving batch #Y" from the batch side, query
`GET /api/purchase-receipts?fulfillsTransferBatchId=Y` — `TransferBatchResponse` doesn't embed
the reverse reference itself.

`TransferBatchStatusUpdater` persists `AWAITING_PURCHASE` in its own `REQUIRES_NEW` transaction,
independent of the failed `submit()` call — by the time `submit()` catches
`InsufficientStockException`, `InventoryService.transferStock`'s own transactional advice has
already marked the ambient transaction rollback-only, so a plain write at that point would just
be discarded. Every stock transfer already applied earlier in that submit's loop is still rolled
back as before; only the `AWAITING_PURCHASE` marker survives.

## Purchase orders

`PurchaseOrder.status`: `DRAFT` → `SUBMITTED` → `PARTIALLY_RECEIVED` → `RECEIVED`, plus a manual
`CLOSED` reachable from any non-terminal status. `RECEIVED` and `CLOSED` are deliberately
separate terminal states — one means "every line's ordered quantity was fully covered by
confirmed receipts," the other means "manually abandoned regardless of coverage" (supplier
discontinued an item, the order was over-cautious, etc.) — so the two are never conflated.
`PARTIALLY_RECEIVED`/`RECEIVED` are recomputed automatically (`PurchaseOrderService.updateStatusFromReceipts`)
every time a `PurchaseReceipt` linked via `purchaseOrderId` is confirmed, cumulatively across
*every* confirmed receipt ever created against that order, not just the one just confirmed.

There is deliberately no auto-chaining for a `PARTIALLY_RECEIVED` order's remaining shortfall —
no new `TransferBatch`/shortfall is generated automatically. Re-querying
`GET /api/purchase-orders/suggestions?supplierId=` is the intended manual follow-up.

### Purchase order suggestions

`GET /api/purchase-orders/suggestions?supplierId=` combines three independent sources for a
given supplier:

1. Shortfall items on `AWAITING_PURCHASE` `TransferBatch` lines — re-checked against **current**
   stock at each batch's origin warehouse (not the stale moment the batch failed), since stock
   may have arrived from elsewhere since then.
2. Items at/below their reorder threshold (same data as `GET /api/inventory/low-stock`).
3. Items on open (`SUBMITTED`/`PARTIALLY_FULFILLED`) `MaterialRequest` lines not yet fully
   dispatched — "dispatched" is the sum of every `COMPLETED` `TransferBatch`'s lines sourced from
   that request.

Two deliberate calls worth knowing about:

- **Not filtered by `ItemSupplier`.** Suggestions are never restricted to items with an existing
  `ItemSupplier` link to the queried supplier — every candidate item is returned regardless, with
  `linkedToSupplier` telling the caller whether that link exists. Chosen over filtering because
  hiding a genuine shortfall due to an unpopulated `ItemSupplier` link would be worse than
  over-suggesting; suggestions are a starting point to edit, never a hard constraint.
- **Sources are summed, not deduplicated.** The same item can legitimately qualify from more than
  one source (e.g. a blocked `TransferBatch` sourced from a `MaterialRequest` that's therefore
  still open too) — their quantities are added together rather than cross-referenced to avoid
  double-counting. Accepted deliberately, for the same "err toward suggesting too much" reasoning.

## Stock adjustments vs. transfers

- **`POST /api/inventory/adjust`** — single-location change. `type` determines direction
  (`IN` increases, `OUT` decreases, `ADJUSTMENT` is a manual correction); `quantity` is always
  positive.
- **`POST /api/inventory/transfer`** — moves stock between two warehouse/location pairs in one
  call, always recorded as `TRANSFER`.

Movements are only ever inserted, never edited or deleted — `StockMovement` rows are the audit
trail for how an `InventoryStock.quantity` reached its current value.

## File uploads

Item and purchase-receipt images are handled by `FileStorageService`, which validates content
type (`image/jpeg`, `image/png`, `image/webp`) and file size, then writes to disk under a
type-specific subfolder. Configured via:

```yaml
app:
  storage:
    local-path: ${STORAGE_LOCAL_PATH:/app/uploads}   # filesystem root
    base-url: ${STORAGE_BASE_URL:http://localhost:8080/uploads}  # URL prefix stored in the DB
```

## Error handling

`GlobalExceptionHandler` maps domain exceptions to HTTP status codes:

| Exception | Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `InsufficientStockException` | 409 |
| `InvalidStockOperationException` | 400 |
| `InactiveResourceException` | 400 |
| `ReceiptProcessingException` | 422 |
| `MaterialRequestNotEditableException` | 422 |
| `TransferBatchNotAwaitingPurchaseException` | 422 |
| `TransferBatchNotDeletableException` | 422 |
| `PurchaseOrderNotEditableException` | 422 |
| `PurchaseOrderNotOpenException` | 422 |
| Bean validation failures | 400 (field-level `ValidationErrorResponse`) |
| Malformed JSON | 400 |
| `IllegalStateException` | 401 |
| Anything else | 500 |

`InvalidFileException` (bad upload content type/size) is mapped separately to 400 by
`InvalidFileExceptionHandler`, a standalone `@RestControllerAdvice`.

## Database migrations

Flyway migrations live in `src/main/resources/db/migration`, `V2` through `V25` (module-local —
the full version sequence is shared and global across all modules, so this module doesn't own
every number), covering items, item images, suppliers, item-supplier links, warehouses, storage
locations, inventory stock, stock movements, purchase receipts and lines, a `type` column added
to `warehouse` (`MAIN`/`SITE`), the transfer batch / material request tables, (`V23`) the
`AWAITING_PURCHASE` transfer batch status plus `purchase_receipt.fulfills_transfer_batch_id`, and
(`V25`) `purchase_order`/`purchase_order_line` plus `purchase_receipt.purchase_order_id`.
Dev-only demo data seeds live separately under `app/src/main/resources/db/dev-data` and are only
loaded when the `dev` Spring profile's `flyway.locations` override is active — never in prod.

## Testing

Tests use an in-memory H2 database (test-scoped dependency in `pom.xml`). Run with:

```bash
../mvnw -pl inventory test
```
