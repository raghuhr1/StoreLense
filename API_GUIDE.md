# StoreLense — Complete API Reference

> **Base URL:** `http://<server>:8080`  
> **All requests** (except login, refresh, and public store/product endpoints) require:  
> `Authorization: Bearer <accessToken>`  
> **All responses** follow the envelope: `{ "success": true, "code": "OK", "message": "...", "data": ... }`

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Users](#2-users)
3. [Stores](#3-stores)
4. [Zones](#4-zones)
5. [Products](#5-products)
6. [Inventory](#6-inventory)
7. [SOH Sessions](#7-soh-sessions)
8. [Refill Tasks](#8-refill-tasks)
9. [RFID Ingest](#9-rfid-ingest)
10. [RFID Dead Letter Queue](#10-rfid-dead-letter-queue)
11. [Reporting](#11-reporting)
12. [ERP Integration](#12-erp-integration)
13. [Common Patterns](#13-common-patterns)
14. [Error Codes](#14-error-codes)

---

## 1. Authentication

**Base path:** `/api/auth` — Port 8081 (proxied via gateway on 8080)  
**All endpoints in this section are public (no token required).**

---

### POST /api/auth/login

Authenticate with username and password. Returns JWT access + refresh tokens.

**Request**
```http
POST /api/auth/login
Content-Type: application/json
```
```json
{
  "username": "admin",
  "password": "Admin@StoreLense1"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `username` | string | yes | min 1 char |
| `password` | string | yes | min 1 char |

**Response `200 OK`**
```json
{
  "success": true,
  "code": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "a1b2c3d4-...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "admin",
    "role": "ADMIN",
    "storeId": null
  }
}
```

> **Token lifetimes:** Access token = 15 minutes. Refresh token = 7 days.

---

### POST /api/auth/refresh

Rotate an expiring refresh token and receive a new access token.

**Request**
```http
POST /api/auth/refresh
Content-Type: application/json
```
```json
{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response `200 OK`** — Same shape as `/login`.

---

### POST /api/auth/logout

Invalidate the current session. The access token is blacklisted in Redis and the refresh token is revoked.

**Request**
```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "refreshToken": "a1b2c3d4-..."
}
```
> `refreshToken` is optional. If omitted, only the access token is blacklisted.

**Response `200 OK`**
```json
{ "success": true, "code": "OK", "data": null }
```

---

### GET /api/auth/me

Return the currently authenticated user's profile from JWT claims.

**Request**
```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — Returns Spring Security `Authentication` object with principal details.

---

## 2. Users

**Base path:** `/api/users` — Hosted on auth-service (port 8081).

---

### GET /api/users

List all users. Non-admin users automatically see only users in their own store.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/users?storeId=<uuid>&page=0&size=20
Authorization: Bearer <accessToken>
```

| Query param | Type | Required | Description |
|---|---|---|---|
| `storeId` | UUID | no | Filter by store (Admin only) |
| `page` | int | no | Page index, default 0 |
| `size` | int | no | Page size, default 20 |

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "id": "550e8400-...",
        "username": "jsmith",
        "email": "jsmith@store.com",
        "firstName": "John",
        "lastName": "Smith",
        "storeId": "a1b2c3d4-...",
        "active": true,
        "roles": ["STORE_ASSOCIATE"],
        "lastLoginAt": "2026-06-04T09:30:00Z",
        "createdAt": "2026-01-15T08:00:00Z"
      }
    ],
    "page": 0, "size": 20, "totalElements": 45, "totalPages": 3, "last": false
  }
}
```

---

### GET /api/users/{id}

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/users/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — Single `UserResponse` object (see above).

---

### POST /api/users

Create a new user account.

**Roles:** `ADMIN`

```http
POST /api/users
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "username": "jsmith",
  "email": "jsmith@store.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Smith",
  "storeId": "a1b2c3d4-...",
  "roles": ["STORE_ASSOCIATE"]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `username` | string | yes | 3–100 chars, unique |
| `email` | string | yes | valid email, unique |
| `password` | string | yes | min 8 chars |
| `firstName` | string | yes | max 100 chars |
| `lastName` | string | yes | max 100 chars |
| `storeId` | UUID | no | Required for non-ADMIN roles |
| `roles` | string[] | yes | One of: `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`, `REFILL_ASSOCIATE` |

**Response `201 Created`** — `UserResponse` object.

---

### PUT /api/users/{id}

Update a user's details.

**Roles:** `ADMIN`

```http
PUT /api/users/550e8400-...
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "email": "new@email.com",
  "firstName": "Johnny",
  "lastName": "Smith",
  "storeId": "b2c3d4e5-...",
  "roles": ["STORE_MANAGER"],
  "active": true
}
```
All fields are optional — send only what you want to change.

**Response `200 OK`** — Updated `UserResponse`.

---

### DELETE /api/users/{id}

Deactivate a user (soft delete — data is preserved).

**Roles:** `ADMIN`

```http
DELETE /api/users/550e8400-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 3. Stores

**Base path:** `/api/stores` — Port 8082.

---

### GET /api/stores

List all active stores (paginated). **Public — no auth required.**

```http
GET /api/stores?page=0&size=50
```

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "id": "a1b2c3d4-...",
        "storeCode": "SYD001",
        "name": "Sydney CBD",
        "addressLine1": "100 George Street",
        "addressLine2": "",
        "city": "Sydney",
        "stateProvince": "NSW",
        "postalCode": "2000",
        "countryCode": "AU",
        "timezone": "Australia/Sydney",
        "active": true,
        "erpStoreCode": "ERP-SYD-001",
        "createdAt": "2025-01-01T00:00:00Z",
        "updatedAt": "2025-06-01T00:00:00Z"
      }
    ],
    "page": 0, "size": 50, "totalElements": 12, "totalPages": 1, "last": true
  }
}
```

---

### GET /api/stores/{id}

**Public.** Get a single store by UUID.

```http
GET /api/stores/a1b2c3d4-...
```

**Response `200 OK`** — Single `StoreResponse`.

---

### POST /api/stores

Create a new store.

**Roles:** `ADMIN`

```http
POST /api/stores
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "storeCode": "MEL001",
  "name": "Melbourne Central",
  "addressLine1": "211 La Trobe Street",
  "city": "Melbourne",
  "stateProvince": "VIC",
  "postalCode": "3000",
  "countryCode": "AU",
  "timezone": "Australia/Melbourne",
  "erpStoreCode": "ERP-MEL-001"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `storeCode` | string | yes | max 20 chars, unique |
| `name` | string | yes | max 255 chars |
| `countryCode` | string | no | 2 uppercase letters, e.g. `AU` |
| `timezone` | string | no | IANA timezone, e.g. `Australia/Sydney` |

**Response `201 Created`** — `StoreResponse`.

---

### PUT /api/stores/{id}

Update store details.

**Roles:** `ADMIN`

```http
PUT /api/stores/a1b2c3d4-...
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "name": "Melbourne Central (Flagship)",
  "active": true
}
```

**Response `200 OK`** — Updated `StoreResponse`.

---

### DELETE /api/stores/{id}

Deactivate a store.

**Roles:** `ADMIN`

```http
DELETE /api/stores/a1b2c3d4-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 4. Zones

**Base path:** `/api/stores/{storeId}/zones`

---

### GET /api/stores/{storeId}/zones

List all zones for a store. **Public.**

```http
GET /api/stores/a1b2c3d4-.../zones
```

**Response `200 OK`**
```json
{
  "data": [
    {
      "id": "z1z2z3z4-...",
      "storeId": "a1b2c3d4-...",
      "zoneCode": "FLOOR-A",
      "name": "Ground Floor",
      "zoneType": "floor",
      "displayOrder": 1,
      "active": true
    }
  ]
}
```

**Zone types:** `floor`, `backroom`, `fitting_room`, `stockroom`, `display`

---

### POST /api/stores/{storeId}/zones

Create a zone.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
POST /api/stores/a1b2c3d4-.../zones
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "zoneCode": "BACK-01",
  "name": "Main Backroom",
  "zoneType": "backroom",
  "displayOrder": 2
}
```

**Response `201 Created`** — `ZoneResponse`.

---

### PUT /api/stores/{storeId}/zones/{zoneId}

Update a zone.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
PUT /api/stores/a1b2c3d4-.../zones/z1z2z3z4-...
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "name": "Main Backroom (Renovated)",
  "displayOrder": 3,
  "active": true
}
```

**Response `200 OK`** — Updated `ZoneResponse`.

---

## 5. Products

**Base path:** `/api/products` — Port 8083.

---

### GET /api/products

List or search products. **Public.**

```http
GET /api/products?search=denim&page=0&size=50
```

| Query param | Type | Description |
|---|---|---|
| `search` | string | Full-text search on name, SKU, brand |
| `page` | int | Page index |
| `size` | int | Page size, default 50 |

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "id": "p1p2p3p4-...",
        "sku": "APP-DNM-001",
        "name": "Classic Denim Jeans",
        "description": "Regular fit blue denim",
        "categoryId": "c1c2c3c4-...",
        "brand": "StoreLense Basic",
        "supplierCode": "SUP-001",
        "erpProductCode": "ERP-P-00123",
        "unitOfMeasure": "EACH",
        "weightGrams": 650,
        "rfidEnabled": true,
        "active": true,
        "erpSyncedAt": "2026-06-01T02:00:00Z",
        "createdAt": "2025-01-15T00:00:00Z"
      }
    ],
    "page": 0, "size": 50, "totalElements": 1247, "totalPages": 25, "last": false
  }
}
```

---

### GET /api/products/{id}

**Public.** Get product by UUID.

```http
GET /api/products/p1p2p3p4-...
```

---

### GET /api/products/by-sku/{sku}

**Public.** Get product by SKU code.

```http
GET /api/products/by-sku/APP-DNM-001
```

---

### GET /api/products/epc/{epc}

**Public.** Look up which product an EPC tag belongs to. Result is Redis-cached for fast RFID processing.

```http
GET /api/products/epc/3034257BF400B71400000064
```

**Response `200 OK`**
```json
{
  "data": {
    "epc": "3034257BF400B71400000064",
    "productId": "p1p2p3p4-...",
    "fromCache": true
  }
}
```

---

### POST /api/products

Create a product.

**Roles:** `ADMIN`

```http
POST /api/products
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "sku": "APP-TEE-007",
  "name": "Cotton Crew Tee",
  "description": "100% cotton crew neck",
  "categoryId": "c1c2c3c4-...",
  "brand": "StoreLense Basic",
  "erpProductCode": "ERP-P-00456",
  "unitOfMeasure": "EACH",
  "weightGrams": 200,
  "rfidEnabled": true
}
```

**Response `201 Created`** — `ProductResponse`.

---

### PUT /api/products/{id}

Update product details.

**Roles:** `ADMIN`

```http
PUT /api/products/p1p2p3p4-...
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "name": "Cotton Crew Tee (Updated)",
  "rfidEnabled": false,
  "active": false
}
```

**Response `200 OK`** — Updated `ProductResponse`.

---

### POST /api/products/{id}/epc

Associate an EPC tag with a product. Used when provisioning new RFID tags.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
POST /api/products/p1p2p3p4-.../epc?epc=3034257BF400B71400000065
Authorization: Bearer <accessToken>
```

**Response `201 Created`** — `{ "data": null }`

---

## 6. Inventory

**Base path:** `/api/inventory` — Port 8084.

---

### GET /api/inventory/state

Get current inventory state for a store. Non-admins see only their own store.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/inventory/state?storeId=a1b2c3d4-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": [
    {
      "productId": "p1p2p3p4-...",
      "storeId": "a1b2c3d4-...",
      "zoneId": "z1z2z3z4-...",
      "quantityOnHand": 18,
      "quantityExpected": 20,
      "accuracyPct": 90.00,
      "lastCountedAt": "2026-06-04T14:30:00Z"
    }
  ]
}
```

---

### GET /api/inventory/low-accuracy

Get products whose inventory accuracy falls below a threshold.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/inventory/low-accuracy?storeId=a1b2c3d4-...&threshold=95.0
Authorization: Bearer <accessToken>
```

| Query param | Type | Default | Description |
|---|---|---|---|
| `storeId` | UUID | required | — |
| `threshold` | double | `95.0` | Accuracy % cutoff |

**Response `200 OK`** — List of `InventoryState` objects below threshold.

---

### GET /api/inventory/epc-summary

Count of EPC tags by status for a store.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/inventory/epc-summary?storeId=a1b2c3d4-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "in_store": 4820,
    "missing": 142,
    "sold": 18340,
    "damaged": 23
  }
}
```

---

## 7. SOH Sessions

**Base path:** `/api/soh/sessions` — Port 8085.

---

### GET /api/soh/sessions

List SOH count sessions for a store. Non-admins see only their store.

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`

```http
GET /api/soh/sessions?storeId=a1b2c3d4-...&status=completed&page=0&size=20
Authorization: Bearer <accessToken>
```

| Query param | Type | Description |
|---|---|---|
| `storeId` | UUID | required |
| `status` | string | Filter: `created`, `in_progress`, `completed`, `cancelled`, `failed` |

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "id": "s1s2s3s4-...",
        "storeId": "a1b2c3d4-...",
        "zoneId": null,
        "sessionType": "full_store",
        "status": "completed",
        "startedBy": "u1u2u3u4-...",
        "startedAt": "2026-06-04T08:00:00Z",
        "completedAt": "2026-06-04T09:45:00Z",
        "totalEpcReads": 28450,
        "uniqueEpcCount": 5124,
        "notes": "Weekly full count"
      }
    ],
    "page": 0, "size": 20, "totalElements": 48, "totalPages": 3, "last": false
  }
}
```

**Session types:** `full_store`, `spot_check`, `manual`

---

### GET /api/soh/sessions/{id}

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`

```http
GET /api/soh/sessions/s1s2s3s4-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — Single `SohSessionResponse`.

---

### POST /api/soh/sessions

Start a new SOH count session.

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`

```http
POST /api/soh/sessions
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "storeId": "a1b2c3d4-...",
  "zoneId": "z1z2z3z4-...",
  "sessionType": "spot_check",
  "notes": "Denim section recount after variance"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `storeId` | UUID | yes | Target store |
| `zoneId` | UUID | no | Scope to a single zone |
| `sessionType` | string | no | `full_store`, `spot_check`, `manual` |
| `notes` | string | no | Free-text notes |

**Response `201 Created`** — `SohSessionResponse` with status `created`.

---

### POST /api/soh/sessions/{id}/complete

Complete a session and generate the accuracy result.

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`

```http
POST /api/soh/sessions/s1s2s3s4-.../complete
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "id": "r1r2r3r4-...",
    "sessionId": "s1s2s3s4-...",
    "storeId": "a1b2c3d4-...",
    "totalProductsCounted": 312,
    "totalUnitsCounted": 5124,
    "totalUnitsExpected": 5200,
    "accuracyPct": 98.54,
    "varianceCount": 14,
    "overcountItems": 3,
    "undercountItems": 11,
    "resultGeneratedAt": "2026-06-04T09:45:00Z"
  }
}
```

---

### POST /api/soh/sessions/{id}/cancel

Cancel an in-progress session.

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`

```http
POST /api/soh/sessions/s1s2s3s4-.../cancel?reason=Scanner+battery+died
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 8. Refill Tasks

**Base path:** `/api/refill/tasks` — Port 8086.

---

### GET /api/refill/tasks

List refill tasks. Non-admins see only their store's tasks.

**Roles:** `ADMIN`, `STORE_MANAGER`, `REFILL_ASSOCIATE`

```http
GET /api/refill/tasks?storeId=a1b2c3d4-...&status=pending&page=0&size=20
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "id": "t1t2t3t4-...",
        "storeId": "a1b2c3d4-...",
        "taskType": "replenishment",
        "status": "assigned",
        "priority": 3,
        "source": "soh_trigger",
        "sourceSessionId": "s1s2s3s4-...",
        "dueDate": "2026-06-05",
        "notes": "Low accuracy on denim",
        "createdBy": "u1u2u3u4-...",
        "createdAt": "2026-06-04T10:00:00Z",
        "completedAt": null,
        "items": [
          {
            "id": "i1i2i3i4-...",
            "productId": "p1p2p3p4-...",
            "zoneId": "z1z2z3z4-...",
            "requestedQuantity": 15,
            "fulfilledQuantity": 0,
            "status": "pending"
          }
        ]
      }
    ],
    "page": 0, "size": 20, "totalElements": 7, "totalPages": 1, "last": true
  }
}
```

**Task types:** `replenishment`, `urgency`, `cycle_count`  
**Task sources:** `manual`, `soh_trigger`, `scheduled`, `erp`  
**Task statuses:** `pending`, `assigned`, `in_progress`, `completed`, `cancelled`  
**Item statuses:** `pending`, `fulfilled`, `partial`, `skipped`  
**Priority:** 1 (highest) → 10 (lowest)

---

### GET /api/refill/tasks/{id}

**Roles:** `ADMIN`, `STORE_MANAGER`, `REFILL_ASSOCIATE`

```http
GET /api/refill/tasks/t1t2t3t4-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — Single `RefillTaskResponse` with all items.

---

### POST /api/refill/tasks

Create a new refill task.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
POST /api/refill/tasks
Authorization: Bearer <accessToken>
Content-Type: application/json
```
```json
{
  "storeId": "a1b2c3d4-...",
  "taskType": "replenishment",
  "priority": 2,
  "source": "manual",
  "dueDate": "2026-06-05",
  "notes": "Urgent restock for weekend",
  "items": [
    {
      "productId": "p1p2p3p4-...",
      "zoneId": "z1z2z3z4-...",
      "requestedQuantity": 20
    },
    {
      "productId": "p5p6p7p8-...",
      "requestedQuantity": 10
    }
  ]
}
```

**Response `201 Created`** — `RefillTaskResponse`.

---

### POST /api/refill/tasks/{id}/assign

Assign a task to a refill associate. Triggers a WebSocket notification to the assignee.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
POST /api/refill/tasks/t1t2t3t4-.../assign?assignedTo=u5u6u7u8-...
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — Updated `RefillTaskResponse` with status `assigned`.

---

### PATCH /api/refill/tasks/{taskId}/items/{itemId}/fulfil

Record the actual quantity fulfilled for a task line item.

**Roles:** `ADMIN`, `STORE_MANAGER`, `REFILL_ASSOCIATE`

```http
PATCH /api/refill/tasks/t1t2t3t4-.../items/i1i2i3i4-.../fulfil?quantity=15
Authorization: Bearer <accessToken>
```

- If `quantity >= requestedQuantity` → item status: `fulfilled`
- If `0 < quantity < requestedQuantity` → item status: `partial`
- When all items are in terminal state → task status auto-transitions to `completed`

**Response `200 OK`** — Updated `RefillTaskResponse`.

---

### POST /api/refill/tasks/{id}/cancel

Cancel a refill task.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
POST /api/refill/tasks/t1t2t3t4-.../cancel?reason=Stock+arrived+from+HQ
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 9. RFID Ingest

**Base path:** `/api/rfid/ingest` — Port 8087.

This is the high-throughput endpoint used by the Zebra Android app to upload RFID scan batches. Publishes to Kafka topic `rfid.reads.raw` and returns immediately (202 Accepted).

---

### POST /api/rfid/ingest/batch

Submit a batch of EPC reads from a scan session.

**Roles:** `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`, `REFILL_ASSOCIATE`

```http
POST /api/rfid/ingest/batch
Authorization: Bearer <accessToken>
Content-Type: application/json
X-Correlation-Id: <optional-uuid>
```
```json
{
  "rfidSessionId": "s1s2s3s4-e5f6-7890-abcd-ef1234567890",
  "storeId": "a1b2c3d4-...",
  "readerId": "r1r2r3r4-...",
  "deviceId": "zebra-tc52-001",
  "reads": [
    {
      "epc": "3034257BF400B71400000001",
      "rssi": -68.5,
      "antennaPort": 0,
      "readAt": "2026-06-04T08:30:00.000Z"
    },
    {
      "epc": "3034257BF400B71400000002",
      "rssi": -72.1,
      "antennaPort": 1,
      "readAt": "2026-06-04T08:30:00.100Z"
    }
  ]
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `rfidSessionId` | UUID | yes | Must match an existing SOH session |
| `storeId` | UUID | yes | Store where scan is happening |
| `readerId` | UUID | no | Registered reader ID |
| `deviceId` | string | no | Mobile device identifier |
| `reads` | array | yes | Non-empty list of EPC reads |
| `reads[].epc` | string | yes | Uppercase hex, 16–128 chars |
| `reads[].rssi` | decimal | no | Signal strength in dBm |
| `reads[].antennaPort` | int | no | Antenna port number |
| `reads[].readAt` | ISO instant | no | When the tag was read |

**Response `202 Accepted`**
```json
{
  "data": {
    "published": 2
  }
}
```

> **Rate limit:** 200 burst requests per IP (configured in Nginx). For high-volume deployments, batch reads into groups of 500–1000 EPCs per request.

---

## 10. RFID Dead Letter Queue

**Base path:** `/api/rfid/dlq` — Port 8088 (RFID processing service).  
**Roles:** `ADMIN` only.

---

### GET /api/rfid/dlq

List RFID read events that failed processing (decode error, unknown EPC, etc.).

```http
GET /api/rfid/dlq
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": [
    {
      "key": "s1s2s3s4-..._3034257BF400B71400000099",
      "event": {
        "epc": "3034257BF400B71400000099",
        "storeId": "a1b2c3d4-...",
        "rfidSessionId": "s1s2s3s4-..."
      },
      "errorMessage": "EPC not found in product registry",
      "failedAt": "2026-06-04T08:35:12Z",
      "partition": 2,
      "offset": 18450
    }
  ]
}
```

---

### POST /api/rfid/dlq/{key}/replay

Re-publish a failed event back to the processing pipeline.

```http
POST /api/rfid/dlq/s1s2s3s4-..._3034257BF400B71400000099/replay
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 11. Reporting

**Base path:** `/api/reporting` — Port 8089.  
**Note:** KPI data is aggregated nightly. Current-day data appears the following morning unless manually triggered.

---

### GET /api/reporting/kpi/daily

Get paginated daily KPI history for a store.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/reporting/kpi/daily?storeId=a1b2c3d4-...&page=0&size=30
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "content": [
      {
        "storeId": "a1b2c3d4-...",
        "reportDate": "2026-06-04",
        "inventoryAccuracyPct": 97.8,
        "sohSessionCount": 2,
        "refillTasksCreated": 5,
        "refillTasksCompleted": 4,
        "refillCompletionRatePct": 80.0,
        "avgRefillTimeMinutes": 34.5,
        "totalEpcReads": 28450,
        "uniqueSkusCounted": 312,
        "varianceItemCount": 14
      }
    ],
    "page": 0, "size": 30, "totalElements": 90, "totalPages": 3, "last": false
  }
}
```

---

### GET /api/reporting/kpi/range

Get KPI data for a specific date range.

**Roles:** `ADMIN`, `STORE_MANAGER`

```http
GET /api/reporting/kpi/range?storeId=a1b2c3d4-...&from=2026-05-01&to=2026-06-04
Authorization: Bearer <accessToken>
```

| Query param | Type | Required | Format |
|---|---|---|---|
| `storeId` | UUID | yes | — |
| `from` | date | yes | `YYYY-MM-DD` |
| `to` | date | yes | `YYYY-MM-DD` |

**Response `200 OK`** — List of `KpiDaily` objects (same shape as above, no pagination wrapper).

---

### POST /api/reporting/kpi/aggregate

Manually trigger KPI re-aggregation for a specific store and date. Use when data was missing or incorrect and needs recalculation.

**Roles:** `ADMIN`

```http
POST /api/reporting/kpi/aggregate?storeId=a1b2c3d4-...&date=2026-06-04
Authorization: Bearer <accessToken>
```

**Response `200 OK`** — `{ "data": null }`

---

## 12. ERP Integration

**Base path:** `/api/erp/admin` — Port 8090.  
**Roles:** `ADMIN` only — all endpoints.

---

### POST /api/erp/admin/sync/products

Pull full product master from ERP, transform, and publish to Kafka for ingestion. Runs asynchronously.

```http
POST /api/erp/admin/sync/products
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "syncId": "sync-uuid-...",
    "status": "STARTED",
    "fetched": 2450,
    "published": 2450,
    "failed": 0
  }
}
```

---

### POST /api/erp/admin/sync/inventory

Pull expected inventory quantities from ERP and update `inventory_state.quantity_expected`.

```http
POST /api/erp/admin/sync/inventory
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "syncId": "sync-uuid-...",
    "status": "STARTED",
    "fetched": 18500,
    "published": 18500
  }
}
```

---

### GET /api/erp/admin/sync/logs

List all sync audit log entries.

```http
GET /api/erp/admin/sync/logs?syncType=PRODUCT_INBOUND&page=0&size=20
Authorization: Bearer <accessToken>
```

| `syncType` value | Description |
|---|---|
| `PRODUCT_INBOUND` | ERP → StoreLense product sync |
| `INVENTORY_INBOUND` | ERP → StoreLense expected inventory sync |
| `SOH_OUTBOUND` | StoreLense → ERP SOH results push |

**Response `200 OK`** — Paginated list of `ErpSyncLog` entities with fields: `syncId`, `syncType`, `status`, `recordsProcessed`, `triggeredBy`, `startedAt`, `completedAt`, `errorMessage`.

---

### GET /api/erp/admin/sync/logs/latest

Get the most recent log entry for each sync type — useful for a quick health-check dashboard.

```http
GET /api/erp/admin/sync/logs/latest
Authorization: Bearer <accessToken>
```

**Response `200 OK`**
```json
{
  "data": {
    "productSync": {
      "syncType": "PRODUCT_INBOUND",
      "status": "SUCCESS",
      "recordsProcessed": 2450,
      "completedAt": "2026-06-04T02:15:00Z"
    },
    "inventorySync": {
      "syncType": "INVENTORY_INBOUND",
      "status": "SUCCESS",
      "recordsProcessed": 18500,
      "completedAt": "2026-06-04T02:45:00Z"
    },
    "sohPush": {
      "syncType": "SOH_OUTBOUND",
      "status": "SUCCESS",
      "recordsProcessed": 5124,
      "completedAt": "2026-06-04T10:02:00Z"
    }
  }
}
```

---

## 13. Common Patterns

### Authentication Header

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Pagination

All paginated endpoints accept `?page=0&size=20` and return:

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```

### Response Envelope

Every response is wrapped:

```json
{
  "success": true,
  "code": "OK",
  "message": "Operation successful",
  "data": { ... }
}
```

### Correlation ID (optional)

Pass `X-Correlation-Id: <uuid>` on any request to trace it through all microservice logs.

```http
X-Correlation-Id: 7f3a9b2c-1234-5678-abcd-ef0123456789
```

### Multi-tenancy

Non-admin users cannot access data outside their assigned store. Passing a different `storeId` returns `403 Forbidden`.

---

## 14. Error Codes

| HTTP Status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body failed validation |
| 401 | `UNAUTHORIZED` | Missing or invalid/expired token |
| 403 | `FORBIDDEN` | Valid token but insufficient role or wrong store |
| 404 | `NOT_FOUND` | Resource does not exist |
| 409 | `CONFLICT` | Unique constraint violation (e.g. duplicate SKU) |
| 422 | `BUSINESS_ERROR` | Business rule violation (e.g. completing an already-cancelled session) |
| 429 | `RATE_LIMITED` | Too many requests — Nginx rate limit hit |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

**Error response shape:**
```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "username: must not be blank; email: must be a valid email address",
  "data": null
}
```

---

## Endpoint Summary

| Method | Path | Roles | Service |
|---|---|---|---|
| POST | `/api/auth/login` | Public | auth |
| POST | `/api/auth/refresh` | Public | auth |
| POST | `/api/auth/logout` | Authenticated | auth |
| GET | `/api/auth/me` | Authenticated | auth |
| GET | `/api/users` | ADMIN, MANAGER | auth |
| GET | `/api/users/{id}` | ADMIN, MANAGER | auth |
| POST | `/api/users` | ADMIN | auth |
| PUT | `/api/users/{id}` | ADMIN | auth |
| DELETE | `/api/users/{id}` | ADMIN | auth |
| GET | `/api/stores` | Public | store |
| GET | `/api/stores/{id}` | Public | store |
| POST | `/api/stores` | ADMIN | store |
| PUT | `/api/stores/{id}` | ADMIN | store |
| DELETE | `/api/stores/{id}` | ADMIN | store |
| GET | `/api/stores/{id}/zones` | Public | store |
| POST | `/api/stores/{id}/zones` | ADMIN, MANAGER | store |
| PUT | `/api/stores/{id}/zones/{zoneId}` | ADMIN, MANAGER | store |
| GET | `/api/products` | Public | product |
| GET | `/api/products/{id}` | Public | product |
| GET | `/api/products/by-sku/{sku}` | Public | product |
| GET | `/api/products/epc/{epc}` | Public | product |
| POST | `/api/products` | ADMIN | product |
| PUT | `/api/products/{id}` | ADMIN | product |
| POST | `/api/products/{id}/epc` | ADMIN, MANAGER | product |
| GET | `/api/inventory/state` | ADMIN, MANAGER | inventory |
| GET | `/api/inventory/low-accuracy` | ADMIN, MANAGER | inventory |
| GET | `/api/inventory/epc-summary` | ADMIN, MANAGER | inventory |
| GET | `/api/soh/sessions` | ADMIN, MANAGER, ASSOCIATE | soh |
| GET | `/api/soh/sessions/{id}` | ADMIN, MANAGER, ASSOCIATE | soh |
| POST | `/api/soh/sessions` | ADMIN, MANAGER, ASSOCIATE | soh |
| POST | `/api/soh/sessions/{id}/complete` | ADMIN, MANAGER, ASSOCIATE | soh |
| POST | `/api/soh/sessions/{id}/cancel` | ADMIN, MANAGER, ASSOCIATE | soh |
| GET | `/api/refill/tasks` | ADMIN, MANAGER, REFILL | refill |
| GET | `/api/refill/tasks/{id}` | ADMIN, MANAGER, REFILL | refill |
| POST | `/api/refill/tasks` | ADMIN, MANAGER | refill |
| POST | `/api/refill/tasks/{id}/assign` | ADMIN, MANAGER | refill |
| PATCH | `/api/refill/tasks/{id}/items/{itemId}/fulfil` | ADMIN, MANAGER, REFILL | refill |
| POST | `/api/refill/tasks/{id}/cancel` | ADMIN, MANAGER | refill |
| POST | `/api/rfid/ingest/batch` | All roles | rfid-ingest |
| GET | `/api/rfid/dlq` | ADMIN | rfid-processing |
| POST | `/api/rfid/dlq/{key}/replay` | ADMIN | rfid-processing |
| GET | `/api/reporting/kpi/daily` | ADMIN, MANAGER | reporting |
| GET | `/api/reporting/kpi/range` | ADMIN, MANAGER | reporting |
| POST | `/api/reporting/kpi/aggregate` | ADMIN | reporting |
| POST | `/api/erp/admin/sync/products` | ADMIN | erp |
| POST | `/api/erp/admin/sync/inventory` | ADMIN | erp |
| GET | `/api/erp/admin/sync/logs` | ADMIN | erp |
| GET | `/api/erp/admin/sync/logs/latest` | ADMIN | erp |
