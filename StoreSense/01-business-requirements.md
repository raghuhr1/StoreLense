# Business Requirements

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.1
**Date:** 2026-06-06

---

## 1. Background

The current RIOT (RFID Inventory Operations Toolset) platform is end-of-life and no longer maintained. Across 400 stores it delivers poor inventory accuracy visibility, lacks real-time alerting, and cannot scale to support the planned store network growth. StoreLense replaces RIOT with a purpose-built, cloud-hosted platform.

---

## 2. Business Objectives

| # | Objective | Measure of Success |
|---|---|---|
| BR-01 | Achieve ≥ 98% inventory accuracy across all stores | Monthly SOH accuracy report shows < 2% variance vs ERP expected |
| BR-02 | Reduce time-to-refill from 4 hours average to < 1 hour | Refill completion time KPI, 90th percentile |
| BR-03 | Give store managers real-time visibility of stock levels | SOH result available within 30 seconds of session completion |
| BR-04 | Retire RIOT across all 400 stores within 4 months | All stores migrated; RIOT decommissioned |
| BR-05 | Integrate with ERP for product master and SOH push | Bi-directional sync operational for all stores |
| BR-06 | Comply with retail data privacy requirements | Audit log for all data writes; PII encrypted at rest |

---

## 3. Scope

### In Scope

- Cloud-hosted web portal for store operations (Admin, Store Manager, Store Associate, Refill Associate roles)
- Zebra Android handheld application for RFID scanning (Zebra TC-series + RFD40/RFD90 Bluetooth sleds)
- Real-time Stock on Hand (SOH) count sessions with RFID
- Refill task management with mobile task assignment and completion
- ERP integration (inbound product master and expected inventory; outbound SOH push)
- User management and role-based access control per store
- Operational KPI reporting (inventory accuracy, refill compliance, RFID throughput)
- Deployment via Docker Compose (on-premise / VM) with migration path to Kubernetes

### Out of Scope

- Point-of-sale (POS) integration
- Customer-facing features
- Financial / accounting systems
- RFID tag encoding hardware provisioning
- Store Wi-Fi infrastructure

---

## 4. Stakeholders

| Role | Responsibility |
|---|---|
| Retail Operations Director | Business sponsor; KPI sign-off |
| Store Managers | Day-to-day SOH and refill operations; UAT |
| Store Associates | RFID scanning; SOH counts |
| Refill Associates | Refill task execution |
| IT / Infrastructure Team | Deployment, server management, network |
| ERP Team | Product master data feed and SOH push integration |
| Development Team | Platform build, deployment, and support |

---

## 5. Functional Requirements

### FR-01 Authentication and User Management

- Users authenticate with username and password; system issues a JWT access token (15 min) and refresh token (7 days).
- Four roles: ADMIN, STORE_MANAGER, STORE_ASSOCIATE, REFILL_ASSOCIATE.
- ADMIN role has access to all stores; store-scoped roles are restricted to their assigned store.
- User accounts can be created, updated, and soft-deleted by ADMIN.
- Account lockout after repeated failed login attempts.

### FR-02 Store and Zone Management

- ADMIN can create and maintain store master records with address, timezone, and ERP cross-reference.
- Each store has one or more zones (floor, backroom, fitting room, stockroom, display).
- RFID readers (fixed, handheld, Bluetooth sled) are registered per store, optionally assigned to a zone.
- Store-level RFID configuration (power level, Gen2 session, target) stored and pushed to mobile devices.

### FR-03 Product Master

- ADMIN can create and manage products with SKU, name, brand, category, and unit of measure.
- Each product can have multiple barcode associations (EAN-13, UPC-A, etc.).
- EPC tags are registered against products; lookup by EPC returns the associated product instantly.
- Products sync in from ERP on a scheduled basis; changes are published via Kafka.

### FR-04 RFID Stock on Hand (SOH) Counting

- Associates initiate SOH count sessions from the Zebra app or web portal, scoped to a store or specific zone.
- RFID reads are streamed from the Zebra device to the platform in batches.
- The platform deduplicates reads within a session window, decodes GS1 EPC to product, and calculates counted quantity per SKU.
- On session completion, the system calculates variance vs ERP expected quantities and persists the SOH result.
- Store Managers can view session results and variance details in the web portal.

### FR-05 Refill Task Management

- Refill tasks are created manually by Store Managers or automatically triggered by SOH variance results.
- Each task contains one or more product-zone line items with requested quantities.
- Tasks are assigned to Refill Associates; the associate receives the task on their Zebra device.
- Associates fulfil items by scanning and marking quantities; task completion is synced in real time or on reconnect.
- Store Managers can view task status, assignment history, and completion rates.

### FR-06 ERP Integration

- Inbound: Product master and expected-inventory data is pulled from ERP on a scheduled interval (configurable, default 6 hours).
- Outbound: Actual SOH results are pushed to ERP after each completed count session.
- Sync audit logs track records fetched, published, and failed for each sync run.
- ADMIN can manually trigger a product or inventory sync from the web portal.

### FR-07 Notifications

- Real-time WebSocket push to connected browsers and mobile devices.
- Events: SOH session status updates, refill task assignments, low-accuracy alerts.

### FR-08 Reporting and KPIs

- Daily KPI snapshot per store: inventory accuracy %, SOH session count, refill completion rate, refill average time.
- ADMIN can view KPIs across all stores; Store Manager sees their own store only.
- KPI data available via API for date-range queries.

---

## 6. Non-Functional Requirements

| # | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | API p95 response time < 500 ms under normal load |
| NFR-02 | Throughput | Platform handles 400 concurrent active stores; peak 50,000 RFID reads/sec burst |
| NFR-03 | Availability | 99.9% uptime (≤ 8.7 hours downtime/year) |
| NFR-04 | SOH Latency | SOH session result available within 30 seconds of session completion |
| NFR-05 | Offline | Zebra app functions during Wi-Fi loss; syncs on reconnect |
| NFR-06 | Security | All external traffic HTTPS/TLS 1.3; JWT auth on every API call; audit log for all writes |
| NFR-07 | Privacy | PII (names, email) encrypted at rest; RFID EPC data is product-identifying only |
| NFR-08 | Scalability | RFID processing service horizontally scalable by Kafka consumer lag |
| NFR-09 | Auditability | Immutable audit log: who, what, when, store for every write operation |
| NFR-10 | Maintainability | Each microservice independently deployable; Flyway-managed DB migrations |

---

## 7. Constraints

- Zebra devices run Android API 29+ with Zebra EMDK SDK.
- ERP integration must use REST or file-based EDI (specific format confirmed per deployment).
- First deployment is on-premise VM using Docker Compose; Kubernetes migration in Phase 2.
- All services must expose Spring Actuator `/actuator/health` for Docker healthchecks.
