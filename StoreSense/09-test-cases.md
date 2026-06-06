# Test Cases Document

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.0
**Date:** 2026-06-06

---

## 1. Test Scope and Strategy

| Test Level | Scope | Tool |
|---|---|---|
| Unit | Individual service methods, JWT logic, EPC decode | JUnit 5 + Mockito |
| Integration | Service → DB → Kafka round-trips | Spring Boot Test + Testcontainers |
| API | Full HTTP request/response through nginx | Postman / curl scripts |
| End-to-End | Web portal user flows | Manual + Playwright (future) |
| Mobile | Zebra app flows with MockRfidReader | Android Instrumentation Tests |
| Performance | RFID ingest throughput, API p95 | k6 / Gatling |
| Security | Auth bypass, injection, token abuse | Manual + OWASP ZAP |

---

## 2. Authentication Test Cases

### TC-AUTH-01: Successful Login
**Precondition:** Admin user exists (username: `admin`, password: `Admin@StoreLense1`)
**Steps:**
1. `POST /api/auth/login` with valid credentials
**Expected:** HTTP 200; `data.accessToken` is a valid JWT; `data.refreshToken` is non-null; `data.user.roles` contains `ADMIN`

---

### TC-AUTH-02: Invalid Password
**Steps:** `POST /api/auth/login` with correct username, wrong password
**Expected:** HTTP 401; `success: false`; no tokens returned

---

### TC-AUTH-03: Non-Existent User
**Steps:** `POST /api/auth/login` with username that doesn't exist
**Expected:** HTTP 401; generic message (no information leakage about whether user exists)

---

### TC-AUTH-04: Token Expiry
**Steps:**
1. Login and receive `accessToken`
2. Wait 15 minutes (or advance system clock)
3. Call `GET /api/auth/me` with the expired token
**Expected:** HTTP 401; `code: INVALID_TOKEN`

---

### TC-AUTH-05: Token Refresh
**Steps:**
1. Login and capture `refreshToken`
2. `POST /api/auth/refresh` with `{ "refreshToken": "..." }`
**Expected:** HTTP 200; new `accessToken` returned; new `refreshToken` returned (old one revoked)

---

### TC-AUTH-06: Logout and Token Blacklist
**Steps:**
1. Login; capture `accessToken`
2. `POST /api/auth/logout` with `Authorization: Bearer {accessToken}`
3. Call `GET /api/auth/me` with the same token
**Expected:** HTTP 200 on logout; HTTP 401 on step 3

---

### TC-AUTH-07: Role-Based Access — ADMIN-Only Endpoint
**Steps:**
1. Login as `STORE_MANAGER`
2. `GET /api/users` (ADMIN only)
**Expected:** HTTP 403; `code: FORBIDDEN`

---

### TC-AUTH-08: Store Isolation
**Steps:**
1. Login as `STORE_MANAGER` for store A
2. `GET /api/soh/sessions?storeId={storeB_id}`
**Expected:** HTTP 403 or empty result (no data from store B visible)

---

### TC-AUTH-09: Account Lockout
**Steps:** Submit 5+ failed login attempts for the same username
**Expected:** Account locked; subsequent attempts return 401 with lockout message until unlock time

---

## 3. Store Management Test Cases

### TC-STORE-01: Create Store
**Precondition:** Authenticated as ADMIN
**Steps:** `POST /api/stores` with valid store payload including unique `storeCode`
**Expected:** HTTP 201; `data.id` is non-null UUID; store retrievable by `GET /api/stores/{id}`

---

### TC-STORE-02: Duplicate Store Code
**Steps:** `POST /api/stores` with a `storeCode` that already exists
**Expected:** HTTP 409; `code: DUPLICATE`

---

### TC-STORE-03: Create Zone
**Steps:**
1. Create store → capture `storeId`
2. `POST /api/stores/{storeId}/zones` with valid zone payload
**Expected:** HTTP 201; zone returned with `id` and correct `storeId`

---

### TC-STORE-04: Duplicate Zone Code in Same Store
**Steps:** Create two zones with the same `zoneCode` in the same store
**Expected:** Second creation returns HTTP 409

---

### TC-STORE-05: Zone Code Unique Across Stores
**Steps:** Create zone with `zoneCode: "FLOOR-1"` in store A, then same code in store B
**Expected:** Both succeed — zone codes are unique per store, not globally

---

### TC-STORE-06: Deactivate Store
**Steps:**
1. `DELETE /api/stores/{id}` (soft delete)
2. `GET /api/stores` (list active stores)
**Expected:** Store is not present in the active list; record still exists in DB with `is_active = false`

---

### TC-STORE-07: Register RFID Reader
**Steps:** `POST /api/stores/{storeId}/readers` with `readerCode`, `readerType: "handheld"`
**Expected:** HTTP 201; reader appears in `GET /api/stores/{storeId}/readers`

---

## 4. Product and EPC Test Cases

### TC-PROD-01: Create Product
**Precondition:** ADMIN auth
**Steps:** `POST /api/products` with unique `sku`, `name`, `unitOfMeasure: "EACH"`, `rfidEnabled: true`
**Expected:** HTTP 201; product returned with non-null `id`

---

### TC-PROD-02: Duplicate SKU
**Steps:** Create product with `sku` that already exists
**Expected:** HTTP 409

---

### TC-PROD-03: Register EPC Tag
**Steps:**
1. Create product → capture `productId`
2. `POST /api/products/{productId}/epc` with `{ "epc": "3034257BF400B71400000064" }`
**Expected:** HTTP 201; EPC registered

---

### TC-PROD-04: EPC Lookup by Tag Value
**Steps:**
1. Register EPC as in TC-PROD-03
2. `GET /api/products/epc/3034257BF400B71400000064`
**Expected:** HTTP 200; `data.productId` matches the product created in step 1

---

### TC-PROD-05: EPC Lookup Cache
**Steps:**
1. Register EPC
2. Call `GET /api/products/epc/{epc}` twice
3. Check Redis: `EXISTS product:epc:{epc}` should be `1`
**Expected:** Second call served from cache (response time significantly lower); Redis key exists with 30-min TTL

---

### TC-PROD-06: Duplicate EPC Registration
**Steps:** Register the same EPC twice (same or different product)
**Expected:** HTTP 409 on the second registration

---

### TC-PROD-07: Get Product by SKU
**Steps:** `GET /api/products/by-sku/{sku}`
**Expected:** HTTP 200; correct product returned

---

## 5. SOH Session Test Cases

### TC-SOH-01: Start SOH Session
**Steps:**
1. Authenticate as STORE_ASSOCIATE
2. `POST /api/soh/sessions` with `{ storeId, zoneId, sessionType: "manual" }`
**Expected:** HTTP 201; session `status: "in_progress"`; session visible to STORE_MANAGER

---

### TC-SOH-02: Submit RFID Reads
**Steps:**
1. Start session → capture `sessionId`
2. `POST /api/rfid/ingest/batch` with 5 EPC reads for known products
**Expected:** HTTP 200; `accepted: 5`; reads processed asynchronously by rfid-processing-service

---

### TC-SOH-03: EPC Deduplication within Session
**Steps:**
1. Start session
2. Submit batch with the same EPC 3 times
**Expected:** `accepted: 3` at ingest level; after processing, only 1 unique EPC counted for that product

---

### TC-SOH-04: Complete Session and Verify Result
**Steps:**
1. Start session
2. Submit batch with 10 unique EPCs (for 3 products with known expected quantities)
3. `POST /api/soh/sessions/{id}/complete`
4. `GET /api/soh/sessions/{id}`
**Expected:** Session `status: "completed"`; `data.result.totalProductsCounted = 3`; variance correctly calculated

---

### TC-SOH-05: Session Latency
**Steps:** Record time from `POST .../complete` to when `GET .../sessions/{id}` returns `status: "completed"` with result
**Expected:** Result available within 30 seconds

---

### TC-SOH-06: Cancel Session
**Steps:**
1. Start session
2. `POST /api/soh/sessions/{id}/cancel` with `{ reason: "Test cancellation" }`
**Expected:** HTTP 200; session `status: "cancelled"`; cannot complete after cancellation

---

### TC-SOH-07: Duplicate Active Session
**Steps:** Start two sessions for the same store + zone simultaneously
**Expected:** Second `POST` returns HTTP 409

---

### TC-SOH-08: Store Manager Cannot See Other Store Sessions
**Steps:** STORE_MANAGER for store A queries sessions for store B
**Expected:** Empty result or HTTP 403

---

## 6. Refill Task Test Cases

### TC-REFILL-01: Create Refill Task
**Precondition:** STORE_MANAGER auth
**Steps:** `POST /api/refill/tasks` with 2 product items
**Expected:** HTTP 201; task `status: "pending"`; items have `status: "pending"`

---

### TC-REFILL-02: Assign Task to Associate
**Steps:**
1. Create task → capture `taskId`
2. `POST /api/refill/tasks/{taskId}/assign` with `{ assigneeId: refillAssociateUserId }`
**Expected:** Task `status: "assigned"`; assignment record created

---

### TC-REFILL-03: Fulfil Task Item
**Steps:**
1. Assign task
2. `PATCH /api/refill/tasks/{taskId}/items/{itemId}/fulfil` with `{ fulfilledQuantity: 8 }`
**Expected:** Item `status: "partial"` (if < requested) or `"fulfilled"` (if ≥ requested)

---

### TC-REFILL-04: Task Auto-Complete
**Steps:**
1. Create task with 2 items
2. Fulfil both items
**Expected:** Task `status: "completed"` after both items are fulfilled

---

### TC-REFILL-05: Skip Task Item
**Steps:** Fulfil with `fulfilledQuantity: 0` (or skip endpoint)
**Expected:** Item `status: "skipped"`; task can still complete if other items fulfilled

---

### TC-REFILL-06: Cancel Task
**Steps:** `POST /api/refill/tasks/{id}/cancel` with reason
**Expected:** Task `status: "cancelled"`; cannot be assigned or fulfilled after

---

### TC-REFILL-07: Associate Cannot See Other Store Tasks
**Steps:** REFILL_ASSOCIATE for store A queries tasks for store B
**Expected:** Empty result or HTTP 403

---

## 7. Inventory Test Cases

### TC-INV-01: Get Inventory State
**Precondition:** At least one completed SOH session with results
**Steps:** `GET /api/inventory/state?storeId={id}`
**Expected:** HTTP 200; list of inventory records with `quantityOnHand`, `quantityExpected`, `accuracyPct`

---

### TC-INV-02: Low Accuracy Query
**Steps:** `GET /api/inventory/low-accuracy?storeId={id}&threshold=98`
**Expected:** Products with `accuracyPct < 98` returned

---

### TC-INV-03: EPC Summary
**Steps:** `GET /api/inventory/epc-summary?storeId={id}`
**Expected:** Counts by status: `in_store`, `sold`, `missing`, etc.

---

### TC-INV-04: Update Expected Quantity
**Steps:** `POST /api/inventory/expected` with `{ storeId, productId, quantityExpected: 20 }`
**Expected:** HTTP 200; subsequent inventory state for that product shows `quantityExpected: 20`

---

## 8. Reporting Test Cases

### TC-RPT-01: Daily KPI After Session
**Precondition:** At least one completed SOH session
**Steps:** `GET /api/reporting/kpi/daily?storeId={id}`
**Expected:** Non-empty response with `kpiDate`, `inventoryAccuracyPct`, `sohSessionsCount`

---

### TC-RPT-02: KPI Date Range
**Steps:** `GET /api/reporting/kpi/range?storeId={id}&from=2026-06-01&to=2026-06-06`
**Expected:** Array of daily KPI records within the date range

---

### TC-RPT-03: Manual KPI Aggregation Trigger
**Steps:** `POST /api/reporting/kpi/aggregate` with `{ storeId, kpiDate: "2026-06-06" }` as ADMIN
**Expected:** HTTP 200; KPI record created or updated for that date

---

## 9. ERP Integration Test Cases

### TC-ERP-01: Manual Product Sync Trigger
**Precondition:** ADMIN auth; ERP_BASE_URL pointing to mock ERP server
**Steps:** `POST /api/erp/admin/sync/products`
**Expected:** HTTP 200; `status: "completed"` or `"partial"`; sync log entry created

---

### TC-ERP-02: Sync Logs
**Steps:** `GET /api/erp/admin/sync/logs`
**Expected:** Non-empty list after a sync run; each entry has `syncType`, `status`, `recordsFetched`

---

### TC-ERP-03: Sync Does Not Run Twice Concurrently
**Steps:** Trigger two simultaneous `POST /api/erp/admin/sync/products` requests
**Expected:** Second call returns `SYNC_RUNNING` error message; only one sync completes

---

### TC-ERP-04: Latest Sync Record
**Steps:** `GET /api/erp/admin/sync/logs/latest`
**Expected:** One entry each for `PRODUCT_INBOUND`, `INVENTORY_INBOUND`, `SOH_OUTBOUND` (or null if never run)

---

## 10. RFID Processing Dead Letter Queue Test Cases

### TC-DLQ-01: List Failed Events
**Steps:** `GET /api/rfid/dlq` as ADMIN
**Expected:** HTTP 200; list (empty or populated) of failed events with `key`, `reason`, `payload`

---

### TC-DLQ-02: Replay Failed Event
**Steps:**
1. Identify a failed event key from TC-DLQ-01
2. `POST /api/rfid/dlq/{key}/replay`
**Expected:** HTTP 200; event re-published to `rfid.reads.raw`; event disappears from DLQ on next `GET`

---

## 11. Performance Test Cases

### TC-PERF-01: RFID Ingest Throughput
**Tool:** k6 or Gatling
**Scenario:** 50 virtual users each submitting 20 EPCs/batch, continuously for 5 minutes
**Expected:** Sustained throughput ≥ 500 reads/sec per simulated store; p95 latency < 500 ms

---

### TC-PERF-02: Concurrent SOH Sessions
**Scenario:** 10 simultaneous SOH sessions from different stores each submitting 100-EPC batches
**Expected:** No errors; all results generated within 30 seconds of completion

---

### TC-PERF-03: API Response Time Under Load
**Scenario:** 100 concurrent users making `GET /api/products`, `GET /api/stores`, `GET /api/soh/sessions` for 2 minutes
**Expected:** p95 response time < 500 ms; p99 < 1000 ms; 0% error rate

---

### TC-PERF-04: Kafka Consumer Lag
**Scenario:** Burst 50,000 RFID reads in 30 seconds
**Expected:** Consumer lag returns to 0 within 60 seconds; no messages lost

---

## 12. Security Test Cases

### TC-SEC-01: SQL Injection in Search
**Steps:** `GET /api/products?search='; DROP TABLE products;--`
**Expected:** HTTP 200 with empty/filtered result; no SQL execution; no 500 error

---

### TC-SEC-02: JWT Algorithm Confusion (alg:none)
**Steps:** Craft a token with `"alg": "none"` and no signature
**Expected:** HTTP 401; token rejected

---

### TC-SEC-03: JWT Secret Brute Force Resistance
**Steps:** Attempt to forge a token with guessed secrets
**Expected:** All forged tokens rejected (validated by secret length ≥ 32 chars)

---

### TC-SEC-04: Cross-Store Data Access via ID Manipulation
**Steps:**
1. Login as STORE_MANAGER for store A
2. Attempt `GET /api/soh/sessions/{session_id_from_store_B}`
**Expected:** HTTP 403 or 404 — session from other store not returned

---

### TC-SEC-05: Actuator Endpoint Blocked
**Steps:** `GET /api/actuator/health` via nginx gateway
**Expected:** HTTP 403 (actuator routes are blocked at nginx level)

---

### TC-SEC-06: Rate Limit Enforcement
**Steps:** Send 100 rapid requests from the same IP to `/api/stores`
**Expected:** Requests beyond the rate limit (60/min) return HTTP 429

---

## 13. Mobile App Test Cases

### TC-MOB-01: Login on Zebra Device
**Steps:** Open app; enter valid credentials
**Expected:** JWT stored encrypted in Room; user lands on task/session home screen

---

### TC-MOB-02: SOH Session with MockRfidReader
**Steps:**
1. Enable MockRfidReader in build config
2. Start SOH session
3. Trigger mock scan (pre-configured EPC list emitted)
4. Complete session
**Expected:** Reads batched and uploaded; session completes; result visible

---

### TC-MOB-03: Offline Buffering
**Steps:**
1. Start SOH session
2. Disable Wi-Fi on device
3. Scan products — reads should buffer locally
4. Re-enable Wi-Fi
**Expected:** Buffered reads sync via WorkManager; no reads lost

---

### TC-MOB-04: Refill Task Assignment Notification
**Steps:** Assign a task in web portal to the test associate
**Expected:** Associate's device receives push notification or task appears on next sync pull

---

### TC-MOB-05: Task Completion Offline
**Steps:**
1. Receive assigned task
2. Go offline
3. Mark item as fulfilled with quantity
4. Reconnect
**Expected:** Fulfilment synced to server; task status updated

---

## 14. End-to-End Happy Path Test

### TC-E2E-01: Full Operational Cycle

**Scenario:** One complete day's operations at P037

**Steps:**
1. Login as ADMIN → create store P037 → create 5 zones → create 10 products → register 50 EPCs
2. Create `mgr_p037` (STORE_MANAGER), `asc_p037` (STORE_ASSOCIATE), `rfl_p037` (REFILL_ASSOCIATE)
3. Login as `asc_p037` → start SOH session for Floor zone → submit 50 EPC reads → complete
4. Login as `mgr_p037` → view session result → verify variance report
5. Create refill task for 3 products with understock variance → assign to `rfl_p037`
6. Login as `rfl_p037` → view assigned task → fulfil all items
7. Login as `mgr_p037` → verify task completed → view KPI dashboard
8. Login as ADMIN → trigger ERP product sync → view sync log → view daily KPI for P037

**Expected:** All steps succeed; KPI shows 1 SOH session, 1 refill task, refill completion rate 100%
