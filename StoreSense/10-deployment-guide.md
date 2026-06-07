# Deployment Guide

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.1
**Date:** 2026-06-06

---

## 1. Prerequisites

### Server Requirements (Minimum)

| Resource | Minimum | Recommended |
|---|---|---|
| CPU | 4 vCPU | 8 vCPU |
| RAM | 8 GB | 16 GB |
| Disk | 40 GB SSD | 100 GB SSD |
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| Network | 100 Mbps | 1 Gbps |

### Required Software

```bash
# Docker Engine 24+
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # logout and login after this

# Docker Compose Plugin (comes with modern Docker Desktop or Engine)
docker compose version   # should show 2.x

# Git
sudo apt-get install git -y

# jq (used by seed scripts)
sudo apt-get install jq -y
```

---

## 2. First-Time Deployment

### Step 1 — Clone the Repository

```bash
cd /opt
sudo git clone https://github.com/raghuhr1/StoreLense.git StoreLense-master
sudo chown -R $USER:$USER StoreLense-master
cd StoreLense-master
```

### Step 2 — Create Environment File

```bash
cd deploy
cp .env.example .env    # if example exists; otherwise create manually
nano .env
```

**Minimum required `.env` content:**

```dotenv
# Database
DB_NAME=storelense
DB_USERNAME=storelense_app
DB_PASSWORD=YourStrongPassword123!
POSTGRES_PASSWORD=YourPostgresPassword123!

# Redis (leave empty to disable auth in dev)
REDIS_PASSWORD=

# JWT — minimum 32 characters, random
JWT_SECRET=change-this-to-a-random-32-char-secret!!

# ERP Integration
ERP_BASE_URL=http://erp.yourdomain.com/api
ERP_API_KEY=your-erp-api-key
ERP_API_VERSION=v1
ERP_PUSH_SOH_ENABLED=false
```

> **Security:** Never commit `.env` to git. It is already in `.gitignore`.

### Step 3 — Build and Start All Services

```bash
cd /opt/StoreLense-master/deploy

# First-time: build all images and start
docker compose up -d --build

# Watch startup progress
watch -n 10 'docker compose ps --format "table {{.Name}}\t{{.Status}}"'
```

**Expected startup sequence:**
1. `postgres`, `redis`, `kafka` → healthy within ~30 seconds
2. Spring Boot services (`auth-service`, `store-service`, etc.) → healthy within **3–4 minutes** (Flyway migrations run on first start)
3. `nginx-gateway` → starts immediately (lazy DNS)
4. `frontend` → healthy within ~60 seconds after nginx starts

### Step 4 — Verify All Services Running

```bash
docker compose ps
```

All 16 containers should show `(healthy)` or `Up`. If any show `(unhealthy)` after 5 minutes, check logs:

```bash
docker compose logs <service-name> --tail=50
```

### Step 5 — Verify Gateway and Login

```bash
# Check nginx health
curl -s http://localhost:8080/health
# Expected: {"status":"UP"}

# Test admin login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' | python3 -m json.tool
# Expected: { "success": true, "data": { "accessToken": "eyJ..." } }
```

### Step 6 — Seed Demo Store Data

Two seeding options are available:

#### Option A — Fixed Demo Seed (Pantaloons P037)

```bash
bash /opt/StoreLense-master/tools/seed_pantaloons_p037.sh \
  --url http://localhost:8080
```

Creates: Store P037 (Jaipur), zones, 100+ products, 1067 EPC registrations, demo users.

> Takes ~20 minutes (REST API calls per EPC). Auto re-logins to handle JWT expiry.

#### Option B — Real XLS Data (Recommended for Production)

Requires `openpyxl`: `pip install openpyxl`

```bash
# SQL bulk mode — seconds instead of hours (recommended)
python3 /opt/StoreLense-master/tools/seed_from_xls.py --sql \
    --dir /path/to/XLS_FILES \
    --store-code P036 \
    --store-name "Pantaloons P036" \
    --url http://localhost:8080 \
    --pg-container deploy-postgres-1

# Write SQL to file first (for review before execution):
python3 /opt/StoreLense-master/tools/seed_from_xls.py --sql \
    --sql-out /tmp/seed_p036.sql \
    --dir /path/to/XLS_FILES \
    --store-code P036
cat /tmp/seed_p036.sql | docker exec -i deploy-postgres-1 psql -U postgres -d storelense

# REST mode (no direct DB access required — slow on large datasets):
python3 /opt/StoreLense-master/tools/seed_from_xls.py \
    --dir /path/to/XLS_FILES \
    --store-code P036 \
    --url http://localhost:8080 \
    --workers 3
```

**XLS file naming convention:** files should include the date in `YYYYMMDD` format (e.g. `P036_20260105.xlsx`) — the seeder extracts it as the SOH session date.

The seeder creates one SOH session per XLS file (representing one cycle-count day), loads all products and EPC tags, and sets inventory state from the most recent file.

### Step 7 — Access the Web Portal

Navigate to `http://<server-ip>:3000` and log in as `admin` / `Admin@StoreLense1`.

---

## 3. Updating to a New Version

### Standard Update (git pull + rebuild)

```bash
cd /opt/StoreLense-master

# Pull latest code
git pull origin master

# Rebuild and restart ALL services (includes migrations)
docker compose -f deploy/docker-compose.yml up -d --build

# Or rebuild specific services only
docker compose -f deploy/docker-compose.yml up -d --build auth-service frontend
```

### Zero-Downtime Consideration

With Docker Compose (single VM), there is brief downtime during `up -d --build` as containers are replaced. For production with multiple replicas, use Kubernetes rolling updates.

### After Pull — Check for Breaking Changes

```bash
git log --oneline HEAD~5..HEAD   # see recent commits
git diff HEAD~1 deploy/docker-compose.yml   # check for infra changes
```

---

## 4. Stopping and Restarting

### Restart Without Losing Data

```bash
# Stop all (keeps volumes)
docker compose -f deploy/docker-compose.yml down

# Start again
docker compose -f deploy/docker-compose.yml up -d
```

### Full Reset (Wipe All Data)

> **Warning:** This deletes all database data, Kafka topics, and Redis cache.

```bash
docker compose -f deploy/docker-compose.yml down -v
docker compose -f deploy/docker-compose.yml up -d --build
```

After a full reset, re-run the seed script.

---

## 5. Individual Service Management

```bash
# Restart a single service
docker compose -f deploy/docker-compose.yml restart auth-service

# Rebuild and restart a single service
docker compose -f deploy/docker-compose.yml up -d --build erp-integration-service

# View logs (live)
docker compose -f deploy/docker-compose.yml logs -f auth-service

# View last 100 lines
docker compose -f deploy/docker-compose.yml logs auth-service --tail=100

# Scale rfid-processing-service (Kafka consumer scale-out)
docker compose -f deploy/docker-compose.yml up -d --scale rfid-processing-service=2
```

---

## 6. Environment Variables Reference

### Shared Variables (all Spring Boot services)

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `postgres` | PostgreSQL hostname (Docker service name) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `storelense` | Database name |
| `DB_USERNAME` | `storelense_app` | App DB user |
| `DB_PASSWORD` | `changeme` | App DB password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis auth password |
| `KAFKA_BOOTSTRAP` | `kafka:9092` | Kafka bootstrap address |
| `JWT_SECRET` | `storelense-dev-secret-key-minimum-32-chars` | JWT signing secret |
| `PRODUCT_SERVICE_URL` | `http://product-service:8083` | Product service URL (for internal calls) |
| `STORE_SERVICE_URL` | `http://store-service:8082` | Store service URL (for internal calls) |

### ERP Integration Variables

| Variable | Default | Description |
|---|---|---|
| `ERP_BASE_URL` | `http://localhost:9000` | ERP REST API base URL |
| `ERP_API_KEY` | _(empty)_ | ERP API authentication key |
| `ERP_API_VERSION` | `v1` | ERP API version prefix |
| `ERP_PUSH_SOH_ENABLED` | `false` | Enable outbound SOH push to ERP |

---

## 7. Port Reference

| Port (External) | Service | Protocol |
|---|---|---|
| 3000 | Frontend (Next.js) | HTTP |
| 8080 | nginx API Gateway | HTTP |
| 8081 | auth-service | HTTP |
| 8082 | store-service | HTTP |
| 8083 | product-service | HTTP |
| 8084 | inventory-service | HTTP |
| 8085 | soh-service | HTTP |
| 8086 | refill-service | HTTP |
| 8087 | rfid-ingest-service | HTTP |
| 8088 | rfid-processing-service | HTTP |
| 8089 | reporting-service | HTTP |
| 8090 | erp-integration-service | HTTP |
| 8091 | notification-service | HTTP + WebSocket |
| 5432 | PostgreSQL | TCP |
| 6379 | Redis | TCP |
| 29092 | Kafka (external listener) | TCP |

**Firewall rules:** In production, expose only ports 3000 and 8080 externally. All other ports should be accessible only from within the VM or a VPN.

---

## 8. Database Management

### Connect to PostgreSQL

```bash
# From host
docker exec -it storelense-postgres-1 psql -U postgres -d storelense

# List schemas
\dn

# Connect to auth schema
SET search_path = auth;
\dt
```

### Manual Backup

```bash
# Full backup
docker exec storelense-postgres-1 pg_dump -U postgres storelense > backup_$(date +%Y%m%d).sql

# Restore
docker exec -i storelense-postgres-1 psql -U postgres storelense < backup_20260606.sql
```

### Check Flyway Migration Status

Each service uses Flyway — migration history visible in:
```sql
SELECT * FROM auth.flyway_schema_history ORDER BY installed_rank;
```
(Replace `auth.` with the service's schema name.)

---

## 9. SSL/TLS Setup (Production)

For production, place a reverse proxy (nginx or Caddy) in front of StoreLense with TLS termination:

**Caddy example (`/etc/caddy/Caddyfile`):**
```
storelense.yourdomain.com {
    reverse_proxy localhost:8080
}

app.storelense.yourdomain.com {
    reverse_proxy localhost:3000
}
```

Or configure nginx with Let's Encrypt certificates:
```nginx
server {
    listen 443 ssl;
    server_name storelense.yourdomain.com;
    ssl_certificate /etc/letsencrypt/live/storelense.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/storelense.yourdomain.com/privkey.pem;
    location / { proxy_pass http://localhost:8080; }
}
```

---

## 10. Health Check URLs

Use these to verify services are running:

```bash
# nginx gateway
curl -s http://localhost:8080/health

# Individual services (internal)
curl -s http://localhost:8081/actuator/health   # auth-service
curl -s http://localhost:8082/actuator/health   # store-service
curl -s http://localhost:8083/actuator/health   # product-service
curl -s http://localhost:8084/actuator/health   # inventory-service
curl -s http://localhost:8085/actuator/health   # soh-service
curl -s http://localhost:8086/actuator/health   # refill-service
curl -s http://localhost:8087/actuator/health   # rfid-ingest-service
curl -s http://localhost:8088/actuator/health   # rfid-processing-service
curl -s http://localhost:8089/actuator/health   # reporting-service
curl -s http://localhost:8090/actuator/health   # erp-integration-service
curl -s http://localhost:8091/actuator/health   # notification-service

# Frontend
curl -s http://localhost:3000
```

---

## 11. Upgrade Checklist

Before each upgrade:

- [ ] Review commit log: `git log --oneline <current-commit>..origin/master`
- [ ] Check for `docker-compose.yml` changes: `git diff <current-commit> deploy/docker-compose.yml`
- [ ] Back up PostgreSQL: `docker exec storelense-postgres-1 pg_dump -U postgres storelense > pre_upgrade_backup.sql`
- [ ] Notify users of expected downtime window
- [ ] Pull and rebuild: `git pull && docker compose -f deploy/docker-compose.yml up -d --build`
- [ ] Verify all services healthy: `docker compose ps`
- [ ] Test login and basic API calls
- [ ] Monitor logs for 10 minutes: `docker compose logs -f --tail=20`

---

## 12. Rollback Procedure

### If Upgrade Fails

```bash
# Find the previous commit
git log --oneline -5

# Reset to previous commit
git checkout <previous-commit-hash>

# Restore DB backup
docker exec -i storelense-postgres-1 psql -U postgres storelense < pre_upgrade_backup.sql

# Rebuild from previous commit
docker compose -f deploy/docker-compose.yml up -d --build

# Verify
docker compose ps
curl -s http://localhost:8080/health
```

---

## 13. Kubernetes Deployment (Phase 2 Reference)

Kubernetes is planned for Phase 2 (110+ stores). Key considerations:

- **HPA on rfid-processing-service** — scale based on Kafka consumer lag (KEDA metric)
- **ConfigMaps** for application.yml; **Secrets** for DB credentials and JWT secret
- **PersistentVolumeClaims** for PostgreSQL and Kafka data
- **Ingress** (nginx ingress controller) replaces the Docker nginx gateway
- **Readiness probes** → `/actuator/health/readiness`; **Liveness probes** → `/actuator/health/liveness`
- Spring Boot services expose both via Spring Boot Actuator (3.x)

Sample HPA for rfid-processing-service:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: rfid-processing-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: rfid-processing-service
  minReplicas: 1
  maxReplicas: 10
  metrics:
  - type: External
    external:
      metric:
        name: kafka_consumergroup_lag
        selector:
          matchLabels:
            topic: rfid.reads.raw
      target:
        type: AverageValue
        averageValue: 1000
```
