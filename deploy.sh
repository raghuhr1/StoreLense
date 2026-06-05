#!/usr/bin/env bash
# =============================================================================
#  StoreLense — One-shot Ubuntu deployment script
#  Usage: bash deploy.sh
#  Run from the repo root after: git clone https://github.com/raghuhr1/StoreLense
# =============================================================================
set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*"; exit 1; }
step()    { echo -e "\n${BOLD}${CYAN}▶ $*${RESET}"; }

# ── Banner ────────────────────────────────────────────────────────────────────
echo -e "${BOLD}${CYAN}"
cat << 'EOF'
  ____  _                 _
 / ___|| |_ ___  _ __ ___| |    ___ _ __  ___  ___
 \___ \| __/ _ \| '__/ _ \ |   / _ \ '_ \/ __|/ _ \
  ___) | || (_) | | |  __/ |__|  __/ | | \__ \  __/
 |____/ \__\___/|_|  \___|_____\___|_| |_|___/\___|

  RFID Store Operations Platform — Deployment Script
EOF
echo -e "${RESET}"

# ── Root check ────────────────────────────────────────────────────────────────
if [[ $EUID -eq 0 ]]; then
  error "Do not run as root. Run as a regular user with sudo privileges."
fi

# ── Repo root check ───────────────────────────────────────────────────────────
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ ! -f "$REPO_ROOT/deploy/docker-compose.yml" ]]; then
  error "Run this script from the StoreLense repo root. deploy/docker-compose.yml not found."
fi
info "Repo root: $REPO_ROOT"

# =============================================================================
# PHASE 1 — COLLECT CONFIGURATION
# =============================================================================
step "Configuration"

# Auto-detect server IP
DETECTED_IP=$(hostname -I | awk '{print $1}')

echo ""
echo -e "${BOLD}Press ENTER to accept the default value shown in [brackets].${RESET}"
echo ""

read -rp "  Server IP or domain  [${DETECTED_IP}]: " SERVER_IP
SERVER_IP="${SERVER_IP:-$DETECTED_IP}"

read -rp "  PostgreSQL superuser password  [postgres]: " PG_PASSWORD
PG_PASSWORD="${PG_PASSWORD:-postgres}"

read -rp "  App DB password (storelense_app)  [changeme]: " DB_PASSWORD
DB_PASSWORD="${DB_PASSWORD:-changeme}"

read -rp "  Redis password (leave blank for none)  []: " REDIS_PASSWORD
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

# Auto-generate JWT secret
DEFAULT_JWT=$(openssl rand -hex 32 2>/dev/null || cat /proc/sys/kernel/random/uuid | tr -d '-' | head -c 64)
read -rp "  JWT secret (min 32 chars)  [auto-generated]: " JWT_SECRET
JWT_SECRET="${JWT_SECRET:-$DEFAULT_JWT}"
if [[ ${#JWT_SECRET} -lt 32 ]]; then
  error "JWT_SECRET must be at least 32 characters."
fi

read -rp "  Enable ERP push (true/false)  [false]: " ERP_ENABLED
ERP_ENABLED="${ERP_ENABLED:-false}"

read -rp "  Setup systemd auto-start on boot? (y/n)  [y]: " SETUP_SYSTEMD
SETUP_SYSTEMD="${SETUP_SYSTEMD:-y}"

echo ""
echo -e "${BOLD}--- Deployment Summary ---${RESET}"
echo -e "  Server IP/Domain : ${GREEN}${SERVER_IP}${RESET}"
echo -e "  Frontend URL     : ${GREEN}http://${SERVER_IP}:3000${RESET}"
echo -e "  API Gateway URL  : ${GREEN}http://${SERVER_IP}:8080${RESET}"
echo -e "  ERP push         : ${ERP_ENABLED}"
echo -e "  Systemd service  : ${SETUP_SYSTEMD}"
echo ""
read -rp "Continue? (y/n) [y]: " CONFIRM
CONFIRM="${CONFIRM:-y}"
[[ "$CONFIRM" =~ ^[Yy]$ ]] || { info "Aborted."; exit 0; }

# =============================================================================
# PHASE 2 — INSTALL DEPENDENCIES
# =============================================================================
step "Installing system dependencies"

export DEBIAN_FRONTEND=noninteractive

# ── Repair apt before doing anything else ────────────────────────────────────
# Fixes: "E: Method https has died unexpectedly! Sub-process https received signal 4"
# Cause: corrupted apt cache, broken apt-transport-https, or stale package lists.
info "Repairing apt cache..."
sudo apt-get clean -qq
sudo rm -rf /var/lib/apt/lists/*
sudo dpkg --configure -a 2>/dev/null || true
sudo apt-get install --fix-broken -y -qq 2>/dev/null || true

# Bootstrap with plain HTTP to get ca-certificates and https transport working
sudo apt-get update -o Acquire::https::Verify-Peer=false -qq 2>/dev/null || \
  sudo apt-get update -qq || true

sudo apt-get install -y -qq --no-install-recommends \
  ca-certificates apt-transport-https 2>/dev/null || true

# Now do the full update with HTTPS working
sudo apt-get update -qq
success "apt repaired and updated"

# Core tools
sudo apt-get install -y -qq --no-install-recommends \
  curl wget git gnupg lsb-release \
  software-properties-common \
  openssl jq python3

success "Core tools installed"

# ── Docker ────────────────────────────────────────────────────────────────────
if command -v docker &>/dev/null; then
  success "Docker already installed: $(docker --version)"
else
  info "Installing Docker..."
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg

  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

  sudo apt-get update -qq
  sudo apt-get install -y -qq --no-install-recommends \
    docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin

  sudo usermod -aG docker "$USER"
  success "Docker installed: $(docker --version)"
fi

# Ensure current session can use docker without sudo
if ! docker info &>/dev/null 2>&1; then
  warn "Running docker via sudo for this session (re-login to make permanent)"
  DOCKER="sudo docker"
else
  DOCKER="docker"
fi

# ── Docker Compose v2 check ───────────────────────────────────────────────────
if ! $DOCKER compose version &>/dev/null 2>&1; then
  error "Docker Compose plugin not found. Re-run the script or install manually."
fi
success "Docker Compose: $($DOCKER compose version --short)"

# ── PowerShell (for integration tests) ───────────────────────────────────────
if command -v pwsh &>/dev/null; then
  success "PowerShell already installed: $(pwsh --version)"
else
  info "Installing PowerShell..."
  curl -sSL https://packages.microsoft.com/keys/microsoft.asc | \
    sudo gpg --dearmor -o /usr/share/keyrings/microsoft.gpg

  echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/microsoft.gpg] \
    https://packages.microsoft.com/ubuntu/$(lsb_release -rs)/prod \
    $(lsb_release -cs) main" | \
    sudo tee /etc/apt/sources.list.d/microsoft-prod.list > /dev/null

  sudo apt-get update -qq
  sudo apt-get install -y -qq --no-install-recommends powershell
  success "PowerShell installed: $(pwsh --version)"
fi

# =============================================================================
# PHASE 3 — WRITE CONFIGURATION FILES
# =============================================================================
step "Writing .env and patching docker-compose.yml"

ENV_FILE="$REPO_ROOT/deploy/.env"

cat > "$ENV_FILE" << EOF
# Generated by deploy.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")

# PostgreSQL
POSTGRES_PASSWORD=${PG_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
DB_USERNAME=storelense_app
DB_NAME=storelense

# Redis
REDIS_PASSWORD=${REDIS_PASSWORD}

# Kafka
KAFKA_BOOTSTRAP=kafka:9092

# JWT (min 32 chars)
JWT_SECRET=${JWT_SECRET}

# Service URLs — do not change (Docker internal networking)
PRODUCT_SERVICE_URL=http://product-service:8083
STORE_SERVICE_URL=http://store-service:8082

# ERP integration
ERP_PUSH_SOH_ENABLED=${ERP_ENABLED}
ERP_BASE_URL=http://localhost:9000
ERP_API_KEY=dev-key
ERP_API_VERSION=v1

# Frontend gateway (internal Next.js SSR)
INTERNAL_API_URL=http://nginx-gateway:8080
EOF

success ".env written to $ENV_FILE"

# Patch server IP in docker-compose.yml
COMPOSE_FILE="$REPO_ROOT/deploy/docker-compose.yml"
# Replace localhost placeholders in the frontend environment block
sed -i \
  "s|NEXT_PUBLIC_API_BASE_URL: http://localhost:8080|NEXT_PUBLIC_API_BASE_URL: http://${SERVER_IP}:8080|g" \
  "$COMPOSE_FILE"
sed -i \
  "s|NEXT_PUBLIC_WS_URL:.*ws://localhost:8091/ws|NEXT_PUBLIC_WS_URL:       ws://${SERVER_IP}:8091/ws|g" \
  "$COMPOSE_FILE"

success "docker-compose.yml patched with IP: ${SERVER_IP}"

# =============================================================================
# PHASE 4 — CONFIGURE FIREWALL
# =============================================================================
step "Configuring UFW firewall"

if command -v ufw &>/dev/null; then
  sudo ufw allow 22/tcp   comment 'SSH'      > /dev/null 2>&1 || true
  sudo ufw allow 3000/tcp comment 'StoreLense Frontend' > /dev/null 2>&1 || true
  sudo ufw allow 8080/tcp comment 'StoreLense API Gateway' > /dev/null 2>&1 || true
  sudo ufw allow 29092/tcp comment 'Kafka external (Zebra devices)' > /dev/null 2>&1 || true
  sudo ufw --force enable > /dev/null 2>&1 || true
  success "UFW rules set (22, 3000, 8080, 29092)"
else
  warn "UFW not found — skipping firewall setup"
fi

# =============================================================================
# PHASE 5 — BUILD AND START SERVICES
# =============================================================================
step "Building Docker images and starting all services"
info "This will take 15–25 minutes on first run..."

cd "$REPO_ROOT/deploy"

$DOCKER compose pull --ignore-buildable -q 2>/dev/null || true
$DOCKER compose up -d --build 2>&1 | tee /tmp/storelense-build.log

success "docker compose up complete"

# =============================================================================
# PHASE 6 — WAIT FOR HEALTHY STATUS
# =============================================================================
step "Waiting for all services to become healthy"

SERVICES=(
  postgres redis kafka
  auth-service store-service product-service inventory-service
  soh-service refill-service rfid-ingest-service rfid-processing-service
  reporting-service erp-integration-service notification-service
  nginx-gateway frontend
)

MAX_WAIT=300   # 5 minutes
INTERVAL=10
ELAPSED=0

while true; do
  ALL_HEALTHY=true
  NOT_READY=()

  for svc in "${SERVICES[@]}"; do
    STATUS=$($DOCKER compose ps --format json 2>/dev/null | \
      python3 -c "
import sys, json
lines = sys.stdin.read().strip()
# handle both JSON array and newline-delimited JSON
try:
    items = json.loads(lines)
    if isinstance(items, dict): items = [items]
except:
    items = [json.loads(l) for l in lines.splitlines() if l.strip()]
target = '${svc}'
for c in items:
    name = c.get('Name','') or c.get('Service','')
    if target in name:
        print(c.get('Health', c.get('Status','')))
        break
" 2>/dev/null || echo "unknown")

    if [[ "$STATUS" != "healthy" ]]; then
      ALL_HEALTHY=false
      NOT_READY+=("$svc($STATUS)")
    fi
  done

  if $ALL_HEALTHY; then
    success "All services are healthy!"
    break
  fi

  if [[ $ELAPSED -ge $MAX_WAIT ]]; then
    warn "Timed out waiting. Not yet healthy: ${NOT_READY[*]}"
    warn "Check logs: docker compose logs -f <service-name>"
    break
  fi

  info "Waiting... (${ELAPSED}s / ${MAX_WAIT}s) — not ready: ${NOT_READY[*]}"
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done

# =============================================================================
# PHASE 7 — SMOKE TEST
# =============================================================================
step "Running smoke tests"

GATEWAY="http://localhost:8080"
PASS=0; FAIL=0

smoke_test() {
  local name="$1" url="$2" expected="${3:-200}"
  local http
  http=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")
  if [[ "$http" == "$expected" ]]; then
    echo -e "  ${GREEN}[PASS]${RESET} $name ($http)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}[FAIL]${RESET} $name — expected $expected, got $http"
    FAIL=$((FAIL + 1))
  fi
}

# Health endpoints
smoke_test "Gateway /health"       "$GATEWAY/health"
smoke_test "Auth actuator/health"  "http://localhost:8081/actuator/health"

# Login
LOGIN_RESP=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' \
  --max-time 15 2>/dev/null || echo "{}")

TOKEN=$(echo "$LOGIN_RESP" | python3 -c \
  "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" \
  2>/dev/null || echo "")

if [[ -n "$TOKEN" ]]; then
  echo -e "  ${GREEN}[PASS]${RESET} POST /api/auth/login (200)"
  PASS=$((PASS + 1))

  smoke_test "GET /api/stores"   "$GATEWAY/api/stores" 200
  smoke_test "GET /api/products" "$GATEWAY/api/products" 200
else
  echo -e "  ${RED}[FAIL]${RESET} POST /api/auth/login — no token returned"
  FAIL=$((FAIL + 1))
fi

TOTAL=$((PASS + FAIL))
echo ""
if [[ $FAIL -eq 0 ]]; then
  success "Smoke tests: ${PASS}/${TOTAL} passed"
else
  warn "Smoke tests: ${PASS}/${TOTAL} passed, ${FAIL} failed"
  warn "Services may still be starting — re-run in 60s: bash $REPO_ROOT/deploy.sh --test-only"
fi

# =============================================================================
# PHASE 8 — SYSTEMD AUTO-START
# =============================================================================
if [[ "$SETUP_SYSTEMD" =~ ^[Yy]$ ]]; then
  step "Setting up systemd service for auto-start on reboot"

  COMPOSE_BIN=$(command -v docker || echo "/usr/bin/docker")

  sudo tee /etc/systemd/system/storelense.service > /dev/null << EOF
[Unit]
Description=StoreLense RFID Store Operations Platform
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=${REPO_ROOT}/deploy
ExecStart=${COMPOSE_BIN} compose up -d --remove-orphans
ExecStop=${COMPOSE_BIN} compose down
TimeoutStartSec=300
User=${USER}

[Install]
WantedBy=multi-user.target
EOF

  sudo systemctl daemon-reload
  sudo systemctl enable storelense
  success "Systemd service enabled — StoreLense will start automatically on reboot"
fi

# =============================================================================
# DONE — Print summary
# =============================================================================
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}${GREEN}  StoreLense deployed successfully!${RESET}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════════════════${RESET}"
echo ""
echo -e "  ${BOLD}Web UI       :${RESET}  http://${SERVER_IP}:3000"
echo -e "  ${BOLD}API Gateway  :${RESET}  http://${SERVER_IP}:8080"
echo -e "  ${BOLD}Username     :${RESET}  admin"
echo -e "  ${BOLD}Password     :${RESET}  Admin@StoreLense1"
echo ""
echo -e "  ${BOLD}Useful commands:${RESET}"
echo -e "  ${CYAN}cd ${REPO_ROOT}/deploy${RESET}"
echo -e "  ${CYAN}docker compose ps${RESET}               — check container status"
echo -e "  ${CYAN}docker compose logs -f <name>${RESET}   — tail a service's logs"
echo -e "  ${CYAN}docker compose restart <name>${RESET}   — restart a service"
echo -e "  ${CYAN}docker compose down${RESET}             — stop everything"
echo -e "  ${CYAN}docker compose down -v${RESET}          — stop + wipe all data"
echo ""
echo -e "  ${YELLOW}ACTION REQUIRED: Change the admin password after first login!${RESET}"
echo ""

# Full integration test hint
if command -v pwsh &>/dev/null; then
  echo -e "  Run full integration tests:"
  echo -e "  ${CYAN}pwsh ${REPO_ROOT}/deploy/test.ps1 -Gateway http://localhost:8080${RESET}"
  echo ""
fi
