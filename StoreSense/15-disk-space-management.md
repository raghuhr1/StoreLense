# 15 — Disk Space Management (100 GB Single Node)

**Target:** Keep StoreLense running indefinitely on a 100 GB disk with up to ~10 stores.
For 400 stores see the 300 GB guidance at the bottom.

---

## 1. Emergency Recovery (Server is Full Right Now)

Run these commands on the server in order. Each command is safe — no data loss.

```bash
# Step 1 — See where disk is gone
df -h /
du -sh /var/lib/docker/*

# Step 2 — Truncate all container logs (zero downtime, services keep running)
truncate -s 0 /var/lib/docker/containers/*/*-json.log

# Step 3 — Remove Docker build cache (safe — images still run, only cache gone)
docker builder prune -f

# Step 4 — Remove dangling/unused images from old builds
docker image prune -f

# Step 5 — Check recovered space
df -h /
```

Expected recovery: **20–60 GB** from steps 2–4 alone on a system that has been
running a few weeks without log limits.

---

## 2. Docker Log Limits (Permanent Fix — Applied to docker-compose.yml)

Without limits Docker writes JSON logs to `/var/lib/docker/containers/*/` forever.
At `INFO` level, 16 Spring Boot services generate ~5–15 MB/day each.

**Limits applied** (see [deploy/docker-compose.yml](../deploy/docker-compose.yml)):

```yaml
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "50m"    # rotate after 50 MB
    max-file: "3"      # keep 3 rotated files
```

This caps logs at **150 MB per container** × 16 containers = **2.4 GB max**.

After updating docker-compose.yml, apply with:

```bash
cd /opt/StoreLense-master/deploy
git pull
docker compose up -d   # recreates containers with new log config
```

---

## 3. PostgreSQL Data Retention

### 3a. SOH Session Items (Biggest Growth Table)

`soh.soh_session_items` grows ~1–3 GB/month per active store.
Purge sessions older than 90 days — KPI summaries in `reporting.kpi_daily`
already capture the aggregated accuracy, so raw session items are not needed.

```sql
-- Run manually or via cron. Safe to run while system is live.
BEGIN;

DELETE FROM soh.soh_session_items
WHERE session_id IN (
    SELECT id FROM soh.soh_sessions
    WHERE started_at < NOW() - INTERVAL '90 days'
      AND status = 'completed'
);

DELETE FROM soh.soh_sessions
WHERE started_at < NOW() - INTERVAL '90 days'
  AND status = 'completed';

COMMIT;

-- Reclaim freed pages (run after DELETE, brief table lock on small tables)
VACUUM ANALYZE soh.soh_sessions;
VACUUM ANALYZE soh.soh_session_items;
```

### 3b. KPI Daily (Keep 2 Years — Tiny)

`reporting.kpi_daily` is small (~100 MB for 400 stores × 2 years). No purge needed.

### 3c. EPC Registry vs EPC Tags

- `inventory.epc_registry` — operational, keep all
- `products.epc_tags` — reference data, keep all

---

## 4. Disk Budget for 100 GB (10 Stores)

| Component | Limit / Target | How It Is Controlled |
|---|---|---|
| OS + Docker engine | ~8 GB | Fixed |
| Docker images (all 16) | ~4 GB | Fixed; prune old with `docker image prune` |
| Docker build cache | ~3 GB | `docker builder prune -f` monthly |
| Container logs | **2.4 GB max** | `max-size: 50m, max-file: 3` in compose |
| Kafka data | ~1 GB | 24h retention already set |
| Redis data | <1 GB | In-memory; `maxmemory 512mb` in compose |
| **PostgreSQL — static** | ~2 GB | Products, EPCs, zones |
| **PostgreSQL — growing** | **≤ 20 GB** | 90-day SOH purge cron (see §3a) |
| PostgreSQL WAL + indexes | ~5 GB | Autovacuum keeps this stable |
| Buffer / headroom | **~54 GB free** | |
| **Total used** | **~46 GB** | |

---

## 5. Automated Maintenance Cron Jobs

Add these to crontab on the server (`crontab -e`):

```cron
# Purge old SOH session data — runs 2 AM every Sunday
0 2 * * 0  psql "postgresql://storelense_app:$DB_PASSWORD@localhost:5432/storelense" \
  -c "DELETE FROM soh.soh_session_items WHERE session_id IN (SELECT id FROM soh.soh_sessions WHERE started_at < NOW() - INTERVAL '90 days' AND status='completed');" \
  -c "DELETE FROM soh.soh_sessions WHERE started_at < NOW() - INTERVAL '90 days' AND status='completed';" \
  -c "VACUUM ANALYZE soh.soh_sessions;" \
  >> /var/log/storelense-purge.log 2>&1

# Docker build cache prune — runs 3 AM on the 1st of each month
0 3 1 * *  docker builder prune -f >> /var/log/storelense-docker-prune.log 2>&1

# Disk usage alert — runs 6 AM daily, emails if >80% used
0 6 * * *  USED=$(df / | awk 'NR==2{print $5}' | tr -d '%'); \
           [ "$USED" -gt 80 ] && echo "StoreLense server disk ${USED}% full" | \
           mail -s "ALERT: Disk usage ${USED}%" karthikeya.r@suveechi.in
```

---

## 6. Redis Memory Cap

Add to `redis` service in docker-compose.yml:

```yaml
  redis:
    image: redis:7-alpine
    command: redis-server --save 20 1 --loglevel warning --maxmemory 512mb --maxmemory-policy allkeys-lru
```

This caps Redis at 512 MB RAM and evicts least-recently-used keys when full
(JWT cache, product cache). No data loss — clients re-fetch on cache miss.

---

## 7. Monitor Disk Usage

```bash
# Quick check — run anytime
docker system df

# Per-volume sizes
docker system df -v | grep storelense

# PostgreSQL table sizes
psql -U storelense_app -d storelense -c "
  SELECT schemaname, tablename,
         pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
  FROM pg_tables
  WHERE schemaname IN ('soh','inventory','products','reporting','refill','stores')
  ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
  LIMIT 15;"
```

---

## 8. If You Scale to 400 Stores

| Store count | Recommended disk | Notes |
|---|---|---|
| Up to 10 stores | 100 GB | Apply all limits in this doc |
| Up to 50 stores | 200 GB | Reduce SOH retention to 60 days |
| Up to 400 stores | 300–500 GB | Separate SSD volume for PostgreSQL |

For 400 stores, mount PostgreSQL data on a dedicated volume:

```bash
# Move postgres_data volume to a larger disk
docker volume create --driver local \
  --opt type=none --opt o=bind \
  --opt device=/mnt/data-disk/postgres \
  storelense_postgres_data_ext
```

And add a read replica for reporting queries to offload the primary.

---

## 9. Quick Reference Card

```
Free space emergency:
  truncate -s 0 /var/lib/docker/containers/*/*-json.log   # instant, safe
  docker builder prune -f                                  # build cache
  docker image prune -f                                    # dangling images

Weekly check:
  df -h / && docker system df

Monthly:
  docker builder prune -f
  # SOH purge runs automatically via cron (§5)
```
