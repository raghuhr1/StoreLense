# Document 17 — Ubuntu 22.04 Deployment Guide

**Project:** StoreLense — RFID Store Operations Platform  
**Version:** 1.0  
**Date:** 2026-06-08  
**Target OS:** Ubuntu 22.04 LTS (Jammy Jellyfish)

---

## 1. Prerequisites

| Requirement | Minimum | Recommended |
|---|---|---|
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| CPU | 8 vCPU | 16–32 vCPU |
| RAM | 8 GB | 16–32 GB |
| Disk | 100 GB SSD | 500 GB SSD |
| Network | Public IP or LAN IP | Domain name + SSL |
| Ports open | 22 (SSH), 3000, 8080 | 22, 80, 443 |

> **RAM note:** The Next.js Docker build (`npm ci`) requires at least 4 GB available memory. The install script automatically creates a 4 GB swapfile if RAM is below threshold.

---

## 2. One-Command Install (Recommended)

SSH into the server as root or a sudo user and run:

```bash
curl -fsSL https://raw.githubusercontent.com/raghuhr1/StoreLense/master/deploy/install.sh | sudo bash
```

Or clone first and run locally:

```bash
sudo apt install -y git
git clone https://github.com/raghuhr1/StoreLense.git /opt/storelense
sudo bash /opt/storelense/deploy/install.sh
```

The script handles all 12 steps automatically. Installation takes **15–25 minutes** on first run (Docker image builds download Maven and npm dependencies).

---

## 3. Manual Step-by-Step Installation

Follow this section if you prefer to run each step manually or need to troubleshoot.

---

### Step 1 — System Update

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git jq openssl python3 python3-pip \
  ca-certificates gnupg lsb-release ufw htop net-tools
```

---

### Step 2 — Install Docker

```bash
# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine + Compose plugin
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

# Add your user to the docker group (log out and back in to apply)
sudo usermod -aG docker $USER

# Verify
docker --version
docker compose version
```

---

### Step 3 — Kernel Tuning

Required for Kafka (`vm.max_map_count`) and heavy file I/O (`nofile` limits).

```bash
# Apply immediately
sudo sysctl -w vm.max_map_count=262144

# Persist across reboots
echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.conf

# File descriptor limits
sudo tee /etc/security/limits.d/storelense.conf <<'EOF'
* soft nofile 65536
* hard nofile 65536
root soft nofile 65536
root hard nofile 65536
EOF
```

---

### Step 4 — Add Swap (if RAM < 4 GB)

```bash
# Check available RAM
free -h

# Create 4 GB swapfile if needed
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Make permanent
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

### Step 5 — Clone Repository

```bash
sudo git clone https://github.com/raghuhr1/StoreLense.git /opt/storelense
sudo chown -R $USER:$USER /opt/storelense
cd /opt/storelense/deploy
```

---

### Step 6 — Configure Environment

#### 6a. Generate secrets

```bash
# JWT secret (minimum 32 chars — use generated value)
openssl rand -hex 32

# Database password
openssl rand -base64 18 | tr -d '/+='
```

#### 6b. Create `.env`

```bash
cd /opt/storelense/deploy
cp .env.example .env
nano .env
```

Edit the following values — never use the defaults in production:

```ini
# PostgreSQL
POSTGRES_PASSWORD=<strong-password>      # postgres superuser
DB_PASSWORD=<strong-password>            # app user (storelense_app)
DB_USERNAME=storelense_app
DB_NAME=storelense

# Redis
REDIS_PASSWORD=                          # leave blank or set a password

# JWT — paste the openssl output here
JWT_SECRET=<64-char-hex-from-openssl>

# Internal service URLs — do not change
PRODUCT_SERVICE_URL=http://product-service:8083
STORE_SERVICE_URL=http://store-service:8082
INTERNAL_API_URL=http://nginx-gateway:8080
```

#### 6c. Patch `postgres/init.sql` with the DB password

The init SQL creates the `storelense_app` user with the password. It must match `DB_PASSWORD` in `.env`.

```bash
DB_PASS=$(grep ^DB_PASSWORD /opt/storelense/deploy/.env | cut -d= -f2)
sed -i "s/WITH PASSWORD 'changeme'/WITH PASSWORD '${DB_PASS}'/" \
  /opt/storelense/deploy/postgres/init.sql
```

#### 6d. Create `docker-compose.override.yml`

This overrides the Redis memory limit (default 512 MB is too low) and sets the WebSocket URL for the frontend.

```bash
SERVER_HOST="192.168.1.100"    # ← replace with your server IP or hostname

cat > /opt/storelense/deploy/docker-compose.override.yml <<EOF
services:
  redis:
    command: >
      redis-server
      --save 20 1
      --loglevel warning
      --maxmemory 4gb
      --maxmemory-policy allkeys-lru

  kafka:
    environment:
      KAFKA_NUM_PARTITIONS: "6"
      KAFKA_LOG_RETENTION_HOURS: "48"

  frontend:
    environment:
      NEXT_PUBLIC_API_BASE_URL: http://${SERVER_HOST}:8080
      NEXT_PUBLIC_WS_URL: ws://${SERVER_HOST}:8091/ws
      INTERNAL_API_URL: http://nginx-gateway:8080
EOF
```

---

### Step 7 — Build Docker Images

```bash
cd /opt/storelense/deploy
docker compose build
```

**Expected duration:** 10–20 minutes on first run (downloads Maven dependencies ~400 MB and npm packages ~300 MB).

On subsequent deploys, layer caching makes builds take 2–5 minutes.

---

### Step 8 — Start All Services

```bash
docker compose up -d
```

**Services started:**

| Container | Role | Port |
|---|---|---|
| postgres | PostgreSQL 16 | 5432 |
| redis | Redis 7 | 6379 |
| kafka | Apache Kafka 3.9 (KRaft) | 29092 |
| nginx-gateway | API Gateway | 8080 |
| auth-service | Authentication | 8081 |
| store-service | Store / Zone management | 8082 |
| product-service | Product master | 8083 |
| inventory-service | Inventory state | 8084 |
| soh-service | SOH sessions | 8085 |
| refill-service | Replenishment tasks | 8086 |
| rfid-ingest-service | RFID batch upload | 8087 |
| rfid-processing-service | EPC processing | 8088 |
| reporting-service | KPI reports | 8089 |
| erp-integration-service | ERP sync | 8090 |
| notification-service | WebSocket push | 8091 |
| frontend | Next.js web portal | 3000 |

---

### Step 9 — Verify All Services

Wait 3–4 minutes for JVM services to complete Flyway database migrations.

```bash
# All containers and their health status
docker compose ps

# Overall gateway health
curl -s http://localhost:8080/health | jq .

# Individual service health
for port in 8081 8082 8083 8084 8085 8086 8087 8088 8089 8090 8091; do
  STATUS=$(curl -sf http://localhost:${port}/actuator/health | jq -r '.status' 2>/dev/null || echo "DOWN")
  printf "  Port %s: %s\n" "$port" "$STATUS"
done

# Frontend
curl -sf http://localhost:3000 | head -5
```

**Expected output:**
```
{"status":"UP"}        ← gateway
{"status":"UP"}        ← each actuator/health
```

---

### Step 10 — Firewall

```bash
sudo ufw enable
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 3000/tcp   # Frontend
sudo ufw allow 8080/tcp   # API Gateway
sudo ufw reload
sudo ufw status
```

For HTTPS with a domain (see Section 5):
```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

---

### Step 11 — Systemd Service (Auto-Start on Reboot)

```bash
sudo tee /etc/systemd/system/storelense.service <<'EOF'
[Unit]
Description=StoreLense RFID Platform
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/storelense/deploy
ExecStart=/usr/bin/docker compose up -d --remove-orphans
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable storelense
```

**Manage the service:**

```bash
sudo systemctl start storelense    # start
sudo systemctl stop storelense     # stop
sudo systemctl restart storelense  # restart
sudo systemctl status storelense   # show status
```

---

### Step 12 — Seed Demo Data

```bash
# Seed P036 store demo data
bash /opt/storelense/tools/seed_demo_data.sh
```

---

## 4. Post-Deployment Verification

### Access URLs

| Resource | URL |
|---|---|
| Web Portal | `http://<SERVER_IP>:3000` |
| API Health | `http://<SERVER_IP>:8080/health` |
| Auth API | `http://<SERVER_IP>:8080/api/auth/login` |

### Quick API Test

```bash
# Test login (replace with your admin credentials)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq .

# List stores
TOKEN="<paste access token from above>"
curl -s http://localhost:8080/api/stores \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### View Logs

```bash
cd /opt/storelense/deploy

# All services
docker compose logs -f

# Specific service
docker compose logs -f auth-service
docker compose logs -f rfid-ingest-service

# Last 100 lines of a service
docker compose logs --tail=100 soh-service

# PostgreSQL queries
docker compose logs postgres
```

### Database Console

```bash
cd /opt/storelense/deploy

# Connect as superuser
docker compose exec postgres psql -U postgres -d storelense

# Connect as app user
docker compose exec postgres psql -U storelense_app -d storelense

# Useful queries
# \dt auth.*           — list auth tables
# \dt stores.*         — list store tables
# SELECT * FROM auth.users LIMIT 5;
```

---

## 5. Optional — HTTPS with Let's Encrypt

Install Nginx on the host as a reverse proxy with SSL termination.

```bash
sudo apt install -y nginx certbot python3-certbot-nginx

# Create Nginx site config
sudo tee /etc/nginx/sites-available/storelense <<'EOF'
server {
    listen 80;
    server_name yourdomain.com;

    # Frontend
    location / {
        proxy_pass         http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade $http_upgrade;
        proxy_set_header   Connection 'upgrade';
        proxy_set_header   Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    # API Gateway
    location /api/ {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket
    location /ws {
        proxy_pass         http://localhost:8091;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade $http_upgrade;
        proxy_set_header   Connection "Upgrade";
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/storelense /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Issue SSL certificate
sudo certbot --nginx -d yourdomain.com
```

After SSL is set up, update `docker-compose.override.yml` to use `https://` and `wss://` URLs, then rebuild the frontend:

```bash
cd /opt/storelense/deploy
docker compose build frontend
docker compose up -d frontend
```

---

## 6. Upgrade / Update

```bash
cd /opt/storelense

# Pull latest code
git pull

# Rebuild and restart changed services
cd deploy
docker compose build
docker compose up -d
```

---

## 7. Common Issues and Fixes

### Frontend build killed (OOM)

```
npm ERR! Killed
```

**Fix:** Add or increase swap:
```bash
sudo swapoff /swapfile 2>/dev/null; sudo fallocate -l 6G /swapfile
sudo chmod 600 /swapfile; sudo mkswap /swapfile; sudo swapon /swapfile
```

---

### Service stuck in "starting" with Flyway error

```
FlywayException: Unable to obtain Jdbc Connection
```

**Cause:** Service started before PostgreSQL was fully ready.  
**Fix:**
```bash
cd /opt/storelense/deploy
docker compose restart auth-service store-service
```

---

### Kafka container exits immediately

```
vm.max_map_count too low
```

**Fix:**
```bash
sudo sysctl -w vm.max_map_count=262144
```

---

### Redis out of memory

```
OOM command not allowed when used memory > maxmemory
```

**Fix:** Ensure `docker-compose.override.yml` is in place with `--maxmemory 4gb`. Then:
```bash
cd /opt/storelense/deploy
docker compose restart redis
```

---

### Cannot connect to port 3000 or 8080 from browser

**Fix:** Check UFW:
```bash
sudo ufw status
sudo ufw allow 3000/tcp
sudo ufw allow 8080/tcp
sudo ufw reload
```

---

### `docker compose` command not found

**Fix:** Install the compose plugin (not the standalone `docker-compose`):
```bash
sudo apt install -y docker-compose-plugin
```

---

### init.sql password mismatch — services can't connect to DB

**Symptom:** All Spring services fail with `password authentication failed for user "storelense_app"`  
**Cause:** PostgreSQL volume was created before init.sql was patched, or DB_PASSWORD in .env doesn't match init.sql.

**Fix (wipe and reinitialise — data loss!)**:
```bash
cd /opt/storelense/deploy
docker compose down -v            # -v removes volumes including postgres_data
# Re-patch init.sql:
DB_PASS=$(grep ^DB_PASSWORD .env | cut -d= -f2)
sed -i "s/WITH PASSWORD '.*'/WITH PASSWORD '${DB_PASS}'/" postgres/init.sql
docker compose up -d
```

---

## 8. File Locations Reference

| File | Purpose |
|---|---|
| `/opt/storelense/deploy/.env` | Passwords and secrets |
| `/opt/storelense/deploy/docker-compose.yml` | Main service definitions |
| `/opt/storelense/deploy/docker-compose.override.yml` | Server-specific overrides (Redis memory, Kafka partitions, frontend URLs) |
| `/opt/storelense/deploy/postgres/init.sql` | First-time DB schema + user setup |
| `/opt/storelense/deploy/nginx/nginx.conf` | API gateway routing config |
| `/etc/systemd/system/storelense.service` | Auto-start on reboot |
| `/root/storelense-credentials.txt` | Generated credentials (install.sh only) |

---

## 9. Document History

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-06-08 | Initial release — Ubuntu 22.04 manual + automated install |
