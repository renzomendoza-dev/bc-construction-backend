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
- `POST /api/purchase-receipts` — create a draft receipt with line items
- `POST /api/purchase-receipts/{receiptId}/confirm` — confirm a draft receipt, posting `IN` stock movements for each line
- `GET /api/purchase-receipts/{receiptId}` — get by id
- `GET /api/purchase-receipts` — paginated list
- `GET /api/purchase-receipts/item/{itemId}/history` — purchase history for an item
- `POST /api/purchase-receipts/{receiptId}/image` (multipart) — upload a receipt image

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
| Bean validation failures | 400 (field-level `ValidationErrorResponse`) |
| Malformed JSON | 400 |
| `IllegalStateException` | 401 |
| Anything else | 500 |

`InvalidFileException` (bad upload content type/size) is mapped separately to 400 by
`InvalidFileExceptionHandler`, a standalone `@RestControllerAdvice`.

## Database migrations

Flyway migrations live in `src/main/resources/db/migration`, `V1` through `V14`, covering
items, item images, suppliers, item-supplier links, warehouses, storage locations, inventory
stock, stock movements, purchase receipts and lines, and later additions (audit columns on
stock movements/purchase receipts, a movement-type column rename, and an `active` flag on
storage locations).

## Testing

Tests use an in-memory H2 database (test-scoped dependency in `pom.xml`). Run with:

```bash
../mvnw -pl inventory test
```
