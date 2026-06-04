# Solution Architecture

**Project:** StoreLense — RFID Store Operations Platform  
**Scope:** 400 stores, replacing legacy RIOT platform  
**Date:** 2026-06-03  
**Version:** 1.0

---

## 1. Overview

StoreLense is a cloud-hosted, microservices-based RFID platform that replaces the RIOT system across 400 retail stores. It provides real-time Stock on Hand (SOH) visibility, intelligent refill task management, and operational reporting through a React web portal and a Zebra Android mobile application.

---

## 2. Architecture Principles

| Principle | Application |
|---|---|
| Domain-Driven Design | Each microservice owns its domain data and logic |
| API-First | All integrations and UI layers consume versioned REST APIs |
| Offline-First Mobile | Zebra app functions during Wi-Fi degradation; syncs on reconnect |
| Event-Driven RFID Processing | RFID reads ingested as events, not blocking API calls |
| Zero-Trust Security | JWT-based auth, role-based access, service-to-service mTLS |
| 12-Factor App | Config via environment, stateless services, structured logs |

---

## 3. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          STORE USERS                                │
│   Store Associates / Refill Associates / Store Managers / Admins    │
└──────────────┬───────────────────────────────────┬──────────────────┘
               │                                   │
   ┌───────────▼──────────┐           ┌────────────▼──────────┐
   │   React Web Portal   │           │  Zebra Android App    │
   │   (Web Browser)      │           │  (Handheld Scanner)   │
   └───────────┬──────────┘           └────────────┬──────────┘
               │  HTTPS                             │  HTTPS / WebSocket
               └──────────────┬─────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │    API Gateway       │
                   │  (Spring Cloud GW)   │
                   │  Auth · Rate Limit   │
                   │  Routing · CORS      │
                   └──────────┬──────────┘
                              │
        ┌─────────────────────┼──────────────────────┐
        │                     │                      │
┌───────▼──────┐  ┌───────────▼───────┐  ┌──────────▼──────────┐
│  Auth        │  │  Core Services    │  │  RFID Processing    │
│  Service     │  │                   │  │  Pipeline           │
│              │  │  · Store Svc      │  │                     │
│  JWT / OAuth │  │  · Product Svc    │  │  · Tag Ingest Svc   │
│  User Mgmt   │  │  · Inventory Svc  │  │  · EPC Decoder      │
│  Roles       │  │  · SOH Svc        │  │  · Conflict Resolver│
└──────────────┘  │  · Refill Svc     │  │  · SOH Calculator   │
                  │  · Reporting Svc  │  └─────────┬───────────┘
                  └───────────────────┘            │
                              │              ┌──────▼──────────┐
                              │              │  Message Broker  │
                              │              │  (Kafka/RabbitMQ)│
                              │              └──────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │  Data Layer          │
                   │                     │
                   │  PostgreSQL (OLTP)   │
                   │  Redis (Cache/Queue) │
                   │  S3 (Reports/Blobs)  │
                   └──────────┬──────────┘
                              │
                   ┌──────────▼──────────┐
                   │  ERP Integration    │
                   │  (Outbound Adapter) │
                   │  · Product Sync     │
                   │  · Inventory Feed   │
                   └─────────────────────┘
```

---

## 4. Frontend — React Web Portal

### 4.1 Technology Stack

| Layer | Technology |
|---|---|
| Framework | React 18 + TypeScript |
| State Management | Redux Toolkit + RTK Query |
| UI Component Library | Ant Design or MUI (to be confirmed) |
| Charts / Reports | Recharts or Apache ECharts |
| Routing | React Router v6 |
| Build | Vite |
| Auth | OAuth2 PKCE flow → JWT stored in memory (not localStorage) |

### 4.2 Modules

```
src/
├── modules/
│   ├── auth/           # Login, logout, role-based route guards
│   ├── admin/          # User management, store config, system settings
│   ├── inventory/      # Product master, EPC tag registry
│   ├── soh/            # SOH dashboard, count sessions, variance reports
│   ├── refill/         # Task board, assignment, completion tracking
│   └── reporting/      # KPI dashboards, export, compliance views
├── shared/
│   ├── api/            # RTK Query API slices
│   ├── components/     # Reusable UI primitives
│   └── hooks/          # Auth, permissions, websocket
└── app/                # Root layout, router, store
```

### 4.3 Role-Based Access

| Role | Accessible Modules |
|---|---|
| Admin | All modules including User Management and System Config |
| Store Manager | SOH, Refill, Reporting (own store) |
| Store Associate | SOH count sessions (own store) |
| Refill Associate | Refill task list and completion (own store) |

---

## 5. Mobile — Zebra Android Application

### 5.1 Technology Stack

| Layer | Technology |
|---|---|
| Platform | Android (API 29+ — Zebra TC-series devices) |
| Language | Kotlin |
| UI | Jetpack Compose |
| RFID SDK | Zebra EMDK / RFD40 Bluetooth RFID SDK |
| Local DB | Room (SQLite) |
| Network | Retrofit + OkHttp |
| Sync | WorkManager for background sync |

### 5.2 Offline-First Architecture

```
┌──────────────────────────────────┐
│         Zebra App                │
│                                  │
│  ┌────────────┐  ┌────────────┐  │
│  │ UI Layer   │  │ RFID Layer │  │
│  │ (Compose)  │  │ (EMDK SDK) │  │
│  └─────┬──────┘  └──────┬─────┘  │
│        │                │        │
│  ┌─────▼────────────────▼─────┐  │
│  │     Domain / Use Case       │  │
│  │     Layer                   │  │
│  └──────────────┬──────────────┘  │
│                 │                 │
│  ┌──────────────▼──────────────┐  │
│  │    Local Room Database       │  │
│  │    (SOH sessions, tasks,     │  │
│  │     pending EPC reads)       │  │
│  └──────────────┬──────────────┘  │
│                 │                 │
│  ┌──────────────▼──────────────┐  │
│  │  Sync Manager (WorkManager)  │  │
│  │  · Upload when online        │  │
│  │  · Pull task updates         │  │
│  └──────────────────────────────┘  │
└──────────────────────────────────┘
           │  HTTPS + JWT
    ┌──────▼───────┐
    │  API Gateway  │
    └───────────────┘
```

### 5.3 Key Mobile Flows

- **SOH Count** — Associate scans zone; EMDK streams EPC reads → buffered locally → batch uploaded on session commit.
- **Refill Task** — Associate receives task from API; scans product to confirm placement; marks complete → synced immediately or on reconnect.
- **RFID Configuration** — Power level, session, target configurable per store profile pulled from backend.

---

## 6. Backend — Spring Boot Microservices

### 6.1 Service Inventory

| Service | Responsibility | Port |
|---|---|---|
| `api-gateway` | Routing, auth filter, rate limiting, CORS | 8080 |
| `auth-service` | Login, JWT issuance, token refresh, user/role CRUD | 8081 |
| `store-service` | Store master, zone config, RFID reader config | 8082 |
| `product-service` | Product master, EPC-to-SKU mapping, barcode | 8083 |
| `inventory-service` | Current inventory state, EPC tag registry | 8084 |
| `soh-service` | SOH count sessions, variance calc, accuracy KPI | 8085 |
| `refill-service` | Refill task CRUD, assignment, completion, KPI | 8086 |
| `rfid-ingest-service` | Receives raw RFID reads, validates, publishes to topic | 8087 |
| `rfid-processing-service` | Consumes reads, decodes EPC, resolves SOH delta, stores | 8088 |
| `reporting-service` | Aggregated KPIs, PDF/CSV export, compliance reports | 8089 |
| `erp-integration-service` | Bidirectional ERP sync (product master, inventory push) | 8090 |
| `notification-service` | WebSocket push (refill alerts, SOH session updates) | 8091 |

### 6.2 Inter-Service Communication

```
Synchronous  → REST (via API Gateway for external; direct Feign for internal)
Asynchronous → Kafka topics for RFID events and ERP sync
Real-time    → WebSocket via notification-service for browser/mobile push
```

### 6.3 API Gateway Detail

- **Auth Filter** — validates JWT on every request; extracts `storeId`, `role`, injects as headers downstream.
- **Routing** — path-prefix routing to services; `/api/auth/**` → auth-service, `/api/soh/**` → soh-service, etc.
- **Rate Limiting** — per-user token bucket (Redis-backed) to protect RFID ingest under bulk scan events.
- **Circuit Breaker** — Resilience4j wrapping each downstream route.

---

## 7. RFID Processing Pipeline

```
Zebra Scanner
     │  EPC read stream (LLRP or EMDK SDK)
     ▼
rfid-ingest-service
  · Validates read payload
  · Attaches storeId + sessionId from JWT context
  · Publishes to Kafka topic: rfid.reads.raw
     │
     ▼
rfid-processing-service (Kafka Consumer)
  · Decodes GS1 EPC (SGTIN-96/198 → company + item + serial)
  · Looks up EPC → SKU in product-service (cached via Redis)
  · Deduplicates within session window (Redis SET per sessionId)
  · Calculates SOH delta vs last committed count
  · Persists EPC reads to rfid_reads table
  · Publishes to topic: rfid.soh.updated
     │
     ▼
soh-service (Kafka Consumer)
  · Updates SOH record for store + SKU + zone
  · Computes inventory accuracy vs ERP expected quantity
  · Publishes to topic: soh.session.updated
     │
     ▼
notification-service (Kafka Consumer)
  · Pushes WebSocket event to connected store manager / associate
```

---

## 8. Data Architecture

### 8.1 Primary Database — PostgreSQL

Each service owns its schema (logical separation, single cluster for MVP; split per service in Phase 2 if needed).

```
Schema: auth
  └── users, roles, user_roles, refresh_tokens

Schema: stores
  └── stores, zones, rfid_readers, store_config

Schema: products
  └── products, epc_tags, barcodes, product_categories

Schema: inventory
  └── inventory_state, epc_registry

Schema: soh
  └── soh_sessions, soh_session_items, soh_results, soh_variance

Schema: refill
  └── refill_tasks, refill_task_items, refill_assignments

Schema: rfid
  └── rfid_reads, rfid_sessions

Schema: reporting
  └── report_snapshots, kpi_daily (materialized views)
```

### 8.2 Key Table Relationships

```
stores ──< zones ──< rfid_readers
stores ──< soh_sessions ──< soh_session_items >── products
products ──< epc_tags
soh_sessions ──< rfid_reads
stores ──< refill_tasks ──< refill_task_items >── products
users >── user_roles >── roles
```

### 8.3 Redis Usage

| Purpose | Key Pattern | TTL |
|---|---|---|
| JWT blacklist (logout) | `jwt:blacklist:{jti}` | Token expiry |
| EPC dedup per session | `rfid:dedup:{sessionId}:{epc}` | Session duration + 5 min |
| Product/EPC lookup cache | `product:epc:{epc}` | 30 min |
| SOH session state | `soh:session:{sessionId}` | 24 hr |
| Rate limit buckets | `ratelimit:{userId}` | Rolling 60s |

### 8.4 Object Storage — S3-Compatible

- Generated PDF/CSV reports
- Bulk EPC import files (initial tag encoding load)
- Audit log archives (after 90 days, rotated from PostgreSQL)

---

## 9. Integration Architecture

### 9.1 ERP Integration

```
ERP System (SAP / Oracle / other)
     │
     │  Inbound: Product master + expected inventory
     ▼
erp-integration-service
  · Scheduled pull (cron) or webhook receiver
  · Transforms ERP format → internal product/inventory model
  · Publishes to Kafka: erp.product.sync, erp.inventory.expected
     │
     │  Outbound: Actual SOH results after count sessions
     ▼
ERP System
  · REST callback or file-based EDI drop
```

### 9.2 RFID Hardware Integration

| Component | Protocol / SDK |
|---|---|
| Zebra FX Series Fixed Readers | LLRP over TCP (rfid-ingest-service direct connection) |
| Zebra RFD40/RFD90 Bluetooth Sleds | Zebra EMDK SDK in Android app |
| Zebra TC-series handheld scanners | Zebra EMDK SDK (built-in scanner) |

---

## 10. Security Architecture

### 10.1 Authentication & Authorization

```
Client (Web/Mobile)
  → POST /api/auth/login (username + password)
  ← access_token (JWT, 15 min) + refresh_token (opaque, 7 days)

Every subsequent request:
  → Authorization: Bearer {access_token}
  API Gateway validates signature + expiry + blacklist check
  Injects X-User-Id, X-Store-Id, X-Role headers for downstream services

Role hierarchy:
  ADMIN > STORE_MANAGER > STORE_ASSOCIATE, REFILL_ASSOCIATE
```

### 10.2 Network Security

| Layer | Control |
|---|---|
| External → API Gateway | HTTPS/TLS 1.3 only |
| Service-to-service | mTLS via service mesh (Linkerd or Istio) |
| Database connections | TLS + credential rotation via Vault |
| Zebra device → API | Certificate pinning in Android app |

### 10.3 Data Security

- PII (user names, emails) encrypted at rest (PostgreSQL column-level or TDE).
- RFID EPC data is product-identifying, not personal — standard encryption at rest.
- Audit log for all write operations: `who + what + when + storeId`.
- Multi-tenancy enforced via `store_id` predicate on all data access; services reject requests without valid store context.

---

## 11. Infrastructure & Deployment

### 11.1 Deployment Topology

```
Cloud Provider (AWS / Azure — to be confirmed)

  VPC
  ├── Public Subnet
  │    └── Load Balancer (ALB) → API Gateway pods
  │
  ├── Private Subnet — Application Tier
  │    └── Kubernetes Cluster (EKS / AKS)
  │         ├── api-gateway (2+ replicas)
  │         ├── auth-service (2+ replicas)
  │         ├── soh-service (2+ replicas)
  │         ├── rfid-processing-service (scaled by Kafka lag)
  │         └── ... (all microservices)
  │
  ├── Private Subnet — Data Tier
  │    ├── RDS PostgreSQL (Multi-AZ)
  │    ├── ElastiCache Redis (cluster mode)
  │    └── MSK Kafka (3-broker cluster)
  │
  └── S3 — Report and archive storage
```

### 11.2 Kubernetes Concerns

- **HPA** on rfid-processing-service driven by Kafka consumer lag metric.
- **PodDisruptionBudget** on all stateless services to ensure zero-downtime rollouts.
- **ConfigMaps / Secrets** via Kubernetes Secrets + external secret operator (Vault sync).
- **Liveness / Readiness probes** on all services via Spring Actuator `/health`.

### 11.3 CI/CD Pipeline

```
GitHub (source) → GitHub Actions
  ├── PR: build + unit tests + SAST (Checkmarx/Semgrep)
  ├── Merge to main: build Docker image → push to ECR → deploy to staging
  └── Tag release: deploy to production (manual approval gate)
```

### 11.4 Environments

| Environment | Purpose | Store Scope |
|---|---|---|
| Development | Feature development, unit tests | Mock data |
| Staging | Integration tests, UAT, demo | 1 pilot store |
| Production | Live operations | 400 stores (phased) |

---

## 12. Observability

### 12.1 Logging

- Structured JSON logs (Logback + logstash-logback-encoder) from all services.
- Correlation ID propagated via `X-Correlation-Id` header across all service hops.
- Log aggregation: ELK Stack (Elasticsearch + Logstash + Kibana) or CloudWatch Logs.

### 12.2 Metrics

- Spring Actuator Micrometer metrics scraped by Prometheus.
- Grafana dashboards for: request latency per service, Kafka consumer lag, RFID read throughput, SOH session duration, error rates.

### 12.3 Tracing

- Distributed tracing via OpenTelemetry SDK + Jaeger or AWS X-Ray.
- Critical traces: RFID read → SOH update end-to-end latency.

### 12.4 Alerting

| Alert | Threshold | Channel |
|---|---|---|
| API Gateway error rate | > 1% over 5 min | PagerDuty |
| Kafka consumer lag | > 10,000 messages | PagerDuty |
| RFID processing latency | > 5s p95 | Slack |
| DB connection pool exhaustion | > 90% | PagerDuty |
| SOH session stuck | No update > 10 min | Slack |

---

## 13. Performance & Scalability Targets

| Metric | Target |
|---|---|
| Concurrent stores active | 400 |
| Peak RFID reads/sec (store count day) | 500 reads/sec per store |
| Total system peak RFID throughput | 50,000 reads/sec (burst) |
| SOH session to result latency | < 30 seconds |
| API response time (p95) | < 500 ms |
| Refill task sync to mobile | < 5 seconds |
| Report generation (store-day) | < 10 seconds |
| System uptime | 99.9% (≤ 8.7 hrs/year downtime) |

---

## 14. Non-Functional Requirements Mapping

| NFR | Approach |
|---|---|
| **Availability** | Multi-AZ DB, Kubernetes HPA, circuit breakers |
| **Scalability** | Stateless services, Kafka for RFID ingestion, horizontal pod scaling |
| **Security** | Zero-trust, mTLS, JWT, certificate pinning, audit logs |
| **Offline resilience** | Room DB on Zebra + WorkManager sync |
| **Maintainability** | Domain-isolated microservices, OpenAPI specs, IaC (Terraform) |
| **Auditability** | Immutable audit log, structured logging with correlation IDs |
| **Rollout safety** | Feature flags (LaunchDarkly or Unleash) for per-store activation |

---

## 15. Rollout Architecture Alignment

| Phase | Target | Architecture Implication |
|---|---|---|
| Month 1 | SOH module live | soh-service + rfid-processing-service + auth + store + product services deployed |
| Month 2 | Refill + Pilot store | refill-service + notification-service + Zebra app deployed to 1 pilot store |
| Month 3 | 110 stores | Auto-scaling validated; ERP integration live; reporting KPIs active |
| Month 4 | 400 stores + RIOT off | Full production scale; RIOT data migration complete; ERP cutover |

---

## 16. Open Decisions

| # | Decision | Options | Owner |
|---|---|---|---|
| 1 | Cloud provider | AWS vs Azure | Engineering Lead |
| 2 | Message broker | Kafka (MSK) vs RabbitMQ | Engineering Lead |
| 3 | Service mesh | Linkerd vs Istio vs none (MVP) | DevOps |
| 4 | ERP system type | SAP / Oracle / custom | Business |
| 5 | UI component library | Ant Design vs MUI | Frontend Lead |
| 6 | DB per service vs shared cluster | Shared cluster (MVP) → per-service later | Engineering Lead |
| 7 | Report engine | JasperReports vs Apache POI vs cloud | Backend Lead |
