# StoreLense — Features Document

> **Product:** StoreLense RFID Store Operations Platform  
> **Version:** 1.0  
> **Target Users:** Retail chains operating 1–400 stores with RFID-enabled inventory  

---

## Table of Contents

1. [Product Overview](#1-product-overview)
2. [Core Feature Modules](#2-core-feature-modules)
3. [Feature Details](#3-feature-details)
   - 3.1 [Authentication & Access Control](#31-authentication--access-control)
   - 3.2 [Real-Time RFID Scanning Pipeline](#32-real-time-rfid-scanning-pipeline)
   - 3.3 [Stock on Hand (SOH) Cycle Counting](#33-stock-on-hand-soh-cycle-counting)
   - 3.4 [Inventory Accuracy Management](#34-inventory-accuracy-management)
   - 3.5 [Refill Task Management](#35-refill-task-management)
   - 3.6 [Product & EPC Registry](#36-product--epc-registry)
   - 3.7 [Store & Zone Management](#37-store--zone-management)
   - 3.8 [KPI Reporting & Analytics](#38-kpi-reporting--analytics)
   - 3.9 [ERP Bidirectional Integration](#39-erp-bidirectional-integration)
   - 3.10 [Real-Time Notifications](#310-real-time-notifications)
   - 3.11 [RFID Device Management](#311-rfid-device-management)
   - 3.12 [User Management](#312-user-management)
   - 3.13 [Zebra Mobile App (Android)](#313-zebra-mobile-app-android)
   - 3.14 [Audit & Compliance](#314-audit--compliance)
4. [Platform Capabilities](#4-platform-capabilities)
5. [Performance Specifications](#5-performance-specifications)
6. [Integration Capabilities](#6-integration-capabilities)
7. [Security Features](#7-security-features)
8. [Feature Matrix by Role](#8-feature-matrix-by-role)
9. [Deployment Options](#9-deployment-options)

---

## 1. Product Overview

StoreLense is a cloud-native, microservices-based RFID store operations platform designed to replace legacy inventory management tools with real-time, automated stock intelligence.

### Business Problem Solved

| Challenge | StoreLense Solution |
|---|---|
| Manual stock counts take 8–16 hours | RFID scanning completes in 30–90 minutes |
| Inventory accuracy below 80% industry average | Drives stores to 95–99% accuracy |
| Refill tasks created manually → stock-outs | Automatic task creation from SOH variances |
| ERP expected quantities out of sync | Nightly + on-demand bidirectional ERP sync |
| No real-time visibility for managers | Live WebSocket dashboard, no page refresh |
| Paper-based or spreadsheet refill tracking | Digital task management with mobile app |
| Cannot handle 400-store scale | Built for 400 concurrent stores, 50,000 RFID reads/sec |

### Key Differentiators

- **End-to-end RFID pipeline** from Zebra scanner to accuracy result in < 30 seconds
- **Offline-first mobile app** — scans continue without WiFi; syncs on reconnect
- **Microservices architecture** — each service scales independently for RFID burst loads
- **Multi-tenant** — 400 stores on a single platform; complete data isolation per store
- **ERP agnostic** — pluggable integration layer supports any ERP via REST API

---

## 2. Core Feature Modules

| Module | Description | Users |
|---|---|---|
| **RFID Scanning Pipeline** | Real-time EPC ingest, decode, deduplication, and SOH calculation | STORE_ASSOCIATE, RFID hardware |
| **SOH Cycle Counting** | Schedule and execute RFID-based stock counts with accuracy results | STORE_ASSOCIATE, STORE_MANAGER |
| **Inventory Accuracy** | Per-product, per-zone accuracy tracking vs ERP expected quantities | STORE_MANAGER, ADMIN |
| **Refill Task Management** | Create, assign, track, and complete stock replenishment tasks | STORE_MANAGER, REFILL_ASSOCIATE |
| **Product & EPC Registry** | Product master data with RFID tag association and Redis-cached EPC lookup | ADMIN, STORE_MANAGER |
| **Store & Zone Configuration** | Multi-store, multi-zone structure with RFID reader mapping | ADMIN |
| **KPI Reporting** | Daily KPI aggregation with trend charts and drill-down tables | STORE_MANAGER, ADMIN |
| **ERP Integration** | Bidirectional sync: product master + expected inventory in; SOH results out | ADMIN |
| **Real-Time Notifications** | WebSocket push alerts for session events, task assignments, reader status | All roles |
| **User Management** | Role-based user accounts with per-store access control | ADMIN |
| **Zebra Mobile App** | Offline-first Android app for RFID scanning and task fulfilment | STORE_ASSOCIATE, REFILL_ASSOCIATE |
| **Audit & Compliance** | Immutable audit log of all operations with monthly partitioning | ADMIN |

---

## 3. Feature Details

---

### 3.1 Authentication & Access Control

#### JWT-Based Authentication

- Username + password login returns an **access token** (15-minute expiry) and a **refresh token** (7-day expiry)
- Tokens are signed with HMAC-SHA256 using a configurable secret (minimum 32 characters)
- Access tokens carry user ID, role, store ID, and expiry as JWT claims
- Silent background token refresh — users are not interrupted during active sessions

#### Token Security

- **Blacklisting:** Logged-out access tokens are stored in Redis and rejected on every request
- **Refresh token rotation:** Each use of a refresh token issues a new refresh token and invalidates the old one — prevents replay attacks
- **In-memory storage only:** Frontend stores tokens in JavaScript memory, never in `localStorage` or `sessionStorage` — prevents XSS token theft

#### Role-Based Access Control (RBAC)

Four roles with progressively broader access:

| Role | Scope | Key Permissions |
|---|---|---|
| **ADMIN** | All stores | Full system access — users, stores, ERP, reporting, all data |
| **STORE_MANAGER** | Assigned store | SOH sessions, refill tasks, inventory, reports, devices |
| **STORE_ASSOCIATE** | Assigned store | Start and execute SOH count sessions; submit RFID batches |
| **REFILL_ASSOCIATE** | Assigned store | View and fulfil assigned refill tasks |

#### Multi-Tenancy Enforcement

- Every API request is scoped to the caller's `store_id` (extracted from JWT)
- Non-admin users physically cannot retrieve data from other stores — enforced at the database query level, not just the UI
- ADMIN users have `store_id = null` and can access all stores

#### Account Security

- Passwords hashed with bcrypt (cost factor 12)
- Account lockout after consecutive failed login attempts
- Password minimum length enforced on creation and update

---

### 3.2 Real-Time RFID Scanning Pipeline

The RFID pipeline is the performance-critical core of the platform. It is designed to process 50,000 RFID reads per second across all stores.

#### Ingest Layer

- **Endpoint:** `POST /api/rfid/ingest/batch` — accepts batches of EPC reads from Zebra devices
- **Input:** Session ID, store ID, reader ID (optional), device ID, list of EPCs with RSSI, antenna port, and timestamp
- **Validation:** EPC format checked (hex, 16–128 characters); session ID and store ID required
- **Response:** HTTP 202 Accepted immediately — the response does not wait for processing
- **Kafka publication:** Each validated read published to `rfid.reads.raw` topic instantly
- **Rate limit:** 200 burst reads/minute per IP (Nginx), with configurable burst for high-density stores

#### Processing Layer (Kafka Consumer)

- **EPC decoding:** GS1 SGTIN-96 and SGTIN-198 standard decoding — extracts company prefix, item reference, and serial number
- **EPC-to-SKU resolution:** Cached in Redis — lookup in < 1ms for known tags; falls back to database on cache miss
- **Session-level deduplication:** Uses Redis SET per session ID — identical EPCs in the same session counted once regardless of how many times the physical tag was read
- **Failure handling:** Processing failures go to an in-memory Dead Letter Queue (DLQ) accessible via API for investigation and replay
- **Horizontal scaling:** The rfid-processing-service can be scaled to multiple replicas: `docker compose up --scale rfid-processing-service=3`

#### SOH Update Layer

- Kafka consumer in soh-service receives processed reads
- Updates `inventory.inventory_state.quantity_on_hand` for each product per zone
- Calculates accuracy: `(quantity_on_hand / quantity_expected) × 100`
- Publishes session update events for real-time dashboard refresh

#### End-to-End Latency

| Stage | Target |
|---|---|
| Ingest to Kafka publish | < 50 ms |
| Kafka to processing complete | < 5 s |
| SOH state update | < 10 s |
| Session result to dashboard | < 30 s total |

---

### 3.3 Stock on Hand (SOH) Cycle Counting

#### Session Types

| Type | Description | Typical Duration |
|---|---|---|
| **Full Store** | Counts every RFID-tagged item in the entire store | 45–90 minutes |
| **Spot Check** | Counts one zone or product group | 10–20 minutes |
| **Manual** | Ad-hoc count with no predefined scope | Variable |

#### Session Lifecycle

```
created → in_progress → completed
                    ↘ cancelled
                    ↘ failed
```

- `created` — session record created; no reads yet
- `in_progress` — first RFID reads received; EPC counts updating in real time
- `completed` — manually marked done; accuracy result generated
- `cancelled` — manually cancelled; no result generated; reads discarded
- `failed` — processing error occurred

#### Accuracy Result

On session completion, the system automatically generates:
- **Total products counted** — distinct SKUs with at least one read
- **Total units counted** — sum of unique EPC reads per product
- **Total units expected** — sum of ERP expected quantities for counted products
- **Accuracy %** — (units counted / units expected) × 100
- **Variance count** — products where counted ≠ expected
- **Overcount items** — products with more found than expected
- **Undercount items** — products with fewer found than expected

#### Variance Tracking

- Detailed `soh_variance` records created for every product with a discrepancy
- Variance records include product ID, zone ID, expected qty, counted qty, and difference
- Variance list is available in the session detail screen and drives automatic refill task creation

#### Zone-Scoped Sessions

- Sessions can be scoped to a specific zone (floor, backroom, fitting room, etc.)
- Zone-scoped sessions only include EPCs read in the selected zone
- Enables targeted recounts without affecting the full-store accuracy figure

---

### 3.4 Inventory Accuracy Management

#### Real-Time Inventory State

Every RFID-enabled product in every store has a live inventory record:
- **quantity_on_hand** — last RFID-scanned quantity
- **quantity_expected** — ERP-sourced expected quantity
- **accuracy_pct** — calculated ratio (refreshed after every SOH session)
- **last_counted_at** — timestamp of the most recent session that included this product

#### Low Accuracy Alerts

- Configurable accuracy threshold (default 95%)
- Products below threshold highlighted in red on the Inventory screen
- Low-accuracy products automatically trigger refill task creation (`source:"soh_trigger"`)
- Admin-configurable per-store threshold

#### EPC Status Tracking

Individual RFID tags maintain a lifecycle status:

| Status | Trigger |
|---|---|
| `in_store` | Tag read in the most recent session |
| `missing` | Tag not seen in the most recent session but previously registered |
| `sold` | Tag decommissioned via point-of-sale |
| `damaged` | Manually marked as damaged/written off |
| `transferred` | Item sent to another store via transfer |

#### Zone-Level Inventory Breakdown

- Inventory state is tracked per product per zone per store
- Zone breakdown available in product inventory detail view
- Enables identification of where missing stock might physically be

---

### 3.5 Refill Task Management

#### Task Creation Sources

| Source | Trigger |
|---|---|
| **manual** | Store Manager creates directly in web UI |
| **soh_trigger** | System automatically creates when SOH session finds accuracy < threshold |
| **scheduled** | Nightly scheduled job based on configured rules |
| **erp** | Pushed from ERP system via integration service |

#### Task Types

| Type | Use Case | Typical Priority |
|---|---|---|
| `replenishment` | Scheduled restocking of sales floor from backroom | 5–8 |
| `urgency` | Critical item needed immediately | 1–3 |
| `cycle_count` | Recount and refill triggered by variance | 2–5 |

#### Priority System

- Tasks prioritised 1 (highest urgency) to 10 (lowest)
- Task list sorted by priority automatically in web UI and mobile app
- Priority set manually on creation or by system rules for auto-created tasks

#### Task Lifecycle

```
pending → assigned → in_progress → completed
                              ↘ cancelled
```

- **pending** — created but not yet assigned to any associate
- **assigned** — assigned to a specific REFILL_ASSOCIATE; they receive a push notification
- **in_progress** — associate has started working on it (first item fulfilled)
- **completed** — all line items are in a terminal state (fulfilled / partial / skipped)
- **cancelled** — cancelled by manager with optional reason; reason preserved in audit log

#### Line Item Fulfilment

Each task contains one or more line items:
- **Product** — what needs to be moved
- **Zone** — where to place the stock (target zone)
- **Requested quantity** — how many units are needed
- **Fulfilled quantity** — how many were actually brought (recorded by associate)

Item fulfilment outcomes:
- `fulfilled` — actual qty ≥ requested qty
- `partial` — actual qty > 0 but < requested
- `skipped` — associate could not fulfil (reason logged)

When all items reach a terminal state, the task automatically transitions to `completed` — no manual step required.

#### Assignment & Notification

- Managers assign tasks to specific Refill Associates via dropdown
- Assignment triggers an instant WebSocket push notification to the associate's device
- Assignment is tracked with timestamp for performance analytics

#### Auto-Assignment

- Per-store configurable setting to auto-assign refill tasks to available associates
- When enabled, system assigns to the associate with the fewest open tasks

---

### 3.6 Product & EPC Registry

#### Product Master Data

Each product record contains:
- **SKU** — unique stock-keeping unit code
- **Name, Description, Brand** — display information
- **Category** — hierarchical (APPAREL → Tops → T-Shirts)
- **ERP Product Code** — mapping key for ERP synchronisation
- **Unit of Measure** — EACH, PAIR, BOX, etc.
- **Weight (grams)** — for logistics
- **RFID Enabled** — flag controlling whether EPC tracking applies
- **Active/Inactive** — soft-delete support; historical data preserved

#### EPC Tag Association

- Each physical RFID tag has a unique EPC (Electronic Product Code)
- EPCs are linked to products in the `epc_tags` table
- Association created via `POST /api/products/{id}/epc?epc=<hex>`
- Typically done during tag provisioning or ERP sync

#### Redis-Cached EPC Lookup

- EPC-to-product lookups cached in Redis for sub-millisecond resolution
- Critical for RFID processing pipeline throughput (50,000 reads/sec)
- Cache is automatically populated on first lookup and invalidated on product deactivation
- `GET /api/products/epc/{epc}` returns `fromCache:true/false` indicator

#### Product Search

- Full-text search across name, SKU, and brand: `GET /api/products?search=denim`
- Paginated results (default 50 per page)
- Public endpoint — no authentication required for read-only product lookup

#### Barcode Support

- Products can have multiple barcode associations (EAN-13, UPC-A, GS1-128)
- Barcodes stored in `products.barcodes` table
- Enables mixed RFID + barcode workflows

---

### 3.7 Store & Zone Management

#### Store Configuration

Each store record contains:
- **Store Code** — unique short code (e.g. `SYD001`)
- **Full address** — line 1, line 2, city, state, postal code, country (ISO 2-char)
- **Timezone** — IANA format (e.g. `Australia/Sydney`) — used for scheduling and KPI date boundaries
- **ERP Store Code** — mapping key for ERP sync
- **Active/Inactive** — chain-wide store status

#### Zone Types

| Zone Type | Physical Location |
|---|---|
| `floor` | Main retail sales floor |
| `backroom` | Storage and receiving area |
| `fitting_room` | Changing rooms |
| `stockroom` | Central stock holding area |
| `display` | Window displays and feature fixtures |

#### Zone Configuration

- Unlimited zones per store
- Display order configurable for UI presentation
- Zones linked to RFID readers for automatic zone attribution of reads
- SOH sessions can be scoped to a single zone

#### Per-Store RFID Settings

Configurable per store:
- **Tx Power** — RFID transmission power (dBm)
- **Gen2 Session** — EPC Gen2 session mode (0–3)
- **Refill auto-assign** — toggle automatic task assignment
- **Accuracy threshold** — custom threshold for low-accuracy alerts (default 95%)

---

### 3.8 KPI Reporting & Analytics

#### Daily KPI Aggregation

A nightly background job aggregates data from SOH, Refill, and RFID schemas into a single daily KPI record per store:

| Metric | Description |
|---|---|
| `inventory_accuracy_pct` | Average accuracy across all counted products |
| `soh_session_count` | Number of count sessions run |
| `refill_tasks_created` | New refill tasks created |
| `refill_tasks_completed` | Tasks completed |
| `refill_completion_rate_pct` | Completed / created × 100 |
| `avg_refill_time_minutes` | Average time from task creation to completion |
| `total_epc_reads` | Total RFID tag reads processed |
| `unique_skus_counted` | Distinct products counted |
| `variance_item_count` | Products with a quantity mismatch |

#### Report Queries

- **Daily history:** Paginated list of KPI records (default 30 per page)
- **Date range:** List of records between `from` and `to` dates — for custom date ranges
- **Manual trigger:** Admin can force KPI recalculation for any store+date combination

#### Dashboard Visualisations

- **Inventory Accuracy Trend** — 7-day line chart with 95% target reference line
- **Refill Completion Rate** — 7-day bar chart
- **RFID Read Volume** — 7-day bar chart (thousands)
- **KPI Detail Table** — all metrics per day in tabular format

#### Time Windows

Preset windows available in the Reports screen: 7 days, 30 days, 90 days. Custom date range supported via API.

#### Data Freshness

- KPI data is aggregated nightly (runs after midnight local store time)
- Current day's data available the following morning
- Admin can trigger on-demand re-aggregation if data is incorrect or missing

---

### 3.9 ERP Bidirectional Integration

#### Architecture

The ERP integration service acts as an **adapter layer** — decoupling the ERP-specific API format from StoreLense's internal models. This makes StoreLense ERP-agnostic; different ERPs can be supported by implementing the integration service for each.

#### Inbound Syncs (ERP → StoreLense)

**Product Master Sync**
- Pulls full product catalogue from ERP
- Transforms ERP product format to StoreLense schema
- Creates new products, updates existing ones (upsert by ERP product code)
- Publishes to Kafka topic `erp.product.sync` → consumed by product-service
- Records: product name, SKU, category, brand, ERP code, rfid-enabled flag

**Expected Inventory Sync**
- Pulls per-store, per-product expected quantity from ERP
- Updates `inventory.inventory_state.quantity_expected` for all stores
- Used as the denominator in accuracy calculations
- Publishes to Kafka `erp.inventory.expected` → consumed by inventory-service

#### Outbound Syncs (StoreLense → ERP)

**SOH Results Push**
- Triggered automatically after each SOH session completes (when `ERP_PUSH_SOH_ENABLED=true`)
- Sends actual on-hand quantities per product per store to ERP
- Allows ERP to update its inventory records with physical scan data
- Async — does not block the session completion response

#### Scheduling

- Both inbound syncs run on a nightly schedule (configurable cron)
- Any sync can be manually triggered via API by an ADMIN user
- SOH outbound push is event-driven (post-session completion)

#### Sync Audit Log

Every sync attempt (successful or failed) is recorded in `erp.erp_sync_logs`:
- Sync type (PRODUCT_INBOUND / INVENTORY_INBOUND / SOH_OUTBOUND)
- Status (SUCCESS / FAILED)
- Records fetched and published
- Error message (on failure)
- Start and completion timestamps
- Triggered by (manual / scheduled)

#### Error Handling

- Failed syncs are logged and do not retry automatically (manual re-trigger required)
- ERP API connection errors captured with full error message in sync log
- Partial sync failures: records processed up to the point of failure are committed; remainder logged

---

### 3.10 Real-Time Notifications

#### WebSocket Architecture

- Notification service connects to Kafka and listens to all event topics
- Connected browser clients maintain a persistent WebSocket connection to `ws://<server>:8091/ws`
- Events are fan-out to all relevant connected clients for a given store
- Authentication: WebSocket connection requires a valid JWT token

#### Event Types

| Event | Trigger | Recipients |
|---|---|---|
| `SOH_SESSION_COMPLETED` | Session marked complete | All STORE_MANAGERs for the store |
| `SOH_EPC_BATCH_RECEIVED` | New RFID reads arrive | STORE_MANAGER viewing that session |
| `REFILL_TASK_ASSIGNED` | Task assigned to associate | The assigned REFILL_ASSOCIATE |
| `REFILL_TASK_COMPLETED` | All items fulfilled | STORE_MANAGER for the store |
| `READER_OFFLINE` | Heartbeat missed > 5 minutes | STORE_MANAGER for the store |
| `READER_ONLINE` | Heartbeat resumes | STORE_MANAGER for the store |
| `INVENTORY_LOW_ACCURACY` | Product drops below threshold | STORE_MANAGER for the store |

#### Dashboard Real-Time Updates

- Dashboard KPI cards refresh automatically when new session or task events arrive
- SOH session detail screen updates EPC counts live without page refresh
- No polling — all updates are pushed by the server

---

### 3.11 RFID Device Management

#### Device Registry

Each RFID reader is registered with:
- **Reader Code** — unique identifier
- **Type** — `fixed`, `handheld`, or `bluetooth_sled`
- **Zone assignment** — which zone the reader is installed in
- **IP Address** — for fixed readers
- **Antenna count** — number of connected antennas
- **Tx Power** — transmission power in dBm
- **Firmware version** — for maintenance tracking

#### Heartbeat Monitoring

- Each active reader sends a heartbeat to the backend periodically
- A reader is shown as **Online** if a heartbeat was received within the last 5 minutes
- Heartbeat threshold is configurable per store
- Offline readers trigger a WebSocket notification to the Store Manager

#### Device Status Dashboard

- KPI cards: Total Readers, Online, Offline, Fixed Readers count
- Per-reader status, last seen timestamp, and enable/disable toggle
- All readers for a store visible on the Devices screen

#### Reader Types

| Type | Hardware Example | Use Case |
|---|---|---|
| `fixed` | Zebra FX9600 | Zone boundary monitoring; continuous reading |
| `handheld` | Zebra MC3300R | Manual item-level scanning |
| `bluetooth_sled` | Zebra RFD40 | Paired with TC-series; primary scanning device |

---

### 3.12 User Management

#### User Accounts

Each user record contains:
- First and last name, username, email
- Bcrypt-hashed password
- Role (single role per user)
- Store assignment (required for non-ADMIN roles)
- Active/inactive status
- Last login timestamp

#### Lifecycle

- **Create:** Admin creates user with role and store assignment
- **Update:** Admin can change name, email, role, store, or active status
- **Deactivate:** Soft delete — user cannot log in; all historical records preserved
- **Reactivate:** Admin can re-enable a deactivated user

#### Role Assignment

- Each user has exactly one role
- Role determines which navigation items are visible and which API endpoints are accessible
- Role changes take effect at the next login (new JWT issued on login reflects current role)

#### Store Assignment

- Non-ADMIN users are assigned to exactly one store
- Store assignment controls which store's data the user can see — enforced at the database level
- ADMIN users have no store assignment and access all stores

---

### 3.13 Zebra Mobile App (Android)

#### Platform

- Android 10–15 (API 29–35)
- Optimised for Zebra TC-series (TC52, TC72) and MC-series devices
- RFID via Zebra RFD40 Bluetooth sled (primary) or integrated TC-series reader

#### RFID Integration

- **Zebra EMDK SDK 9.x** — direct integration with Zebra RFID hardware
- **EmDkRfidReader** — production implementation; uses real RFID antenna
- **MockRfidReader** — debug/development implementation; emits fake EPCs every 200ms
- EPC reads stream as a Kotlin `Flow<String>` — fully reactive

#### Offline-First Architecture

- All RFID reads stored in **Room database** before any network call — scan data is never lost
- WorkManager uploads batches when network is available
- Retry policy: exponential backoff (15s → 30s → 60s → 120s → 240s)
- App functions fully without WiFi; syncs automatically on reconnect

#### SOH Scanning Features

- Start, pause, resume, and complete scan sessions
- Zone selection for scoped counts
- Real-time EPC count display during scanning
- Session progress synced to web portal in real time
- Accuracy result displayed on completion

#### Refill Task Features

- Task list synced from backend every 15 minutes (RefillSyncWorker)
- Tasks sorted by priority (1 = urgent)
- Per-item quantity entry with confirm/partial/skip options
- Offline fulfilment: actions queued and synced on reconnect
- Push notifications when new tasks assigned

#### Build Variants

| Variant | RFID | Backend | Use |
|---|---|---|---|
| Debug | MockRfidReader | `http://10.0.2.2:8080/` | Emulator & development |
| Release | Real EMDK | Production URL | Physical Zebra devices |

#### Security

- Tokens stored using Android `security-crypto` EncryptedSharedPreferences
- Certificate pinning on network calls (configurable)
- No sensitive data written to external storage

---

### 3.14 Audit & Compliance

#### Immutable Audit Log

- Every create, update, and delete operation across all services is recorded in `audit.audit_log`
- Audit records are write-once — no updates or deletes permitted
- Each record contains: entity type, entity ID, action, before/after values, user ID, store ID, IP address, timestamp

#### Data Partitioning

- `audit.audit_log` partitioned by month (PostgreSQL range partitioning)
- `rfid.rfid_reads` partitioned by month — handles millions of rows per day without performance degradation
- Old partitions can be archived to cold storage (e.g. S3) after retention period

#### Soft Delete Policy

- No master data is hard-deleted: products, stores, users, zones marked `is_active = false`
- Historical relationships preserved even when entities are deactivated
- Enables accurate historical reporting

#### Data Retention

- RFID reads: 24-hour log retention in Kafka; indefinite in PostgreSQL (partition-based archival)
- Audit log: indefinite (configurable archival)
- SOH sessions: indefinite
- KPI aggregates: indefinite

---

## 4. Platform Capabilities

### API

- **RESTful JSON API** over HTTPS
- **OpenAPI 3.0** specification (Springdoc) — Swagger UI at `/swagger-ui.html` on each service
- **49 endpoints** across 11 microservices
- Consistent response envelope: `{ success, code, message, data }`
- Pagination on all list endpoints
- Optional `X-Correlation-Id` header for distributed request tracing

### Event Streaming

- Apache Kafka for all async communication
- 6 Kafka topics covering RFID, SOH, ERP, and notification flows
- Consumer groups enable multiple consumers to scale independently
- Dead Letter Queue for failed RFID processing events with API-based replay

### Caching

- Redis 7 for:
  - JWT blacklist (logged-out tokens)
  - EPC-to-product lookup (< 1ms)
  - Session deduplication sets
  - API rate limit counters

### Database

- PostgreSQL 16 — single cluster, schema-per-service isolation
- 9 schemas: auth, stores, products, inventory, soh, refill, rfid, reporting, audit
- Flyway for version-controlled migrations
- Monthly range partitioning on high-volume tables
- Shared trigger function for `updated_at` management

---

## 5. Performance Specifications

| Metric | Target |
|---|---|
| Concurrent stores | 400 |
| Peak RFID reads/sec (system-wide) | 50,000 |
| Peak RFID reads/sec (per store) | 500 |
| SOH session to result latency | < 30 seconds |
| API response time p95 | < 500 ms |
| Refill task notification to device | < 5 seconds |
| Report generation (store, 90 days) | < 10 seconds |
| EPC lookup (cache hit) | < 1 ms |
| EPC lookup (cache miss) | < 50 ms |
| System uptime SLA | 99.9% |
| RFID processing service replicas (scalable) | 1–N |

---

## 6. Integration Capabilities

### ERP Integration

- **Protocol:** REST API (JSON)
- **Direction:** Bidirectional (inbound: products + expected inventory; outbound: SOH results)
- **Trigger:** Nightly scheduled + on-demand via API
- **Authentication:** Configurable API key or bearer token
- **ERP systems supported:** Any ERP with a REST API (configurable via `ERP_BASE_URL`, `ERP_API_KEY`)

### RFID Hardware Integration

| Hardware | Protocol | Integration |
|---|---|---|
| Zebra FX9600 (fixed) | LLRP | Direct TCP; configured via reader web UI |
| Zebra RFD40 (sled) | Zebra EMDK over Bluetooth | Android app (EMDK SDK 9.x) |
| Zebra TC-series (integrated) | Zebra EMDK | Android app |
| Zebra MC3300R (handheld) | Zebra EMDK | Android app |

### Monitoring & Observability

| Tool | Purpose |
|---|---|
| Prometheus | Metrics scraping (`/actuator/prometheus` on each service) |
| Grafana | Dashboard visualisation |
| ELK Stack | Structured log aggregation |
| OpenTelemetry | Distributed tracing |
| Jaeger | Trace visualisation |

---

## 7. Security Features

| Feature | Implementation |
|---|---|
| Authentication | JWT (HMAC-SHA256), 15-min access token |
| Token revocation | Redis blacklist on logout |
| Token rotation | Refresh token invalidated after single use |
| Password hashing | bcrypt, cost factor 12 |
| Multi-tenancy | Database-level store_id predicate |
| RBAC | Spring `@PreAuthorize` on every endpoint |
| Rate limiting | Nginx: 60 req/min per IP (200 burst for RFID) |
| CORS | Configurable allowed origins in Nginx |
| TLS | HTTPS termination at load balancer / reverse proxy |
| Secret management | Environment variables (no secrets in code) |
| Audit logging | Immutable `audit.audit_log` table |
| Token storage (browser) | In-memory JavaScript only (no localStorage) |
| Token storage (Android) | Android `security-crypto` EncryptedSharedPreferences |
| SQL injection prevention | Spring Data JPA parameterised queries |
| XSS prevention | Content-Security-Policy headers; data stored as plain text |

---

## 8. Feature Matrix by Role

| Feature | ADMIN | STORE_MANAGER | STORE_ASSOCIATE | REFILL_ASSOCIATE |
|---|---|---|---|---|
| **Login / Logout** | ✓ | ✓ | ✓ | ✓ |
| **Dashboard** | ✓ | ✓ | ✓ | — |
| **Start SOH session** | ✓ | ✓ | ✓ | — |
| **Complete SOH session** | ✓ | ✓ | ✓ | — |
| **Cancel SOH session** | ✓ | ✓ | ✓ | — |
| **Submit RFID batch** | ✓ | ✓ | ✓ | ✓ |
| **View SOH sessions** | ✓ | ✓ (own store) | ✓ (own store) | — |
| **View inventory** | ✓ | ✓ (own store) | — | — |
| **View low-accuracy products** | ✓ | ✓ (own store) | — | — |
| **View EPC summary** | ✓ | ✓ (own store) | — | — |
| **Create refill task** | ✓ | ✓ (own store) | — | — |
| **Assign refill task** | ✓ | ✓ (own store) | — | — |
| **Fulfil refill task items** | ✓ | ✓ | — | ✓ |
| **Cancel refill task** | ✓ | ✓ (own store) | — | — |
| **View refill tasks** | ✓ | ✓ (own store) | — | ✓ (assigned only) |
| **View reports / KPI** | ✓ | ✓ (own store) | — | — |
| **Trigger KPI aggregation** | ✓ | — | — | — |
| **View stores** | ✓ | — | — | — |
| **Create / edit stores** | ✓ | — | — | — |
| **Create / edit zones** | ✓ | ✓ (own store) | — | — |
| **View products** | ✓ | ✓ | ✓ | ✓ |
| **Create / edit products** | ✓ | — | — | — |
| **Associate EPC to product** | ✓ | ✓ | — | — |
| **View RFID devices** | ✓ | ✓ (own store) | — | — |
| **Manage users** | ✓ | — | — | — |
| **Trigger ERP sync** | ✓ | — | — | — |
| **View ERP sync logs** | ✓ | — | — | — |
| **View DLQ** | ✓ | — | — | — |
| **Replay DLQ events** | ✓ | — | — | — |

---

## 9. Deployment Options

### Option 1 — Docker Compose (Single Server)

- All services deployed as Docker containers on one Ubuntu server
- Recommended for: small chains (1–20 stores), development, staging
- One-command deployment: `bash deploy.sh`
- Auto-start on reboot via systemd
- Minimum: 4 CPU, 8 GB RAM, 40 GB disk

### Option 2 — Kubernetes (Multi-Node Cluster)

- Kubernetes manifests provided in `deploy/k8s/`
- Horizontal Pod Autoscaler configured for rfid-processing-service
- Recommended for: production chains (20+ stores), high-availability requirements
- Supports multi-region deployments for geographic distribution
- CD pipeline automates staging and production rollouts via GitHub Actions

### Option 3 — Hybrid (Native Java + Docker Infrastructure)

- Infrastructure (PostgreSQL, Redis, Kafka) in Docker
- Spring Boot services run as native Java processes
- Recommended for: local development and debugging
- Fastest iteration cycle for backend changes
- Use `deploy/start-services.ps1` on Windows

### CI/CD Pipeline

- **CI:** GitHub Actions validates every PR — backend build, frontend type-check, Docker image build
- **CD:** Push to `main` triggers image build → push to GHCR → staging deployment → smoke tests → production deployment (requires manual approval for release tags)
- **Images:** Multi-architecture (linux/amd64 + linux/arm64) pushed to GitHub Container Registry
