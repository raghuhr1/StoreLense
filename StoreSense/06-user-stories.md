# User Stories

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.1
**Date:** 2026-06-06

---

## Roles

- **ADMIN** — IT/Operations team managing the platform across all stores
- **STORE_MANAGER** — Store manager overseeing a single store's operations
- **STORE_ASSOCIATE** — Floor associate performing RFID count sessions
- **REFILL_ASSOCIATE** — Stockroom associate completing refill tasks

---

## Epic 1 — Authentication

### US-01 User Login
**As a** user of any role,
**I want to** log in with my username and password,
**So that** I can access the features for my role.

**Acceptance Criteria:**
- Valid credentials return a JWT access token (15 min) and refresh token (7 days)
- Invalid credentials return a clear error message
- Account is locked after multiple failed attempts
- Mobile app stores tokens encrypted in Room DB

---

### US-02 Session Continuity
**As a** user on any device,
**I want** my session to stay active for up to 7 days without re-entering my password,
**So that** I don't get interrupted during a work shift.

**Acceptance Criteria:**
- Access token silently refreshes using the refresh token before expiry
- Refresh token rotation: each use issues a new refresh token

---

### US-03 Logout
**As a** user,
**I want to** log out securely,
**So that** my session cannot be reused on a shared device.

**Acceptance Criteria:**
- Logout revokes the refresh token and blacklists the access token in Redis
- Subsequent API calls with the old token return 401

---

## Epic 2 — Store and Zone Management

### US-04 Create Store
**As an** ADMIN,
**I want to** add a new store to the platform,
**So that** the store can be onboarded for RFID operations.

**Acceptance Criteria:**
- Store is created with code, name, address, timezone, and ERP cross-reference
- Duplicate store codes are rejected with a clear error
- Created store appears in the store list immediately

---

### US-05 Configure Zones
**As an** ADMIN,
**I want to** define zones within a store (floor, backroom, fitting room, etc.),
**So that** SOH sessions and refill tasks can be scoped to specific areas.

**Acceptance Criteria:**
- Zone has code, name, type, and display order
- Zone codes must be unique within a store
- Zones can be reordered and deactivated

---

### US-06 Register RFID Reader
**As an** ADMIN,
**I want to** register an RFID reader device against a store and zone,
**So that** the platform can associate scan sessions with the correct location.

**Acceptance Criteria:**
- Reader has a code, type (fixed/handheld/bluetooth_sled), and optional zone assignment
- Reader last heartbeat timestamp is visible for hardware monitoring

---

## Epic 3 — Product and EPC Management

### US-07 Create Product
**As an** ADMIN,
**I want to** add a product with SKU, name, brand, and category,
**So that** EPC tags can be mapped to products and counted in SOH sessions.

**Acceptance Criteria:**
- Product SKU must be unique
- Product can have RFID enabled/disabled flag
- Product is visible in the web portal Products page immediately after creation

---

### US-08 Register EPC Tag
**As an** ADMIN,
**I want to** associate an EPC tag with a product,
**So that** when the scanner reads that tag during a count, it maps to the correct product.

**Acceptance Criteria:**
- EPC is stored as uppercase hex
- Duplicate EPCs are rejected
- EPC lookup by tag value returns the associated product within 500 ms (cached in Redis)

---

### US-09 Bulk EPC Import
**As an** ADMIN,
**I want to** import thousands of EPC registrations from a CSV or batch API,
**So that** store onboarding can be completed efficiently.

**Acceptance Criteria:**
- Bulk import via seed script accepts store+product+EPC mappings
- Failed rows are reported without stopping the import
- Existing EPCs are skipped (not duplicated)

---

## Epic 4 — Stock on Hand (SOH) Counting

### US-10 Start SOH Session
**As a** STORE_ASSOCIATE,
**I want to** start an SOH count session on my Zebra device,
**So that** I can scan all RFID tags in a zone or the full store.

**Acceptance Criteria:**
- Session can be scoped to a specific zone or the full store
- Session is created in `in_progress` state and visible to the Store Manager
- Only one active session per store/zone at a time

---

### US-11 Submit RFID Reads
**As a** STORE_ASSOCIATE,
**I want to** scan products with the RFID reader and have reads automatically uploaded,
**So that** counted quantities are recorded without manual entry.

**Acceptance Criteria:**
- Reads are batched and uploaded every few seconds via the ingest API
- Duplicate EPC reads within a session are deduplicated
- If Wi-Fi drops, reads buffer locally and sync when connection resumes

---

### US-12 Complete SOH Session
**As a** STORE_ASSOCIATE,
**I want to** complete a count session when I've finished scanning,
**So that** the system calculates the SOH result and generates the variance report.

**Acceptance Criteria:**
- Session transitions to `completed` state
- SOH result shows counted quantity per product vs ERP expected quantity
- Variance (+ or -) is calculated per product
- Result is available within 30 seconds of session completion

---

### US-13 View Variance Report
**As a** STORE_MANAGER,
**I want to** see which products have count variances after a session,
**So that** I can investigate and take corrective action.

**Acceptance Criteria:**
- Variance report lists each product with counted qty, expected qty, and variance %
- Products can be filtered by overcount/undercount
- Variance items can be flagged for investigation

---

### US-14 Session History
**As a** STORE_MANAGER,
**I want to** see a history of all SOH sessions for my store,
**So that** I can track accuracy trends over time.

**Acceptance Criteria:**
- Sessions listed with date, zone, session type, accuracy %, and status
- Clicking a session shows the full result and variance detail
- Date range filtering available

---

## Epic 5 — Refill Task Management

### US-15 Create Refill Task
**As a** STORE_MANAGER,
**I want to** create a refill task with a list of products and target quantities per zone,
**So that** the stockroom team knows exactly what needs to be replenished.

**Acceptance Criteria:**
- Task has priority (1–10), due date, and optional notes
- Each task item has product, zone, and requested quantity
- Task is visible to all Refill Associates for the store immediately

---

### US-16 Auto-Create Refill from SOH
**As a** STORE_MANAGER,
**I want** the system to automatically create refill tasks when a SOH session reveals understock variance,
**So that** I don't have to manually review every session.

**Acceptance Criteria:**
- When variance exceeds the configured threshold, a refill task is created with the affected products
- Task source is marked as `soh_trigger` with reference to the session ID

---

### US-17 Assign Refill Task
**As a** STORE_MANAGER,
**I want to** assign a pending refill task to a specific Refill Associate,
**So that** the right person is responsible for completing it.

**Acceptance Criteria:**
- Assignment sends a push notification to the associate's device
- Task status changes to `assigned`
- Only one active assignment per task

---

### US-18 Complete Refill Task
**As a** REFILL_ASSOCIATE,
**I want to** mark items as fulfilled with the actual quantity placed,
**So that** the manager can see what was restocked.

**Acceptance Criteria:**
- Each item can be fulfilled with a quantity (may differ from requested)
- Items can be skipped with a reason
- Task auto-completes when all items have a fulfil or skip status
- Works offline; syncs on reconnect

---

### US-19 Track Refill Progress
**As a** STORE_MANAGER,
**I want to** see real-time progress on active refill tasks,
**So that** I know what's been done and what's still pending.

**Acceptance Criteria:**
- Task board shows status: pending, assigned, in-progress, completed
- Each task shows fulfilled qty vs requested qty per item
- Completion triggers a WebSocket push to the manager's browser

---

## Epic 6 — Reporting and KPIs

### US-20 View Daily KPIs
**As a** STORE_MANAGER,
**I want to** see today's KPI summary for my store,
**So that** I can quickly assess operational health.

**Acceptance Criteria:**
- Dashboard shows: inventory accuracy %, SOH session count, refill completion rate, avg refill time
- Data reflects the most recent completed session and tasks for the day

---

### US-21 KPI Trend View
**As a** STORE_MANAGER,
**I want to** see my store's KPI history over a date range,
**So that** I can identify whether performance is improving or declining.

**Acceptance Criteria:**
- Date range picker allows selecting any 90-day window
- Chart shows daily accuracy % trend
- Table shows per-day breakdown of all KPI metrics

---

### US-22 Cross-Store KPI Summary
**As an** ADMIN,
**I want to** see KPIs aggregated across all stores,
**So that** I can identify which stores need attention.

**Acceptance Criteria:**
- Summary shows each store's latest accuracy %, refill rate, and session count
- Stores below accuracy threshold are highlighted
- Sortable by any KPI metric

---

## Epic 7 — ERP Integration

### US-23 Product Sync from ERP
**As an** ADMIN,
**I want** the platform to automatically pull product master data from the ERP,
**So that** the product catalog stays up to date without manual entry.

**Acceptance Criteria:**
- Sync runs automatically every 6 hours (configurable)
- New products are created; changed products are updated
- Sync audit log shows records fetched, published, and failed

---

### US-24 Manual ERP Sync Trigger
**As an** ADMIN,
**I want to** manually trigger a product or inventory sync from the web portal,
**So that** I can force an update outside the scheduled window.

**Acceptance Criteria:**
- Trigger buttons available on ERP Admin page
- Response shows sync result summary immediately
- Running sync cannot be triggered a second time until complete

---

### US-25 SOH Push to ERP
**As an** ADMIN,
**I want** actual SOH counts to be pushed back to the ERP after each session,
**So that** the ERP reflects real on-hand quantities.

**Acceptance Criteria:**
- Push happens automatically after session completion (when enabled)
- Push can be enabled/disabled per store config
- Sync log records outbound push results

---

## Epic 8 — Administration

### US-26 Manage Users
**As an** ADMIN,
**I want to** create, update, and deactivate user accounts,
**So that** only authorised personnel can access the platform.

**Acceptance Criteria:**
- User created with username, email, role(s), and store assignment
- Deactivated users cannot log in; their data is retained
- Password meets complexity requirements (uppercase, digit, special char)

---

### US-27 RFID Dead Letter Queue
**As an** ADMIN,
**I want to** view failed RFID read events and replay them,
**So that** temporary processing failures don't result in permanent data loss.

**Acceptance Criteria:**
- DLQ shows up to 1000 recent failed events with reason
- Individual events can be re-published to the processing topic
- Successfully replayed events are removed from the DLQ view
