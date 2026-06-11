#!/usr/bin/env bash
# =============================================================================
#  StoreLense — Update / Re-deploy Script
#  Usage:  bash update.sh                 (pull + rebuild changed services)
#          bash update.sh --all           (rebuild all services)
#          bash update.sh --fix-password  (only fix DB password, no rebuild)
#  Run from the repo root on the server.
# =============================================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
step()    { echo -e "\n${BOLD}${CYAN}▶ $*${RESET}"; }

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE="docker compose"
ENV_FILE="$REPO_ROOT/deploy/.env"

if ! command -v docker &>/dev/null; then
  echo -e "${RED}Docker not found${RESET}"; exit 1
fi
if ! docker info &>/dev/null 2>&1; then
  COMPOSE="sudo docker compose"
fi

cd "$REPO_ROOT/deploy"

# ── Read DB password from .env ────────────────────────────────────────────────
DB_PASSWORD=""
if [[ -f "$ENV_FILE" ]]; then
  DB_PASSWORD=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2 | tr -d '[:space:]')
fi
if [[ -z "$DB_PASSWORD" ]]; then
  DB_PASSWORD="changeme"
  warn "DB_PASSWORD not found in .env — using default 'changeme'"
fi

# =============================================================================
# FIX PASSWORD  (always run — keeps postgres in sync with .env)
# =============================================================================
fix_password() {
  step "Syncing Postgres password with .env"
  if $COMPOSE ps postgres 2>/dev/null | grep -q "running\|Up"; then
    $COMPOSE exec -T postgres psql -U postgres \
      -c "ALTER USER storelense_app WITH PASSWORD '$DB_PASSWORD';" \
      > /dev/null 2>&1 && success "storelense_app password synced" \
      || warn "Could not alter password (postgres may be starting — will retry)"
  else
    warn "Postgres container not running — skipping password sync"
  fi
}

# =============================================================================
# RESTART SERVICES THAT DEPEND ON DB
# =============================================================================
restart_db_services() {
  step "Restarting DB-dependent services"
  for svc in auth-service store-service product-service inventory-service \
              soh-service erp-integration-service reporting-service \
              rfid-ingest-service rfid-processing-service refill-service \
              notification-service; do
    if $COMPOSE ps "$svc" 2>/dev/null | grep -q "running\|Up"; then
      $COMPOSE restart "$svc" > /dev/null 2>&1 && info "Restarted $svc"
    fi
  done
  success "DB-dependent services restarted"
}

# =============================================================================
# MAIN
# =============================================================================
FIX_ONLY=false
REBUILD_ALL=false

for arg in "$@"; do
  case "$arg" in
    --fix-password) FIX_ONLY=true ;;
    --all)          REBUILD_ALL=true ;;
  esac
done

if $FIX_ONLY; then
  fix_password
  restart_db_services
  echo ""
  success "Password fix complete. Try logging in now."
  exit 0
fi

# ── Pull latest code ──────────────────────────────────────────────────────────
step "Pulling latest code from git"
cd "$REPO_ROOT"
git pull
success "git pull done"

cd "$REPO_ROOT/deploy"

# ── Rebuild services ──────────────────────────────────────────────────────────
if $REBUILD_ALL; then
  step "Rebuilding ALL services"
  $COMPOSE up -d --build
else
  step "Rebuilding changed services (erp-integration-service + frontend)"
  $COMPOSE up -d --build erp-integration-service frontend
fi

# ── Always sync the password after any rebuild ────────────────────────────────
# Wait a moment for postgres to settle if it also restarted
sleep 5
fix_password

# ── Restart auth-service last (needs correct DB password + Flyway) ────────────
step "Restarting auth-service"
$COMPOSE restart auth-service
success "auth-service restarted"

# ── Wait for auth-service to be healthy ───────────────────────────────────────
step "Waiting for auth-service to become healthy"
MAX=60; ELAPSED=0
while [[ $ELAPSED -lt $MAX ]]; do
  STATUS=$($COMPOSE ps --format '{{json .}}' 2>/dev/null | \
    python3 -c "
import sys, json
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    try: c = json.loads(line)
    except: continue
    if 'auth' in (c.get('Name','') + c.get('Service','')):
        print(c.get('Health') or c.get('State') or 'unknown')
        break
" 2>/dev/null || echo "unknown")

  if [[ "$STATUS" == "healthy" ]]; then
    success "auth-service is healthy"
    break
  fi
  info "Waiting for auth-service... ($ELAPSED s) status=$STATUS"
  sleep 5; ELAPSED=$((ELAPSED + 5))
done

if [[ $ELAPSED -ge $MAX ]]; then
  warn "auth-service not yet healthy after ${MAX}s"
  warn "Check logs: docker compose logs --tail=50 auth-service"
fi

# ── Smoke test login ──────────────────────────────────────────────────────────
step "Testing admin login"
sleep 3
LOGIN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' \
  --max-time 10 2>/dev/null || echo "{}")

TOKEN=$(echo "$LOGIN" | python3 -c \
  "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" \
  2>/dev/null || echo "")

if [[ -n "$TOKEN" ]]; then
  success "Admin login OK — you can log in at http://$(hostname -I | awk '{print $1}'):3000"
else
  warn "Login test failed. Running password fix again..."
  fix_password
  sleep 3
  $COMPOSE restart auth-service
  warn "Wait 20s then try logging in. If still failing:"
  warn "  docker compose logs --tail=50 auth-service"
fi

echo ""
echo -e "${BOLD}${GREEN}Update complete.${RESET}"
echo -e "  ${BOLD}URL:${RESET} http://$(hostname -I | awk '{print $1}'):3000"
echo -e "  ${BOLD}Login:${RESET} admin / Admin@StoreLense1"
echo ""
