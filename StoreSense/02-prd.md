# Product Requirements Document

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.1
**Date:** 2026-06-06

---

## 1. Product Vision

StoreLense is a real-time RFID inventory operations platform for retail store networks. It replaces the legacy RIOT system with a modern, cloud-hosted platform that gives store operations teams accurate stock visibility, automated refill task creation, and KPI reporting — all driven by RFID scanning on Zebra handheld devices.

---

## 2. Users and Roles

| Role | Who | What they do |
|---|---|---|
| **ADMIN** | IT/Operations team | Manages stores, products, users, ERP sync, and system config |
| **STORE_MANAGER** | Store Manager | Monitors SOH accuracy, reviews variance, manages refill tasks, views store KPIs |
| **STORE_ASSOCIATE** | Floor associate | Runs RFID SOH count sessions with their Zebra device |
| **REFILL_ASSOCIATE** | Stockroom associate | Receives and completes refill tasks on their Zebra device |

---

## 3. Modules

### Module 1 — Administration

**Audience:** ADMIN

| Feature | Description |
|---|---|
| User Management | Create, update, and deactivate users; assign roles and store |
| Store Management | Create and maintain store master records (address, timezone, ERP code) |
| Zone Management | Define zones within each store (floor, backroom, fitting room, etc.) |
| RFID Reader Management | Register readers per store/zone; track last heartbeat |
| Product Master | Create and update product records; manage EPC-to-product mappings |
| ERP Sync Admin | Manually trigger product or inventory sync; view sync audit logs |

### Module 2 — Inventory

**Audience:** ADMIN, STORE_MANAGER

| Feature | Description |
|---|---|
| Inventory State | View current on-hand quantities per store/product/zone |
| Low Accuracy List | See products whose accuracy falls below a configurable threshold |
| EPC Summary | See count of EPC tags by status (in_store, sold, missing, etc.) |
| EPC Registration | Register EPC tags against products (bulk or individual) |

### Module 3 — Stock on Hand (SOH)

**Audience:** STORE_MANAGER, STORE_ASSOCIATE

| Feature | Description |
|---|---|
| Start SOH Session | Initiate a count session for full store or specific zone |
| RFID Scanning | Stream EPC reads from Zebra device; deduplicated in real time |
| Session Result | View counted vs expected quantities per product after completion |
| Variance Report | Drill into products with overcount/undercount variance |
| Session History | Browse past sessions with accuracy %, session type, and date |

### Module 4 — Refill Tasks

**Audience:** STORE_MANAGER, REFILL_ASSOCIATE

| Feature | Description |
|---|---|
| Task List | View all pending/assigned/in-progress tasks for the store |
| Create Task | Manually create a task with product-zone items and quantities |
| Task Assignment | Assign a task to a Refill Associate |
| Task Completion | Associate marks items fulfilled (with quantity); task auto-completes when all items done |
| Fulfilment Tracking | Manager views fulfilled vs requested quantities per item |
| Auto-Task from SOH | System auto-creates tasks when SOH variance exceeds threshold |

### Module 5 — Reporting

**Audience:** ADMIN (all stores), STORE_MANAGER (own store)

| Feature | Description |
|---|---|
| Daily KPI Dashboard | Inventory accuracy %, SOH session count, refill completion rate, avg refill time |
| KPI Date Range | Query KPI metrics for any date range |
| Cross-Store Summary | ADMIN view aggregated KPIs across all stores |

---

## 4. Key Performance Indicators

| KPI | Definition | Target |
|---|---|---|
| Inventory Accuracy | (Counted units / Expected units) × 100, per store per session | ≥ 98% |
| Refill Completion Rate | (Completed tasks / Created tasks) × 100, 30-day rolling | ≥ 95% |
| Average Refill Time | Mean time from task creation to completion | < 60 minutes |
| SOH Session Duration | Time from session start to result available | < 30 seconds post-scan |
| RFID Read Throughput | Peak reads/sec per store during count session | ≥ 500 reads/sec |

---

## 5. Web Portal Features (Implemented)

The web portal is a Next.js 15 application with role-based navigation.

| Page | Route | Roles | Description |
|---|---|---|---|
| Login | `/login` | All | JWT authentication form |
| Dashboard | `/dashboard` | All | Overview KPIs and recent activity |
| Stores | `/stores` | ADMIN | Store list, create, view details |
| Store Detail | `/stores/[id]` | ADMIN | Zones, readers, store config |
| Products | `/products` | ADMIN | Product list, create, EPC registration |
| Users | `/users` | ADMIN | User list, create, manage |
| SOH Sessions | `/soh` | STORE_MANAGER, STORE_ASSOCIATE | Session list, start, results |
| Refill Tasks | `/refill` | STORE_MANAGER, REFILL_ASSOCIATE | Task board, assign, complete |
| Reporting | `/reporting` | ADMIN, STORE_MANAGER | KPI dashboard, date range |

---

## 6. Mobile App Features (Zebra Android)

| Feature | Description |
|---|---|
| JWT Login | Username/password → JWT stored in Room (encrypted) |
| SOH Scanning | Start/resume session; EMDK streams reads → batch upload |
| Offline Buffering | RFID reads buffered locally in Room; synced via WorkManager on reconnect |
| Refill Task List | Assigned tasks pushed via WorkManager sync; offline-capable |
| Task Completion | Scan or manual quantity entry per item; sync on reconnect |
| RFID Configuration | Power, session, target pulled from store config API |
| Mock RFID Reader | `MockRfidReader` for dev/testing on non-Zebra devices |

---

## 7. API Gateway

nginx 1.27 (alpine) is used as the API gateway:

- Routes all `/api/*` paths to the correct microservice
- Lazy DNS resolution via Docker's internal resolver (`127.0.0.11`) — services can start in any order
- CORS headers added on all responses
- Rate limiting: 60 req/min per IP (burst 20) on API routes; 200 req/min burst on RFID ingest
- `/health` endpoint returns 200 directly (no backend dependency)
- WebSocket proxy to notification-service at `/ws`

---

## 8. Deployment

Current deployment model: **Docker Compose on Linux VM**

| Component | Image / Build |
|---|---|
| PostgreSQL | `postgres:16-alpine` |
| Redis | `redis:7-alpine` |
| Kafka | `apache/kafka:3.9.0` |
| 11 microservices | Built from `backend/` monorepo (multi-module Maven) |
| Frontend | Built from `frontend/` (Next.js 15, multi-stage Docker) |
| nginx gateway | `nginx:1.27-alpine` with custom `nginx.conf` |

All services use a shared PostgreSQL cluster with schema-per-service isolation. Flyway migrations run on first startup.

---

## 9. Acceptance Criteria

| Module | Acceptance Criterion |
|---|---|
| Auth | Login returns JWT; invalid credentials return 401; token expires after 15 min |
| Stores | CRUD operations succeed; store-scoped users cannot see other stores |
| Products | Create product; register EPC; lookup by EPC returns correct product within 500 ms |
| SOH | Initiate session; submit batch RFID reads; complete → result shows correct counted qty and variance |
| Refill | Create task; assign; fulfil items; task moves to completed state |
| Reporting | Daily KPI endpoint returns non-empty data after at least one completed SOH session |
| ERP Sync | Manual trigger runs sync; audit log shows records fetched |
