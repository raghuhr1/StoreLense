# StoreLense — Deployment Guide

> **One-command deployment** for Ubuntu 20.04 / 22.04 / 24.04 LTS.  
> The `deploy.sh` script installs every dependency, builds all Docker images, starts all services, and verifies the deployment end-to-end.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Configuration Prompts](#3-configuration-prompts)
4. [What the Script Does](#4-what-the-script-does)
5. [Verifying the Deployment](#5-verifying-the-deployment)
6. [Managing the Application](#6-managing-the-application)
7. [Troubleshooting](#7-troubleshooting)
8. [Re-deploying / Updating](#8-re-deploying--updating)

---

## 1. Prerequisites

| Requirement | Details |
|---|---|
| **OS** | Ubuntu 20.04, 22.04, or 24.04 LTS (64-bit) |
| **User** | A regular user account with `sudo` privileges — **do not run as root** |
| **CPU** | 4 cores minimum (8 recommended) |
| **RAM** | 8 GB minimum (16 GB recommended) |
| **Disk** | 40 GB free minimum (80 GB SSD recommended) |
| **Network** | Outbound internet access to pull Docker images and apt packages |
| **Ports** | 22, 3000, 8080, 29092 must not be in use |

The script installs everything else automatically (Docker, Docker Compose, PowerShell).

---

## 2. Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/raghuhr1/StoreLense.git
cd StoreLense

# 2. Make the script executable
chmod +x deploy.sh

# 3. Run the deployment
bash deploy.sh
```

The script will ask you 6 questions, then handle everything automatically.  
**First-time build takes 15–25 minutes** (downloading base images + compiling Java services).

---

## 3. Configuration Prompts

When you run `deploy.sh` you will be asked the following. Press **Enter** to accept the default shown in brackets.

| Prompt | Default | Description |
|---|---|---|
| **Server IP or domain** | Auto-detected from `hostname -I` | The IP or domain browsers will use to reach the app. Use your public IP for remote access. |
| **PostgreSQL superuser password** | `postgres` | Password for the internal `postgres` superuser. Not exposed externally. |
| **App DB password** | `changeme` | Password for the `storelense_app` database user used by all services. |
| **Redis password** | *(blank)* | Leave blank to disable Redis authentication (fine for single-server deployments). |
| **JWT secret** | Auto-generated (64 hex chars) | Secret key for signing authentication tokens. Must be at least 32 characters. Auto-generated if left blank. |
| **Enable ERP push** | `false` | Set to `true` only if you have an ERP system configured and ready to receive SOH data. |
| **Setup systemd auto-start** | `y` | Registers a systemd service so StoreLense starts automatically after server reboots. |

After answering, you will see a **Deployment Summary** and be asked to confirm before anything is installed.

### Example session

```
▶ Configuration

  Server IP or domain  [150.241.247.238]:
  PostgreSQL superuser password  [postgres]: MySecurePass123!
  App DB password (storelense_app)  [changeme]: AppPass456!
  Redis password (leave blank for none)  []:
  JWT secret (min 32 chars)  [auto-generated]:
  Enable ERP push (true/false)  [false]:
  Setup systemd auto-start on boot? (y/n)  [y]:

--- Deployment Summary ---
  Server IP/Domain : 150.241.247.238
  Frontend URL     : http://150.241.247.238:3000
  API Gateway URL  : http://150.241.247.238:8080
  ERP push         : false
  Systemd service  : y

Continue? (y/n) [y]: y
```

---

## 4. What the Script Does

The script runs 8 phases sequentially. It is safe to re-run — each phase is idempotent (skips things already done).

### Phase 1 — Configuration
Collects your answers and validates them (e.g. JWT secret length check). Nothing is written to disk yet.

### Phase 2 — Install Dependencies
Runs on every execution to ensure all tools are present.

| Tool | Version | Purpose |
|---|---|---|
| Docker Engine | Latest stable | Container runtime |
| Docker Compose plugin | v2 | Multi-container orchestration |
| PowerShell | Latest | Running the integration test script |
| curl, wget, git, gnupg | System | Supporting tools |
| python3 | System | Health-check status parsing |
| openssl | System | JWT secret generation |

**Apt repair is run first** to fix corrupted caches before any installation:
```
apt-get clean → rm /var/lib/apt/lists/* → dpkg --configure -a → apt --fix-broken
```
This handles the common `E: Method https has died unexpectedly! signal 4` error.

### Phase 3 — Write Configuration Files

**`deploy/.env`** is written with all your passwords and settings:
```
POSTGRES_PASSWORD, DB_PASSWORD, DB_USERNAME, DB_NAME
REDIS_PASSWORD, JWT_SECRET, KAFKA_BOOTSTRAP
ERP_PUSH_SOH_ENABLED, ERP_BASE_URL, ERP_API_KEY
PRODUCT_SERVICE_URL, STORE_SERVICE_URL, INTERNAL_API_URL
```

**`deploy/docker-compose.yml`** is patched to replace `localhost` with your server IP in the frontend environment:
```yaml
NEXT_PUBLIC_API_BASE_URL: http://<YOUR_IP>:8080
NEXT_PUBLIC_WS_URL:       ws://<YOUR_IP>:8091/ws
```

### Phase 4 — Configure Firewall (UFW)

Opens only the ports needed:

| Port | Service | Direction |
|---|---|---|
| 22 | SSH | Inbound |
| 3000 | Frontend Web UI | Inbound |
| 8080 | API Gateway | Inbound |
| 29092 | Kafka (Zebra RFID devices) | Inbound |

All other service ports (5432, 6379, 8081–8091) remain internal.

### Phase 5 — Build and Start Services

Runs:
```bash
docker compose up -d --build
```

This builds and starts all 15 containers:

| Container | Port | Role |
|---|---|---|
| `postgres` | 5432 (internal) | PostgreSQL 16 database |
| `redis` | 6379 (internal) | Cache + JWT blacklist |
| `kafka` | 9092 (internal), 29092 (external) | Event streaming |
| `auth-service` | 8081 | Login, tokens, users |
| `store-service` | 8082 | Store + zone management |
| `product-service` | 8083 | Product master + EPC lookup |
| `inventory-service` | 8084 | Inventory state + accuracy |
| `soh-service` | 8085 | Stock count sessions |
| `refill-service` | 8086 | Refill task management |
| `rfid-ingest-service` | 8087 | RFID batch ingest endpoint |
| `rfid-processing-service` | 8088 | EPC decode + deduplication |
| `reporting-service` | 8089 | KPI aggregation + reports |
| `erp-integration-service` | 8090 | ERP bidirectional sync |
| `notification-service` | 8091 | WebSocket push notifications |
| `nginx-gateway` | 8080 | API gateway + routing |
| `frontend` | 3000 | Next.js web portal |

### Phase 6 — Wait for Healthy Status

Polls all 15 containers every 10 seconds until all report `healthy`.  
Timeout: **5 minutes**. Infrastructure (postgres, redis, kafka) comes up first, then services, then the gateway and frontend last.

### Phase 7 — Smoke Tests

Runs 5 automated checks:

| Test | What it checks |
|---|---|
| Gateway `/health` | Nginx is up and returning 200 |
| Auth `actuator/health` | Spring Boot auth-service is up |
| `POST /api/auth/login` | Login returns a valid JWT token |
| `GET /api/stores` | Store API is accessible with auth |
| `GET /api/products` | Product API is accessible with auth |

### Phase 8 — Systemd Auto-Start

Creates `/etc/systemd/system/storelense.service` and enables it so that StoreLense starts automatically on every server reboot without manual intervention.

---

## 5. Verifying the Deployment

After the script finishes, open in your browser:

| URL | What you should see |
|---|---|
| `http://<SERVER_IP>:3000` | StoreLense login page |
| `http://<SERVER_IP>:8080/health` | `{"status":"UP"}` |

**Default login credentials:**

| Field | Value |
|---|---|
| Username | `admin` |
| Password | `Admin@StoreLense1` |

> **Change this password immediately** after first login via Users → Edit.

### Full integration test (optional)

After deployment, run the complete test suite:

```bash
cd StoreLense/deploy
pwsh ./test.ps1 -Gateway "http://localhost:8080"
```

Expected: `All tests PASSED` (11/11)

### Check all container statuses

```bash
cd StoreLense/deploy
docker compose ps
```

All containers must show `healthy`. If any show `unhealthy` or `starting`, wait another 60 seconds and check again.

---

## 6. Managing the Application

All management commands are run from the `deploy/` directory:

```bash
cd StoreLense/deploy
```

### Daily operations

```bash
# Check status of all containers
docker compose ps

# View live logs for all services
docker compose logs -f

# View logs for a specific service
docker compose logs -f auth-service
docker compose logs -f frontend
docker compose logs -f nginx-gateway

# Restart a single service (e.g. after changing .env)
docker compose restart product-service

# Stop all services (data is preserved)
docker compose down

# Stop all services AND wipe all data (DESTRUCTIVE)
docker compose down -v
```

### Scaling

Scale the RFID processing service for high scan load:

```bash
docker compose up -d --scale rfid-processing-service=3
```

### Systemd control

```bash
sudo systemctl start storelense      # start
sudo systemctl stop storelense       # stop
sudo systemctl restart storelense    # restart
sudo systemctl status storelense     # check status
sudo systemctl disable storelense    # disable auto-start
```

### Check disk usage

```bash
docker system df                     # Docker image/volume usage
df -h /                              # Server disk usage
```

---

## 7. Troubleshooting

### apt HTTPS error during install

```
E: Method https has died unexpectedly!
E: Sub-process https received signal 4.
```

**Fix:**
```bash
sudo apt-get clean
sudo rm -rf /var/lib/apt/lists/*
sudo dpkg --configure -a
sudo apt-get install --fix-broken -y
sudo apt-get update
```
Then re-run `bash deploy.sh`. The script now runs this repair automatically.

---

### A container is stuck in `starting` or `unhealthy`

```bash
# See what went wrong
docker compose logs <service-name> --tail=50

# Example: auth-service failing
docker compose logs auth-service --tail=50
```

Common causes:

| Symptom in logs | Cause | Fix |
|---|---|---|
| `Connection refused` to postgres | Postgres not ready yet | Wait 30s and retry |
| `FlywayException: migration failed` | Bad SQL or schema conflict | `docker compose down -v` then re-run deploy |
| `JWT secret too short` | JWT_SECRET under 32 chars | Edit `deploy/.env`, restart service |
| `Could not resolve host kafka` | Kafka not ready | Wait for kafka to be `healthy` first |

---

### Cannot reach the app from a browser

```bash
# Confirm UFW allows the ports
sudo ufw status verbose

# Confirm containers are binding on all interfaces (0.0.0.0)
docker compose ps
# Port column must show: 0.0.0.0:3000->3000/tcp (not 127.0.0.1:3000)

# Check your server IP
hostname -I
```

If behind a cloud provider (AWS, GCP, Azure, DigitalOcean): also open ports 3000 and 8080 in the **cloud firewall / security group**, not just UFW.

---

### Login returns 401 / invalid credentials

The admin account is seeded from SQL migrations on first boot. If migrations did not run:
```bash
docker compose logs auth-service | grep -i "flyway\|migration\|error"
```

If migrations failed, wipe the database and restart:
```bash
docker compose down -v
docker compose up -d --build
```

---

### Out of memory — containers crashing

```bash
free -h   # check available RAM
```

If RAM is under 8 GB, add swap:
```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

### Docker not found after install

If you get `docker: command not found` after the script runs, your shell session predates the group change:
```bash
newgrp docker
# or log out and back in
```

---

### Port already in use

```bash
sudo ss -tlnp | grep -E '3000|8080|5432|6379|9092'
```

Stop the conflicting process or change the port mapping in `deploy/docker-compose.yml`.

---

## 8. Re-deploying / Updating

To deploy a new version of the code:

```bash
cd StoreLense

# Pull latest code
git pull

# Rebuild and restart all services (preserves data)
cd deploy
docker compose up -d --build

# Or to fully reset (wipes all database data)
docker compose down -v
docker compose up -d --build
```

To change passwords or the JWT secret after deployment:

```bash
nano deploy/.env          # edit the values
docker compose up -d      # restart all services to pick up new env
```

To change the server IP (e.g. after moving to a new host):

```bash
# Edit docker-compose.yml directly
nano deploy/docker-compose.yml
# Update NEXT_PUBLIC_API_BASE_URL and NEXT_PUBLIC_WS_URL

docker compose up -d frontend   # only frontend needs rebuilding
```

---

## Port Reference

| Port | Service | Exposed |
|---|---|---|
| **3000** | Frontend (Next.js Web UI) | Public |
| **8080** | Nginx API Gateway | Public |
| **29092** | Kafka (Zebra RFID devices) | Zebra devices only |
| 8081 | auth-service | Internal |
| 8082 | store-service | Internal |
| 8083 | product-service | Internal |
| 8084 | inventory-service | Internal |
| 8085 | soh-service | Internal |
| 8086 | refill-service | Internal |
| 8087 | rfid-ingest-service | Internal |
| 8088 | rfid-processing-service | Internal |
| 8089 | reporting-service | Internal |
| 8090 | erp-integration-service | Internal |
| 8091 | notification-service (WebSocket) | Internal |
| 5432 | PostgreSQL | Internal |
| 6379 | Redis | Internal |

---

*For application usage instructions see the [User Guide](StoreSense/02-prd.md).*  
*For API reference see [API Specification](StoreSense/05-api-specification.md).*
