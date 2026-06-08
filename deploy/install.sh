#!/usr/bin/env bash
# =============================================================================
#  StoreLense — Fresh Ubuntu 22.04 LTS Install Script
#  Usage:  sudo bash install.sh
#  Tested: Ubuntu 22.04 LTS (Jammy)
# =============================================================================
set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

log()  { echo -e "${GREEN}[OK]${NC}    $*"; }
info() { echo -e "${BLUE}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
step() { echo -e "\n${BOLD}${BLUE}━━━━━━ $* ━━━━━━${NC}"; }

# ── Pre-flight ────────────────────────────────────────────────────────────────
[[ "$EUID" -ne 0 ]] && err "Run with sudo:  sudo bash install.sh"

OS_ID=$(lsb_release -si 2>/dev/null || echo "")
OS_CS=$(lsb_release -cs 2>/dev/null || echo "")
[[ "$OS_ID" != "Ubuntu" ]]  && warn "Script tested on Ubuntu only (detected: ${OS_ID})"
[[ "$OS_CS" != "jammy"  ]]  && warn "Script tested on Ubuntu 22.04 jammy (detected: ${OS_CS})"

INSTALL_DIR=/opt/storelense
REPO_URL=https://github.com/raghuhr1/StoreLense.git
CREDS_FILE=/root/storelense-credentials.txt

# ── RAM check ─────────────────────────────────────────────────────────────────
TOTAL_RAM_MB=$(awk '/MemTotal/{printf "%d", $2/1024}' /proc/meminfo)
[[ $TOTAL_RAM_MB -lt 7680 ]] && warn "Less than 8 GB RAM detected (${TOTAL_RAM_MB} MB). Add swap in Step 4."

echo -e "\n${BOLD}StoreLense — Ubuntu 22.04 Installer${NC}"
echo    "────────────────────────────────────"
echo    "Install directory : $INSTALL_DIR"
echo    "RAM detected      : ${TOTAL_RAM_MB} MB"
echo    "User              : ${SUDO_USER:-root}"
echo ""
read -rp "Press ENTER to continue or Ctrl-C to cancel..."

# =============================================================================
step "1 / 12 — System Update"
# =============================================================================
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get upgrade -y -qq
apt-get install -y -qq \
  curl wget git jq openssl \
  python3 python3-pip \
  ca-certificates gnupg lsb-release \
  ufw htop net-tools
log "System packages up to date"

# =============================================================================
step "2 / 12 — Docker Engine + Compose Plugin"
# =============================================================================
if command -v docker &>/dev/null; then
  log "Docker already installed: $(docker --version | head -1)"
else
  info "Installing Docker from official Docker repository..."
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    | tee /etc/apt/sources.list.d/docker.list > /dev/null
  apt-get update -qq
  apt-get install -y -qq \
    docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin
  log "Docker installed: $(docker --version | head -1)"
fi

# Add the sudo-invoking user to the docker group
SUDO_USER_NAME=${SUDO_USER:-}
if [[ -n "$SUDO_USER_NAME" ]] && id "$SUDO_USER_NAME" &>/dev/null; then
  usermod -aG docker "$SUDO_USER_NAME"
  log "Added $SUDO_USER_NAME to docker group (re-login to apply)"
fi

systemctl enable --now docker >/dev/null 2>&1
docker compose version >/dev/null || err "docker compose plugin not found"
log "Docker Compose: $(docker compose version --short)"

# =============================================================================
step "3 / 12 — Kernel Tuning (Kafka + file descriptors)"
# =============================================================================
# Kafka requires vm.max_map_count >= 262144
sysctl -w vm.max_map_count=262144 >/dev/null
grep -qxF 'vm.max_map_count=262144' /etc/sysctl.conf \
  || echo 'vm.max_map_count=262144' >> /etc/sysctl.conf

# Raise open-file limits for PostgreSQL and Kafka
cat > /etc/security/limits.d/storelense.conf <<'EOF'
* soft nofile 65536
* hard nofile 65536
root soft nofile 65536
root hard nofile 65536
EOF

log "Kernel tuning applied (vm.max_map_count=262144, nofile=65536)"

# =============================================================================
step "4 / 12 — Swap (required for Next.js Docker build if RAM < 4 GB)"
# =============================================================================
TOTAL_RAM_KB=$(awk '/MemTotal/{print $2}' /proc/meminfo)
SWAP_KB=$(awk '/SwapTotal/{print $2}' /proc/meminfo)

if [[ $TOTAL_RAM_KB -lt 4194304 && $SWAP_KB -lt 1048576 ]]; then
  info "Low RAM detected — creating 4 GB swapfile..."
  [[ -f /swapfile ]] && swapoff /swapfile 2>/dev/null || true
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -qxF '/swapfile none swap sw 0 0' /etc/fstab \
    || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  log "4 GB swap created and enabled"
else
  log "Sufficient RAM/swap available (skipping swap creation)"
fi

# =============================================================================
step "5 / 12 — Source Code"
# =============================================================================
if [[ -f "$INSTALL_DIR/deploy/docker-compose.yml" ]]; then
  # Code already present (extracted from ZIP or previously cloned)
  log "Code found at $INSTALL_DIR — skipping clone"
  if [[ -d "$INSTALL_DIR/.git" ]]; then
    info "Git repo detected — pulling latest changes..."
    git -C "$INSTALL_DIR" pull --rebase && log "Repository updated"
  fi
else
  # Nothing there — try to clone
  info "Cloning StoreLense to $INSTALL_DIR ..."
  apt-get install -y -qq git
  git clone "$REPO_URL" "$INSTALL_DIR"
  log "Repository cloned"
fi

cd "$INSTALL_DIR/deploy"

# =============================================================================
step "6 / 12 — Configuration"
# =============================================================================

# Detect server IP
DETECTED_IP=$(curl -sf --max-time 5 https://ipv4.icanhazip.com 2>/dev/null \
  || hostname -I | awk '{print $1}')

echo ""
echo "  Detected public IP: ${DETECTED_IP}"
echo "  Enter your server's hostname or IP."
echo "  Examples:  192.168.1.100   or   storelense.example.com"
read -rp "  Server hostname/IP [${DETECTED_IP}]: " SERVER_HOST
SERVER_HOST=${SERVER_HOST:-$DETECTED_IP}

# Generate strong secrets
JWT_SECRET=$(openssl rand -hex 32)
DB_PASS=$(openssl rand -base64 18 | tr -d '/+=')
PG_PASS=$(openssl rand -base64 18 | tr -d '/+=')

log "Server host: $SERVER_HOST"
log "Secrets generated"

# Write .env
cat > "$INSTALL_DIR/deploy/.env" <<EOF
# StoreLense environment — generated $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# Keep this file secure. Do not commit to git.

# ── PostgreSQL ──────────────────────────────────────────────
POSTGRES_PASSWORD=${PG_PASS}
DB_PASSWORD=${DB_PASS}
DB_USERNAME=storelense_app
DB_NAME=storelense

# ── Redis ───────────────────────────────────────────────────
REDIS_PASSWORD=

# ── JWT (32-char minimum) ───────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Service URLs (internal Docker network) ──────────────────
PRODUCT_SERVICE_URL=http://product-service:8083
STORE_SERVICE_URL=http://store-service:8082
INTERNAL_API_URL=http://nginx-gateway:8080

# ── ERP Integration ──────────────────────────────────────────
ERP_BASE_URL=http://erp-mock:9000
ERP_API_KEY=dev-key
ERP_API_VERSION=v1
ERP_PUSH_SOH_ENABLED=false
EOF
log ".env written"

# Patch init.sql: replace hardcoded 'changeme' password with generated one
# init.sql runs only on first postgres volume creation
sed -i "s/WITH PASSWORD 'changeme'/WITH PASSWORD '${DB_PASS}'/" \
  "$INSTALL_DIR/deploy/postgres/init.sql"
log "postgres/init.sql patched with generated DB password"

# Create docker-compose.override.yml
# - Increases Redis maxmemory from 512 MB → 4 GB
# - Increases Kafka partitions from 3 → 6
# - Sets server-specific WebSocket URL
cat > "$INSTALL_DIR/deploy/docker-compose.override.yml" <<EOF
# Generated by install.sh — server-specific overrides
# Do not edit manually; re-run install.sh to regenerate.
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
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"

  frontend:
    environment:
      NEXT_PUBLIC_API_BASE_URL: http://${SERVER_HOST}:8080
      NEXT_PUBLIC_WS_URL: ws://${SERVER_HOST}:8091/ws
      INTERNAL_API_URL: http://nginx-gateway:8080
EOF
log "docker-compose.override.yml written"

# Save credentials
cat > "$CREDS_FILE" <<EOF
StoreLense — Deployment Credentials
Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Server:    ${SERVER_HOST}
KEEP THIS FILE SECURE. Do not share.

── Access URLs ──────────────────────────────────────────────
Frontend:       http://${SERVER_HOST}:3000
API Gateway:    http://${SERVER_HOST}:8080
Health check:   http://${SERVER_HOST}:8080/health

── Database ─────────────────────────────────────────────────
Host:           localhost:5432
Database:       storelense
App user:       storelense_app  /  ${DB_PASS}
Superuser:      postgres        /  ${PG_PASS}

── Security ─────────────────────────────────────────────────
JWT Secret:     ${JWT_SECRET}

── Manage ───────────────────────────────────────────────────
Start:   sudo systemctl start storelense
Stop:    sudo systemctl stop storelense
Logs:    cd /opt/storelense/deploy && docker compose logs -f
Status:  cd /opt/storelense/deploy && docker compose ps
EOF
chmod 600 "$CREDS_FILE"
log "Credentials saved → $CREDS_FILE"

# =============================================================================
step "7 / 12 — Build Docker Images (10–20 min on first run)"
# =============================================================================
info "Building 11 backend services + frontend..."
info "Maven downloads ~400 MB, npm downloads ~300 MB on first build."
info "Output filtered — run 'docker compose build' manually for full output."

docker compose build 2>&1 \
  | grep -E '^\s*(Successfully|ERROR|WARN|\-\-\->|Step [0-9])' \
  | sed 's/^/  /' \
  || { err "Docker build failed. Run: cd $INSTALL_DIR/deploy && docker compose build"; }

log "All images built"

# =============================================================================
step "8 / 12 — Start All Services"
# =============================================================================
docker compose up -d
log "All containers started"

# =============================================================================
step "9 / 12 — Wait for Services to Become Healthy"
# =============================================================================
info "JVM services take 2–3 minutes to complete Flyway migrations and start..."

wait_for() {
  local label=$1
  local url=$2
  local max_attempts=60   # 60 × 5s = 5 minutes
  local attempt=0
  printf "  Waiting for %-35s" "${label}..."
  until curl -sf "$url" >/dev/null 2>&1; do
    ((attempt++))
    if [[ $attempt -ge $max_attempts ]]; then
      echo -e " ${RED}TIMEOUT${NC}"
      warn "  $label did not start in time. Check: docker compose logs ${label}"
      return 1
    fi
    sleep 5
    printf "."
  done
  echo -e " ${GREEN}OK${NC}"
}

sleep 20

wait_for "postgres"              "http://localhost:8080/health" || true
wait_for "nginx-gateway"         "http://localhost:8080/health"
wait_for "auth-service"          "http://localhost:8081/actuator/health"
wait_for "store-service"         "http://localhost:8082/actuator/health"
wait_for "product-service"       "http://localhost:8083/actuator/health"
wait_for "inventory-service"     "http://localhost:8084/actuator/health"
wait_for "soh-service"           "http://localhost:8085/actuator/health"
wait_for "rfid-ingest-service"   "http://localhost:8087/actuator/health"
wait_for "frontend"              "http://localhost:3000"

# =============================================================================
step "10 / 12 — Firewall (UFW)"
# =============================================================================
ufw --force enable          >/dev/null
ufw default deny incoming   >/dev/null
ufw default allow outgoing  >/dev/null
ufw allow ssh               >/dev/null
ufw allow 3000/tcp comment 'StoreLense frontend'   >/dev/null
ufw allow 8080/tcp comment 'StoreLense API gateway' >/dev/null
ufw reload                  >/dev/null
log "UFW enabled: SSH + 3000 + 8080 open"

# =============================================================================
step "11 / 12 — Systemd Auto-Start on Reboot"
# =============================================================================
cat > /etc/systemd/system/storelense.service <<EOF
[Unit]
Description=StoreLense RFID Platform
Documentation=https://github.com/raghuhr1/StoreLense
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${INSTALL_DIR}/deploy
ExecStart=/usr/bin/docker compose up -d --remove-orphans
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable storelense
log "storelense.service enabled (starts automatically on reboot)"

# =============================================================================
step "12 / 12 — Seed Demo Data (optional)"
# =============================================================================
info "Seeding P036 demo store data..."
if [[ -f "$INSTALL_DIR/tools/seed_demo_data.sh" ]]; then
  bash "$INSTALL_DIR/tools/seed_demo_data.sh" \
    && log "Demo data seeded" \
    || warn "Seed script failed — run manually: bash $INSTALL_DIR/tools/seed_demo_data.sh"
else
  warn "Seed script not found — skip seeding"
fi

# =============================================================================
echo ""
echo -e "${BOLD}${GREEN}"
echo    "  ╔══════════════════════════════════════════════════════╗"
echo    "  ║         StoreLense Deployment Complete               ║"
echo    "  ╠══════════════════════════════════════════════════════╣"
printf  "  ║  Frontend    : http://%-30s║\n" "${SERVER_HOST}:3000  "
printf  "  ║  API Gateway : http://%-30s║\n" "${SERVER_HOST}:8080  "
printf  "  ║  Health      : http://%-30s║\n" "${SERVER_HOST}:8080/health  "
echo    "  ╠══════════════════════════════════════════════════════╣"
echo    "  ║  Credentials : /root/storelense-credentials.txt     ║"
echo    "  ╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo    "  Quick commands:"
echo    "    View all logs   :  cd $INSTALL_DIR/deploy && docker compose logs -f"
echo    "    Service status  :  cd $INSTALL_DIR/deploy && docker compose ps"
echo    "    Restart all     :  sudo systemctl restart storelense"
echo    "    Stop all        :  sudo systemctl stop storelense"
echo    "    DB console      :  cd $INSTALL_DIR/deploy && docker compose exec postgres psql -U postgres storelense"
echo ""
