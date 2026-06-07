# API Specification

**Project:** StoreLense — RFID Store Operations Platform
**Base URL:** `http://<gateway-host>:8080`
**Auth:** `Authorization: Bearer <access_token>` on all endpoints except `/api/auth/login` and `/api/auth/refresh`
**Version:** 1.2 — 2026-06-07

---

## Common Response Envelope

All endpoints return:

```json
{
  "success": true,
  "data": { ... },
  "message": "OK",
  "timestamp": "2026-06-06T07:00:00Z"
}
```

Error responses:

```json
{
  "success": false,
  "data": null,
  "message": "Human-readable error",
  "code": "ERROR_CODE"
}
```

Paginated responses use `data.content`, `data.totalElements`, `data.totalPages`, `data.page`, `data.size`.

---

## 1. Auth Service — `/api/auth` and `/api/users`

### POST /api/auth/login

Authenticate with username and password. Returns JWT access token (15 min) and refresh token (7 days).

**Request:**
```json
{
  "username": "admin",
  "password": "Admin@StoreLense1"
}
```

**Response:**
```json
{
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "username": "admin",
      "roles": ["ADMIN"]
    }
  }
}
```

---

### POST /api/auth/refresh

Rotate refresh token; returns new access token and refresh token.

**Request:**
```json
{ "refreshToken": "eyJ..." }
```

---

### POST /api/auth/logout

Revoke tokens. Blacklists the access token in Redis.

**Request:** `Authorization: Bearer <token>` header only.

---

### GET /api/auth/me

Returns the currently authenticated user's profile from JWT claims.

---

### GET /api/users

List users. ADMIN can query all; store-scoped roles see own store only.

**Query params:** `storeId`, `role`, `page` (0-indexed), `size`

---

### GET /api/users/{id}

Get user by UUID.

---

### POST /api/users

Create a new user. ADMIN only.

**Request:**
```json
{
  "username": "mgr_p037",
  "email": "manager@store.com",
  "firstName": "Store",
  "lastName": "Manager",
  "password": "Manager@P0371!",
  "roles": ["STORE_MANAGER"],
  "storeId": "uuid"
}
```

---

### PUT /api/users/{id}

Update user details or roles. ADMIN only.

---

### DELETE /api/users/{id}

Soft-deactivate a user (sets `is_active = false`). ADMIN only.

---

## 2. Store Service — `/api/stores`

### GET /api/stores

List all active stores.

**Query params:** `page`, `size`, `search`

---

### GET /api/stores/{id}

Get store by UUID including zones and config.

---

### POST /api/stores

Create a new store. ADMIN only.

**Request:**
```json
{
  "storeCode": "P037",
  "name": "Pantaloons — Malviya Nagar",
  "addressLine1": "A-44 Malviya Nagar",
  "city": "Jaipur",
  "stateProvince": "Rajasthan",
  "postalCode": "302017",
  "countryCode": "IN",
  "timezone": "Asia/Kolkata",
  "erpStoreCode": "P037"
}
```

---

### PUT /api/stores/{id}

Update store details. ADMIN only.

---

### DELETE /api/stores/{id}

Soft-deactivate store. ADMIN only.

---

### GET /api/stores/{storeId}/zones

List zones for a store.

---

### POST /api/stores/{storeId}/zones

Create a zone. ADMIN only.

**Request:**
```json
{
  "zoneCode": "FLOOR-1",
  "name": "Ground Floor",
  "zoneType": "floor",
  "displayOrder": 1
}
```

`zoneType` values: `floor`, `backroom`, `fitting_room`, `stockroom`, `display`, `entrance`

---

### PUT /api/stores/{storeId}/zones/{zoneId}

Update zone details. ADMIN only.

---

### GET /api/stores/{storeId}/readers

List RFID readers registered for a store.

---

### POST /api/stores/{storeId}/readers

Register an RFID reader. ADMIN only.

**Request:**
```json
{
  "readerCode": "RDR-01",
  "readerType": "handheld",
  "zoneId": "uuid"
}
```

---

## 3. Product Service — `/api/products`

### GET /api/products

List or search products.

**Query params:** `search` (SKU or name), `brand`, `rfidEnabled` (true/false), `active` (true/false), `page`, `size`

---

### GET /api/products/{id}

Get product by UUID.

---

### GET /api/products/by-sku/{sku}

Get product by SKU.

---

### GET /api/products/epc/{epc}

Look up product by EPC string (Redis-cached, 30 min TTL).

**Response:**
```json
{ "data": { "epc": "3034257BF400B714...", "productId": "uuid" } }
```

---

### POST /api/products

Create a new product. ADMIN only.

**Request:**
```json
{
  "sku": "MENS-SHIRT-RED-M",
  "name": "Classic Red Shirt — Medium",
  "description": "Optional free-text description",
  "brand": "Arrow",
  "unitOfMeasure": "EACH",
  "rfidEnabled": true,
  "erpProductCode": "ERP-MENS-SHIRT-RED-M"
}
```

`unitOfMeasure` values: `EACH`, `PAIR`, `PACK`, `KG`, `LTR`

---

### PUT /api/products/{id}

Update product details. ADMIN only.

---

### POST /api/products/{id}/epc

Register an EPC tag against a product. ADMIN only.

**Request:**
```json
{ "epc": "3034257BF400B71400000064" }
```

---

## 4. Inventory Service — `/api/inventory`

### GET /api/inventory/state

Get current inventory state for a store.

**Query params:** `storeId` (required), `zoneId`, `productId`, `page`, `size`

**Response:** Paginated list of `{ productId, zoneId, quantityOnHand, quantityExpected, accuracyPct, lastCountedAt }`

---

### GET /api/inventory/low-accuracy

Products whose accuracy falls below a threshold.

**Query params:** `storeId` (required), `threshold` (default 95), `page`, `size`

---

### GET /api/inventory/epc-summary

EPC count breakdown by status (in_store, sold, missing, damaged, transferred) for a store.

**Query params:** `storeId` (required)

---

### POST /api/inventory/expected

Upsert ERP expected quantity for a store/product/zone. Used by erp-integration-service.

**Request:**
```json
{
  "storeId": "uuid",
  "productId": "uuid",
  "zoneId": "uuid",
  "quantityExpected": 12
}
```

---

## 5. SOH Service — `/api/soh`

### GET /api/soh/sessions

List SOH sessions for a store. Store-scoped.

**Query params:** `storeId`, `status`, `zoneId`, `page`, `size`

---

### GET /api/soh/sessions/{id}

Get session by UUID including result and variance items.

---

### POST /api/soh/sessions

Start a new SOH count session.

**Request:**
```json
{
  "storeId": "uuid",
  "zoneId": "uuid",
  "sessionType": "manual",
  "notes": "Weekly floor count"
}
```

---

### POST /api/soh/sessions/{id}/complete

Complete a session. Triggers SOH result calculation and variance generation.

---

### POST /api/soh/sessions/{id}/cancel

Cancel an in-progress session.

**Request:**
```json
{ "reason": "Scanner battery died" }
```

---

## 6. RFID Ingest Service — `/api/rfid/ingest`

### POST /api/rfid/ingest/batch

Submit a batch of EPC reads from a scan session. Used by the Zebra Android app.

**Request:**
```json
{
  "sessionId": "uuid",
  "storeId": "uuid",
  "reads": [
    { "epc": "3034257BF400B71400000064", "rssi": -45.5, "antennaPort": 1, "readAt": "2026-06-06T07:30:00Z" },
    { "epc": "3034257BF400B71400000065", "rssi": -48.2, "antennaPort": 1, "readAt": "2026-06-06T07:30:00Z" }
  ]
}
```

**Response:** `{ "accepted": 2, "duplicate": 0, "invalid": 0 }`

---

## 7. Refill Service — `/api/refill`

### GET /api/refill/tasks

List refill tasks. Store-scoped.

**Query params:** `storeId`, `status`, `assignedTo`, `page`, `size`

---

### GET /api/refill/tasks/{id}

Get refill task by UUID including items and assignment history.

---

### POST /api/refill/tasks

Create a refill task. STORE_MANAGER or ADMIN.

**Request:**
```json
{
  "storeId": "uuid",
  "taskType": "replenishment",
  "priority": 3,
  "dueDate": "2026-06-07",
  "notes": "Urgent floor restock",
  "items": [
    { "productId": "uuid", "zoneId": "uuid", "requestedQuantity": 10 },
    { "productId": "uuid", "zoneId": "uuid", "requestedQuantity": 6 }
  ]
}
```

---

### POST /api/refill/tasks/{id}/assign

Assign a task to a Refill Associate.

**Request:**
```json
{ "assigneeId": "uuid" }
```

---

### PATCH /api/refill/tasks/{taskId}/items/{itemId}/fulfil

Record fulfilment quantity for a single task item.

**Request:**
```json
{ "fulfilledQuantity": 8 }
```

---

### POST /api/refill/tasks/{id}/cancel

Cancel a task.

**Request:**
```json
{ "reason": "Product unavailable in stockroom" }
```

---

## 8. Reporting Service — `/api/reporting`

### GET /api/reporting/kpi/daily

Get daily KPI data for a store.

**Query params:** `storeId` (required), `page`, `size`

**Response fields per day:** `kpiDate`, `inventoryAccuracyPct`, `sohSessionsCount`, `refillTasksCreated`, `refillTasksCompleted`, `refillCompletionRatePct`, `avgRefillTimeMinutes`, `totalEpcReads`, `uniqueSkusCounted`, `varianceItemsCount`

---

### GET /api/reporting/kpi/range

Get KPI for a store within a date range.

**Query params:** `storeId`, `from` (YYYY-MM-DD), `to` (YYYY-MM-DD)

---

### POST /api/reporting/kpi/aggregate

Manually trigger KPI aggregation for a store and date. ADMIN only.

**Request:**
```json
{ "storeId": "uuid", "kpiDate": "2026-06-06" }
```

---

## 9. ERP Integration Service — `/api/erp/admin`

All endpoints require ADMIN role.

### POST /api/erp/admin/sync/products

Trigger a full product master sync from ERP.

**Response:** `{ "syncId": "uuid", "status": "completed", "fetched": 1200, "published": 1195, "failed": 5 }`

---

### POST /api/erp/admin/sync/inventory

Trigger expected-inventory sync from ERP.

---

### GET /api/erp/admin/sync/logs

List sync audit logs.

**Query params:** `syncType` (PRODUCT_INBOUND, INVENTORY_INBOUND, SOH_OUTBOUND), `page`, `size`

---

### GET /api/erp/admin/sync/logs/latest

Get the last successful sync record for each type.

---

## 10. RFID Processing — Dead Letter Queue — `/api/rfid/dlq`

### GET /api/rfid/dlq

List recently failed RFID read events (up to 1000). ADMIN only.

---

### POST /api/rfid/dlq/{key}/replay

Re-publish a failed event back to the `rfid.reads.raw` Kafka topic. ADMIN only.

---

## 11. WebSocket — `/ws`

Real-time push events via WebSocket (STOMP over SockJS).

**Connection:** `ws://<gateway-host>:8080/ws`

### Topics

| Topic | Event | Payload |
|---|---|---|
| `/topic/store/{storeId}/soh` | SOH session status update | `{ sessionId, status, accuracyPct }` |
| `/topic/store/{storeId}/refill` | New refill task available | `{ taskId, priority, itemCount }` |
| `/topic/user/{userId}/tasks` | Task assigned to user | `{ taskId, storeId }` |
| `/topic/store/{storeId}/alerts` | Low-accuracy alert | `{ productId, accuracyPct, threshold }` |

---

## 12. HTTP Status Codes Used

| Code | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 400 | Bad request / validation error |
| 401 | Missing or invalid token |
| 403 | Insufficient role |
| 404 | Resource not found |
| 409 | Conflict (duplicate SKU, session already running, etc.) |
| 500 | Internal server error |
