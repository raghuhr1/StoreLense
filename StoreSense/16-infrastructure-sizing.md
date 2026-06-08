# Document 16 — Infrastructure Sizing Guide

**Project:** StoreLense — RFID Store Operations Platform  
**Scope:** 400 Stores, Production Deployment  
**Version:** 1.0  
**Date:** 2026-06-07  
**Status:** Approved for Planning

---

## 1. Purpose

This document defines the infrastructure sizing recommendations for deploying StoreLense at full scale across 400 retail stores. It covers two operational models (real-time scanning and once-daily sync), component-level sizing for all infrastructure layers, and cost benchmarks across Indian cloud providers.

---

## 2. Operational Models

StoreLense supports two deployment patterns. The chosen model drives infrastructure sizing by a factor of 500×.

| Parameter | Real-Time Model | Daily Sync Model |
|---|---|---|
| RFID reads/sec (peak) | 50,000 | ~100 API calls/sec (burst) |
| Concurrent scanning stores | 100 | 30–50 (upload window only) |
| Upload pattern | Continuous stream | Batch once per day per store |
| Kafka throughput | 25 MB/sec sustained | ~2 MB/sec burst |
| PostgreSQL IOPS | 16,000 sustained | ~2,000 burst, 300 idle |
| Redis working set | 2.5 GB | 256 MB |
| Est. monthly infra cost | ₹8–12 lakh | ₹1.4–2.6 lakh |

**Daily sync** is the recommended model for Phase 1 rollout (400 stores). It aligns with store operations (count at opening or closing), reduces infrastructure cost by ~80%, and is fully viable on managed cloud services.

---

## 3. Load Model — Daily Sync (400 Stores)

### 3.1 Per-Store Upload Profile

| Parameter | Value | Basis |
|---|---|---|
| EPCs per store | ~200,000 | Apparel: 1 RFID tag per garment |
| Android batch size | 300 EPCs | SohRepository chunk (fixed) |
| HTTP batches per store sync | ~667 | 200,000 ÷ 300 |
| Store upload duration | 5–8 minutes | Network + processing time |
| Concurrent uploading stores (peak) | 30–50 | Natural stagger across store opening times |
| Peak API calls/sec to rfid-ingest | ~100 req/sec | 50 stores × 667 batches ÷ 360 sec |

### 3.2 Sync Window

Stores upload during a natural 2–4 hour window aligned to opening time or end-of-day count. No coordinated scheduling is required — stagger is inherent.

```
6:00 AM  ──────────────────────────────────────────────── 10:00 AM
          │← 50 stores uploading concurrently (peak) →│
          │        ~100 API calls/sec                  │
          │  rfid-ingest handles, Kafka buffers,       │
          │  rfid-processing drains queue              │
```

### 3.3 Data Volume at Full Scale

| Table | Rows (400 Stores) | Retention | Est. Size |
|---|---|---|---|
| `epc_registry` | 80 million | Permanent | ~16 GB |
| `inventory_state` | 80 million | Permanent | ~16 GB |
| `epc_tags` (product-EPC map) | 80 million | Permanent | ~20 GB |
| `rfid_reads` (raw scan reads) | 5.2 billion | 90 days | ~780 GB |
| `soh_results` + `soh_variance` | 5.2 billion | 90 days | ~1 TB |
| `soh_sessions` | 104,000 sessions/year | 1 year | ~2 GB |
| `refill_tasks` + items | 200 million | 90 days | ~40 GB |
| Indexes (~50% overhead) | — | — | ~930 GB |
| **Total active DB size** | | | **~2.8–3 TB** |

> Partition `rfid_reads` and `soh_results` by month + `store_id`. Drop old partitions instead of DELETE — instant operation on 400M+ row tables.

---

## 4. Component Sizing

### 4.1 PostgreSQL

| Parameter | Specification |
|---|---|
| Instance class | 8 vCPU / 64 GB RAM |
| Deployment | Multi-AZ primary + 1 read replica |
| Storage | 4 TB, 6,000 IOPS provisioned (gp3 or equivalent) |
| IOPS profile | Burst to 16,000 during 2–4 hr sync window; ~300 IOPS idle |
| Read replica use | Reporting queries, KPI aggregation — never hits primary |
| Connection pooling | **PgBouncer required** — 11 services × up to 10 pods × 10 pool = 1,100+ raw connections without it |
| Partitioning | Range partition `rfid_reads`, `soh_results` by `(store_id, year_month)` |
| Backup | Daily snapshot + WAL archiving; 7-day PITR retention |

### 4.2 Redis

| Purpose | Key Pattern | Peak Size | TTL |
|---|---|---|---|
| EPC dedup (50 concurrent stores) | `rfid:dedup:{sessionId}:{epc}` | ~1 GB | Session + 5 min |
| Product/EPC lookup cache | `product:epc:{epc}` | ~25 MB | 30 min |
| SOH session state | `soh:session:{sessionId}` | ~5 MB | 24 hr |
| JWT blacklist | `jwt:blacklist:{jti}` | ~10 MB | Token expiry |
| Rate limit buckets | `ratelimit:{userId}` | ~5 MB | Rolling 60s |
| **Total working set** | | **~1.1 GB → budget 4 GB** | |

| Parameter | Specification |
|---|---|
| Size | 6 GB minimum, 13 GB recommended |
| Mode | Primary + 1 replica (no cluster needed for daily sync) |
| `maxmemory-policy` | `allkeys-lru` |
| Current setting | 512 MB — **must be increased before any production use** |

### 4.3 Kafka

Daily sync is a batch pattern. Kafka throughput is low (~2 MB/sec burst vs 25 MB/sec for real-time). Cluster is retained for decoupling and retry resilience.

| Topic | Peak Throughput | Partitions | Retention |
|---|---|---|---|
| `rfid.reads.raw` | ~2 MB/sec burst | 6 | 48 hr |
| `rfid.soh.updated` | ~500 KB/sec | 6 | 48 hr |
| `soh.session.updated` | ~50 KB/sec | 3 | 24 hr |
| `erp.product.sync` | Low | 3 | 24 hr |

| Parameter | Specification |
|---|---|
| Brokers | 3 (minimum for replication factor 3) |
| Broker size | 2 vCPU / 4 GB RAM each |
| Storage per broker | 200 GB |
| Replication factor | 3 (change from current default of 1) |

> **Alternative:** For teams without Kafka expertise, replace Kafka with a PostgreSQL-backed async job queue (Spring Batch + Quartz). Removes operational complexity at this throughput level. Migrate to Kafka only if real-time scanning is added later.

### 4.4 Application Pods (Kubernetes)

| Service | Min Pods | Max (HPA) | CPU/pod | Mem/pod | HPA Trigger |
|---|---|---|---|---|---|
| nginx-gateway | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| auth-service | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| store-service | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| product-service | 2 | 4 | 0.5 | 768 MB | CPU 70% |
| inventory-service | 2 | 6 | 1 | 1 GB | CPU 70% |
| soh-service | 2 | 8 | 1 | 1 GB | CPU 70% |
| refill-service | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| rfid-ingest-service | 2 | 6 | 1 | 1 GB | CPU 70% |
| rfid-processing-service | 3 | 8 | 1 | 1 GB | **Kafka lag > 5,000** |
| reporting-service | 2 | 4 | 1 | 2 GB | CPU 70% |
| erp-integration-service | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| notification-service | 2 | 4 | 0.5 | 512 MB | CPU 70% |
| frontend (Next.js) | 2 | 4 | 1 | 1 GB | CPU 70% |

**Baseline total:** ~18 CPU cores, ~22 GB RAM  
**Peak burst (upload window):** ~30 CPU cores, ~35 GB RAM

### 4.5 Kubernetes Node Groups

```
Cluster
└── General Pool
     3 × 8 vCPU / 32 GB nodes   (steady state)
     Scale to 5 nodes during upload window
     Cluster Autoscaler: trigger on pending pods

     All 13 services fit within 3 nodes at baseline.
     5 nodes absorbs full HPA burst for rfid-processing.
```

---

## 5. Indian Cloud Provider Pricing

Pricing as of June 2026. Exchange rate: **₹84 / USD**.

### 5.1 Component Pricing by Provider

| Component | AWS Mumbai (ap-south-1) | Azure India Central | GCP Mumbai (asia-south1) | E2E Networks |
|---|---|---|---|---|
| App nodes (3 × 8C/32G) | ₹78,708 | ₹81,043 | ₹49,527 | ₹28,500 |
| PostgreSQL HA + Replica | ₹56,448 | ₹75,432 | ₹53,424 | ₹28,000 ¹ |
| DB Storage (4 TB) | ₹46,368 | ₹34,608 | ₹30,240 | ₹16,000 |
| Redis HA | ₹22,680 | ₹24,696 | ₹12,012 | ₹2,500 ¹ |
| Kafka / Queue | ₹13,272 | ₹12,600 ² | ₹4,200 ³ | ₹10,500 ¹ |
| Object Storage + Transfer | ₹16,800 | ₹16,800 | ₹12,264 | ₹6,000 |
| LB + NAT + Cluster Fee | ₹20,748 | ₹14,700 | ₹18,984 | ₹5,000 |
| **On-demand / Pay-as-you-go** | **₹2,55,024** | **₹2,59,879** | **₹1,80,651** | **₹96,500** |
| **1-yr Reserved / Committed** | **₹1,85,000** | **₹1,95,000** | **₹1,38,000** | ₹96,500 |

> ¹ E2E: self-managed on VMs — no managed PostgreSQL, Redis, or Kafka  
> ² Azure Event Hubs Standard (Kafka-compatible, 10 Throughput Units)  
> ³ GCP Cloud Pub/Sub (priced per message — very cheap at daily sync scale)

### 5.2 Detailed Breakdown — AWS Mumbai (ap-south-1)

| Component | Spec | Rate (USD) | ₹/month |
|---|---|---|---|
| EKS cluster fee | 1 cluster | $0.10/hr | ₹6,132 |
| App nodes | 3 × m5.2xlarge (8C/32G) | $0.428/hr each | ₹78,708 |
| RDS PostgreSQL Multi-AZ | db.r6g.2xlarge (8C/64G) | $0.614/hr | ₹37,638 |
| RDS read replica | db.r6g.2xlarge | $0.307/hr | ₹18,809 |
| RDS storage | 4 TB gp3 | $0.138/GB-month | ₹46,368 |
| ElastiCache Redis HA | cache.r6g.large × 2 | $0.185/hr each | ₹22,680 |
| MSK Kafka | 3 × kafka.t3.small | $0.063/hr each | ₹13,272 |
| S3 + data transfer | ~2 TB | — | ₹16,800 |
| ALB + NAT Gateway | — | — | ₹14,700 |
| **On-demand Total** | | | **₹2,55,107** |
| **1-yr Reserved (RDS + Cache + Nodes)** | ~35% saving | | **~₹1,85,000** |

### 5.3 Detailed Breakdown — Azure India Central

| Component | Spec | Rate (USD) | ₹/month |
|---|---|---|---|
| AKS cluster fee | 1 cluster | Free | ₹0 |
| App nodes | 3 × Standard_D8s_v4 (8C/32G) | $0.441/hr each | ₹81,043 |
| PostgreSQL Flexible HA | D8ds_v4 HA | $0.822/hr | ₹50,321 |
| PostgreSQL read replica | D8ds_v4 | $0.411/hr | ₹25,154 |
| DB storage (3.5 TB extra) | Premium SSD | $0.115/GB-month | ₹34,608 |
| Azure Cache for Redis HA | C3 (6 GB) × 2 nodes | $0.201/hr each | ₹24,696 |
| Azure Event Hubs Standard | 10 TUs (Kafka-compatible) | Fixed | ₹12,600 |
| Blob Storage + transfer | ~2 TB | — | ₹16,800 |
| Load Balancer + NAT | — | — | ₹14,700 |
| **Pay-as-you-go Total** | | | **₹2,59,922** |
| **1-yr Reserved** | ~25% saving | | **~₹1,95,000** |

### 5.4 Detailed Breakdown — GCP Mumbai (asia-south1)

| Component | Spec | Rate (USD) | ₹/month |
|---|---|---|---|
| GKE cluster fee | Standard | $0.10/hr | ₹6,132 |
| App nodes | 3 × e2-standard-8 (8C/32G) | $0.2688/hr each | ₹49,527 |
| Cloud SQL PostgreSQL HA | db-n1-highmem-8 (8C/52G) | $0.608/hr | ₹37,325 |
| Cloud SQL read replica | db-n1-highmem-8 | $0.272/hr | ₹16,646 |
| Cloud SQL storage | 4 TB SSD | $0.09/GB-month | ₹30,240 |
| Memorystore Redis HA | 6 GB standard tier | $0.196/hr | ₹12,012 |
| Cloud Pub/Sub | Kafka replacement | Per-message | ₹4,200 |
| Cloud Storage + transfer | ~2 TB | $0.023/GB | ₹12,264 |
| Load Balancer + NAT | — | — | ₹12,852 |
| **On-demand Total** | | | **₹1,81,198** |
| **1-yr Committed Use (nodes + SQL)** | ~30% saving | | **~₹1,38,000** |

### 5.5 Detailed Breakdown — E2E Networks (Self-Managed)

E2E Networks (NSE: E2ENETWORKS) — India's largest listed cloud provider. No managed database, Redis, or Kafka services. All infrastructure self-managed on VMs.

| Component | Spec | ₹/month |
|---|---|---|
| App servers | 3 × 8 vCPU / 32 GB VMs | ₹28,500 |
| DB server | 1 × 16 vCPU / 128 GB VM | ₹28,000 |
| Kafka cluster | 3 × 4 vCPU / 8 GB VMs | ₹10,500 |
| Redis server | 1 × 2 vCPU / 4 GB VM | ₹2,500 |
| Block storage | 4 TB @ ₹4/GB | ₹16,000 |
| Object storage | 2 TB @ ₹2/GB | ₹4,000 |
| Bandwidth / egress | ~10 TB/month | ₹7,000 |
| **Total** | | **₹96,500** |

**What is NOT included (must be built and operated by your team):**

| Capability | AWS/Azure/GCP | E2E Networks |
|---|---|---|
| PostgreSQL Multi-AZ failover | Automatic < 60 sec | Manual or Patroni setup |
| Redis failover | Automatic | Redis Sentinel — self-configured |
| Kafka broker upgrades | Provider-managed | Manual rolling upgrade |
| Automated backups | Built-in, point-in-time restore | Custom cron + shell scripts |
| Kubernetes | Fully managed (EKS/AKS/GKE) | Docker Compose or self-managed k3s |
| SLA | 99.95–99.99% per component | 99.95% (compute only) |

E2E is recommended **only if** the team has a dedicated DevOps engineer with PostgreSQL HA (Patroni/repmgr), Redis Sentinel, and Kafka cluster management experience.

---

## 6. Deployment Architecture Diagram

```
                        ┌─────────────────────────────┐
                        │   400 Zebra Android Devices  │
                        │   (daily batch upload)       │
                        └──────────────┬──────────────┘
                                       │ HTTPS
                        ┌──────────────▼──────────────┐
                        │       Load Balancer          │
                        │  (ALB / Azure LB / GCP LB)  │
                        └──────────────┬──────────────┘
                                       │
                        ┌──────────────▼──────────────┐
                        │    Kubernetes Cluster        │
                        │    3–5 nodes (8C/32G each)  │
                        │                             │
                        │  nginx-gateway  (2 pods)    │
                        │  auth-service   (2 pods)    │
                        │  rfid-ingest    (2–6 pods)  │  ← HPA on CPU
                        │  rfid-process   (3–8 pods)  │  ← HPA on Kafka lag
                        │  soh-service    (2–8 pods)  │
                        │  refill-service (2 pods)    │
                        │  + 7 other services         │
                        │  frontend       (2–4 pods)  │
                        └──────────────┬──────────────┘
                                       │
               ┌───────────────────────┼──────────────────────┐
               │                       │                      │
┌──────────────▼──────┐  ┌─────────────▼───────┐  ┌──────────▼──────────┐
│     PostgreSQL       │  │       Redis          │  │       Kafka         │
│  Multi-AZ Primary    │  │   Primary + Replica  │  │   3-Broker Cluster  │
│  + Read Replica      │  │   4–6 GB             │  │   6 partitions      │
│  4 TB storage        │  │                      │  │   (rfid.reads.raw)  │
│  PgBouncer pooling   │  │                      │  │                     │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
                                       │
                        ┌──────────────▼──────────────┐
                        │    Object Storage (S3 / GCS) │
                        │    Reports, EPC archives     │
                        │    90-day → Glacier/Coldline │
                        └─────────────────────────────┘
```

---

## 7. Docker Compose Option (Interim — Up to 50 Stores)

If Kubernetes is not available for Phase 1, Docker Compose on two VMs handles up to ~50 stores with daily sync. Beyond 50 stores, Kubernetes is required for HPA scaling during the upload window.

### VM Sizing

| VM | Role | Spec | Est. Monthly Cost |
|---|---|---|---|
| VM-1 (App) | All 13 microservices + nginx + Redis + Kafka | 32 vCPU / 64 GB RAM / 500 GB SSD | ₹55,000–70,000 |
| VM-2 (DB) | PostgreSQL + PgBouncer | 16 vCPU / 128 GB RAM / 4 TB NVMe | ₹40,000–55,000 |
| **Total** | | | **₹95,000–1,25,000/month** |

### Required docker-compose.yml Changes

```yaml
redis:
  command: redis-server --maxmemory 4gb --maxmemory-policy allkeys-lru
  # Current 512mb is insufficient even for 10 stores

kafka:
  environment:
    KAFKA_NUM_PARTITIONS: "6"          # was 3
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"   # keep 1 for single-broker
    KAFKA_LOG_RETENTION_HOURS: "48"    # 2-day buffer for retry safety

pgbouncer:
  image: pgbouncer/pgbouncer:1.22
  environment:
    DATABASES_HOST: postgres
    PGBOUNCER_MAX_CLIENT_CONN: "500"
    PGBOUNCER_DEFAULT_POOL_SIZE: "25"
  ports:
    - "5433:5432"
# All Spring services point to pgbouncer:5432 instead of postgres:5432
```

---

## 8. Cost Summary

### Monthly Cost by Provider and Model

| Deployment Model | AWS Mumbai | Azure India | GCP Mumbai | E2E Networks |
|---|---|---|---|---|
| **Daily Sync — On-demand** | ₹2,55,000 | ₹2,60,000 | ₹1,81,000 | ₹96,500 |
| **Daily Sync — 1-yr Reserved** | ₹1,85,000 | ₹1,95,000 | ₹1,38,000 | ₹96,500 |
| **Real-Time — On-demand** | ₹10,50,000 | ₹10,80,000 | ₹8,20,000 | ₹3,50,000 |
| **Real-Time — 1-yr Reserved** | ₹7,20,000 | ₹7,80,000 | ₹5,60,000 | ₹3,50,000 |

### Annual Cost — Daily Sync (Recommended)

| Provider | Annual (Reserved) | Saving vs AWS |
|---|---|---|
| AWS Mumbai | ₹22,20,000 | — |
| Azure India | ₹23,40,000 | −₹1,20,000 |
| **GCP Mumbai** | **₹16,56,000** | **+₹5,64,000 saved** |
| E2E Networks | ₹11,58,000 | +₹10,62,000 saved |

---

## 9. Recommendation

### Recommended Configuration

| Layer | Choice | Spec |
|---|---|---|
| Cloud Provider | **GCP Mumbai (asia-south1)** | Best cost-to-managed-service ratio |
| App Tier | GKE Standard, 3–5 nodes | e2-standard-8 (8 vCPU / 32 GB) |
| Database | Cloud SQL PostgreSQL HA | db-n1-highmem-8, 4 TB SSD, read replica |
| Cache | Memorystore Redis HA | 6 GB standard tier |
| Messaging | Cloud Pub/Sub | Replaces Kafka; zero-ops, per-message billing |
| Storage | Cloud Storage | Standard → Coldline after 90 days |
| **Monthly cost (1-yr committed)** | | **~₹1,38,000/month** |
| **Annual cost** | | **~₹16,56,000/year** |

### Decision Matrix

| Criteria | AWS Mumbai | Azure India | GCP Mumbai | E2E Networks |
|---|---|---|---|---|
| Managed services | ✅ Full | ✅ Full | ✅ Full | ❌ Self-managed |
| Cost (daily sync, reserved) | ₹1.85L | ₹1.95L | **₹1.38L** | ₹0.97L |
| Kafka managed | ✅ MSK | ✅ Event Hubs | ✅ Pub/Sub | ❌ |
| Team familiarity | High | Medium | Medium | Low |
| MEITY empanelled | ✅ | ✅ | ✅ | ✅ |
| India data residency | ✅ | ✅ | ✅ | ✅ |
| Recommendation | If AWS-native team | If Microsoft EA | **Best value** | If strong DevOps |

### Key Actions Before Go-Live (Any Provider)

1. Increase Redis `maxmemory` from 512 MB to **4 GB minimum**
2. Increase Kafka partitions from 3 to **6 on `rfid.reads.raw`**
3. Set Kafka replication factor to **3** (currently 1 — single point of failure)
4. Add **PgBouncer** between application tier and PostgreSQL
5. Partition `rfid_reads` and `soh_results` tables by `(store_id, year_month)` before data exceeds 500M rows
6. Configure **Cluster Autoscaler** on Kubernetes to scale nodes during upload window
7. Enable daily automated database snapshots with **7-day point-in-time recovery**

---

## 10. Document History

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-06-07 | Initial release — daily sync sizing, Indian cloud pricing |
