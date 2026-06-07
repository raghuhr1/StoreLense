# 16 — Observability Stack (Support Team Guide)

**Stack:** Prometheus · Grafana · Loki · Promtail · cAdvisor · Node Exporter · PostgreSQL Exporter
**URL:** `http://<server-ip>:3001` → Grafana (admin login)
**Purpose:** Unified visibility into service health, disk, memory, errors, and logs for the L1/L2 support team.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  StoreLense App Stack (storelense_default network)              │
│  auth · store · product · inventory · soh · refill              │
│  rfid-ingest · rfid-processing · reporting · erp · notification │
│  → each exposes /actuator/prometheus (micrometer built-in)      │
└──────────────────────┬──────────────────────────────────────────┘
                       │ scrape every 30s
┌──────────────────────▼──────────────────────────────────────────┐
│  Observability Stack (storelense-obs)                           │
│                                                                 │
│  Prometheus ──────────────────────► Grafana (port 3001)        │
│  (metrics store, 15d retention)      (dashboards + alerts)     │
│                                           ▲                    │
│  Loki ◄── Promtail                        │                    │
│  (log store)  (Docker log reader)    ─────┘                    │
│                                                                 │
│  Node Exporter  (host CPU/RAM/disk)                             │
│  cAdvisor       (container CPU/RAM/net per container)           │
│  Postgres Exporter (DB connections, size, queries)              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. Deploy (First Time)

```bash
cd /opt/StoreLense-master/deploy

# Copy env template
cp .env.observability.example .env.observability

# Start the observability stack
# Note: main app stack must be running first (needs storelense_default network)
docker compose -f docker-compose.observability.yml --env-file .env --env-file .env.observability up -d

# Check all services started
docker compose -f docker-compose.observability.yml ps
```

Expected containers:
```
storelense-obs-prometheus-1      Running   :9090
storelense-obs-grafana-1         Running   :3001
storelense-obs-loki-1            Running   :3100
storelense-obs-promtail-1        Running
storelense-obs-node-exporter-1   Running   :9100
storelense-obs-cadvisor-1        Running   :9082
storelense-obs-postgres-exporter-1 Running
```

**Open Grafana:** `http://<server-ip>:3001`
- Username: `admin`
- Password: value of `GRAFANA_PASSWORD` in `.env.observability` (default: `StoreLense@Obs1`)

---

## 2. Import Community Dashboards (one-time, 5 min)

The StoreLense Overview dashboard loads automatically. For deeper infrastructure views, import these from Grafana.com:

| Dashboard | ID | What It Shows |
|---|---|---|
| Node Exporter Full | **1860** | Host CPU, RAM, disk, network |
| Docker cAdvisor | **14282** | Per-container CPU, RAM, network |
| PostgreSQL | **9628** | DB queries, connections, table sizes |
| JVM (Micrometer) | **4701** | Per-service heap, GC, threads |

**How to import:**
1. Grafana → Dashboards → Import
2. Enter the ID number → Load
3. Select `Prometheus` as the data source → Import

---

## 3. StoreLense Overview Dashboard

Auto-loaded at Grafana startup. Panels:

| Panel | What It Shows | Alert Threshold |
|---|---|---|
| Services UP / DOWN | Which microservices are reachable | Any DOWN = critical |
| Disk Used % | Fill % per mount (select `/opt` from dropdown) | >80% warning, >90% critical |
| PostgreSQL Connections | Active DB connections vs max | >80% of max_connections |
| Active Alerts | Count of firing Prometheus rules | Any = look at Alerting tab |
| Service Status table | Up/down per service name | Red row = service down |
| HTTP Request Rate | req/s per service | Reference only |
| JVM Heap % | Heap usage per service | >85% warning |
| Container Memory | RAM per container | Reference only |
| 5xx Error Rate | Server errors per service | >0.5/s = warning |
| p95 Response Time | Slowness per service | >3s = warning |
| Disk Free trend | Available space over time | Downward trend = action needed |
| Error Logs (live) | Last ERROR/WARN log lines across all services | Use to diagnose alerts |

---

## 4. Setting Up Email Alerts

### Option A — Gmail App Password (simplest)

1. Enable 2FA on your Gmail account
2. Create an App Password: Google Account → Security → App Passwords
3. Edit `.env.observability`:
   ```dotenv
   GRAFANA_SMTP_ENABLED=true
   GRAFANA_SMTP_HOST=smtp.gmail.com:587
   GRAFANA_SMTP_USER=karthikeya.r@suveechi.in
   GRAFANA_SMTP_PASSWORD=<16-char-app-password>
   GRAFANA_SMTP_FROM=karthikeya.r@suveechi.in
   ```
4. Restart Grafana:
   ```bash
   docker compose -f docker-compose.observability.yml restart grafana
   ```

### Option B — Configure in Grafana UI (no restart needed)

Grafana → Alerting → Contact Points → Add Contact Point → Email
Enter recipient email → Test → Save

Then create a Notification Policy to route all alerts to this contact point.

---

## 5. Alert Rules (Pre-configured in Prometheus)

| Alert | Condition | Severity |
|---|---|---|
| `ServiceDown` | Service unreachable > 1 min | Critical |
| `ServiceRestarting` | Container restarting in last 5 min | Warning |
| `DiskSpaceCritical` | Any mount > 90% full | Critical |
| `DiskSpaceWarning` | Any mount > 80% full | Warning |
| `DockerVolumeGrowing` | /opt loses > 1 GB in 1 hour | Warning |
| `JvmHeapHigh` | Heap > 85% for 5 min | Warning |
| `JvmHeapCritical` | Heap > 95% for 2 min | Critical |
| `HighErrorRate` | > 0.5 5xx errors/s for 3 min | Warning |
| `SlowResponses` | p95 > 3s for 5 min | Warning |
| `PostgresDown` | Cannot connect to DB | Critical |
| `PostgresHighConnections` | Connections > 80% of max | Warning |
| `ContainerHighMemory` | Container memory > 90% of limit | Warning |

View and silence alerts: Grafana → Alerting → Alert Rules (for Grafana-native) or `http://<ip>:9090/alerts` (Prometheus raw).

---

## 6. Searching Logs

Grafana → Explore → Select `Loki` data source

**Common queries:**

```logql
# All ERROR logs across all services
{compose_project="storelense", level="ERROR"}

# Errors from one service
{service="inventory-service"} |= "ERROR"

# Full-text search across all services
{compose_project="storelense"} |= "NullPointerException"

# Last 50 log lines from a service
{service="rfid-processing-service"} | limit 50

# Logs containing an EPC or store code
{compose_project="storelense"} |= "P004"

# Filter by time + keyword
{compose_project="storelense"} |= "ROLLBACK" | json
```

---

## 7. Disk Space: Quick Response Runbook

When `DiskSpaceCritical` fires:

```bash
# Step 1 — Check what's using space
docker system df
du -sh $(docker info --format '{{.DockerRootDir}}')/containers/* | sort -rh | head -5

# Step 2 — Truncate logs (safe, instant, zero downtime)
truncate -s 0 $(docker info --format '{{.DockerRootDir}}')/containers/*/*-json.log

# Step 3 — Check recovery
df -h

# Step 4 — If still low, clear build cache
docker builder prune -f

# Step 5 — Run DB purge (cleans SOH session items > 90 days)
docker exec storelense-postgres-1 psql -U storelense_app storelense -c \
  "DELETE FROM soh.soh_session_items WHERE session_id IN (SELECT id FROM soh.soh_sessions WHERE started_at < NOW() - INTERVAL '90 days' AND status='completed');"
```

---

## 8. Disk Usage of Observability Stack

| Component | Max Disk | Configured By |
|---|---|---|
| Prometheus | 2 GB | `--storage.tsdb.retention.size=2GB` |
| Loki | ~1 GB | `retention_period: 168h` (7 days) |
| Grafana | ~100 MB | Dashboards + user prefs |
| Images (all obs containers) | ~1.5 GB | One-time pull |
| Logs (obs containers) | ~100 MB | `max-size: 20m, max-file: 2` |
| **Total** | **~5 GB** | Stable — does not grow unboundedly |

---

## 9. Update / Restart Observability Stack

```bash
cd /opt/StoreLense-master/deploy

# Pull latest config
git pull

# Restart with updated config
docker compose -f docker-compose.observability.yml --env-file .env --env-file .env.observability up -d

# Stop observability (app stack continues running)
docker compose -f docker-compose.observability.yml down

# Full reset (deletes all metrics/log history)
docker compose -f docker-compose.observability.yml down -v
```
