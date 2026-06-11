#!/usr/bin/env bash
# StoreLense — Store LK001 seed script
# Usage: bash tools/seed_lk001.sh [BASE_URL]
# Example: bash tools/seed_lk001.sh http://150.241.244.61:8080

set -euo pipefail

GATEWAY="${1:-http://150.241.244.61:8080}"
BOLD='\033[1m'; GREEN='\033[32m'; RED='\033[31m'; CYAN='\033[36m'; YELLOW='\033[33m'; RESET='\033[0m'

step()  { echo -e "\n${BOLD}${CYAN}▶ $*${RESET}"; }
ok()    { echo -e "  ${GREEN}✔ $*${RESET}"; }
info()  { echo -e "  ${YELLOW}ℹ $*${RESET}"; }
fail()  { echo -e "  ${RED}✘ $*${RESET}"; }

post()  { curl -s -X POST "$GATEWAY$1" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$2" --max-time 15; }
get()   { curl -s "$GATEWAY$1" -H "Authorization: Bearer $TOKEN" --max-time 15; }
extract() { echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print($2)" 2>/dev/null || echo ""; }

# ── Login ──────────────────────────────────────────────────────────────────────
step "Authenticating as admin"
LOGIN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' --max-time 15)
TOKEN=$(extract "$LOGIN" 'd["data"]["accessToken"]')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  fail "Login failed — check server is reachable at $GATEWAY"
  echo "$LOGIN"
  exit 1
fi
ok "Token acquired"

# ── Step 1: Store ──────────────────────────────────────────────────────────────
step "Creating Store: LK001"
STORE_CREATE=$(post /api/stores '{
  "storeCode":    "LK001",
  "name":         "StoreLense LK001",
  "addressLine1": "Main Street",
  "city":         "Lucknow",
  "stateProvince":"Uttar Pradesh",
  "postalCode":   "226001",
  "countryCode":  "IN",
  "timezone":     "Asia/Kolkata",
  "erpStoreCode": "ERP-LK001"
}')
STORE_ID=$(extract "$STORE_CREATE" 'd["data"]["id"]')
if [[ -n "$STORE_ID" && "$STORE_ID" != "null" ]]; then
  ok "Store LK001 created → $STORE_ID"
else
  info "Store creation failed ($(extract "$STORE_CREATE" 'd.get("message","")')) — checking if LK001 already exists"
  STORE_LIST=$(get "/api/stores?page=0&size=100")
  STORE_ID=$(echo "$STORE_LIST" | python3 -c "
import sys,json
stores=json.load(sys.stdin)
content=stores.get('data',{}).get('content', stores.get('data',[]))
match=[s['id'] for s in content if s.get('storeCode')=='LK001']
print(match[0] if match else '')
" 2>/dev/null || echo "")
  if [[ -n "$STORE_ID" && "$STORE_ID" != "null" ]]; then
    ok "Found existing store LK001 → $STORE_ID"
  else
    fail "Could not create or find store LK001 — aborting"
    exit 1
  fi
fi

# ── Step 2: Zones ─────────────────────────────────────────────────────────────
step "Creating Zones"
create_zone() {
  local resp
  resp=$(post "/api/stores/$STORE_ID/zones" "$2")
  local id; id=$(extract "$resp" 'd.get("data",{}).get("id","") or d.get("id","")')
  if [[ -n "$id" && "$id" != "null" ]]; then
    ok "Zone $1 → $id"
  else
    info "Zone $1: $(extract "$resp" 'd.get("message","already exists")')"
  fi
}

create_zone "FLOOR-A"   '{"zoneCode":"FLOOR-A","name":"Shop Floor A","zoneType":"floor","displayOrder":1}'
create_zone "FLOOR-B"   '{"zoneCode":"FLOOR-B","name":"Shop Floor B","zoneType":"floor","displayOrder":2}'
create_zone "TRIAL"     '{"zoneCode":"TRIAL","name":"Trial Rooms","zoneType":"fitting_room","displayOrder":3}'
create_zone "BACKROOM"  '{"zoneCode":"BACKROOM","name":"Backroom","zoneType":"backroom","displayOrder":4}'
create_zone "STOCKROOM" '{"zoneCode":"STOCKROOM","name":"Stockroom","zoneType":"stockroom","displayOrder":5}'

# ── Step 3: Users ─────────────────────────────────────────────────────────────
step "Creating Store Users"
make_user() {
  local uname="$1" pwd="$2" first="$3" last="$4" email="$5" role="$6"
  local resp
  resp=$(post /api/users "{
    \"username\":\"$uname\",\"password\":\"$pwd\",
    \"firstName\":\"$first\",\"lastName\":\"$last\",
    \"email\":\"$email\",\"roles\":[\"$role\"],
    \"storeId\":\"$STORE_ID\"
  }")
  local id; id=$(extract "$resp" 'd.get("data",{}).get("id","") or d.get("id","")')
  if [[ -n "$id" && "$id" != "null" ]]; then
    ok "User $uname ($role) → $id"
  else
    info "User $uname: $(extract "$resp" 'd.get("message","may already exist")')"
  fi
}

make_user "mgr_lk001"   "Manager@LK0011!"  "Store"   "Manager"   "mgr@lk001.storelense"    "STORE_MANAGER"
make_user "asc_lk001"   "Assoc@LK0011!"    "Store"   "Associate" "asc@lk001.storelense"    "STORE_ASSOCIATE"
make_user "rfl_lk001"   "Refill@LK0011!"   "Refill"  "Associate" "rfl@lk001.storelense"    "REFILL_ASSOCIATE"
make_user "asc2_lk001"  "Assoc2@LK0011!"   "Store"   "Associate" "asc2@lk001.storelense"   "STORE_ASSOCIATE"
make_user "guard_lk001" "Guard@LK0011!"    "Gate"    "Guard"     "guard@lk001.storelense"  "SECURITY_GUARD"

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "${BOLD}  Store LK001 ready!${RESET}"
echo -e "${BOLD}  Store ID:${RESET} $STORE_ID"
echo ""
echo -e "  ${BOLD}Login credentials for Zebra (SOH) app:${RESET}"
echo -e "    mgr_lk001   / Manager@LK0011!  → Store Manager"
echo -e "    asc_lk001   / Assoc@LK0011!    → Store Associate"
echo -e "    asc2_lk001  / Assoc2@LK0011!   → Store Associate"
echo -e "    rfl_lk001   / Refill@LK0011!   → Refill Associate"
echo -e ""
echo -e "  ${BOLD}Login credentials for C66 Guard app:${RESET}"
echo -e "    guard_lk001 / Guard@LK0011!    → Security Guard"
echo -e ""
echo -e "  ${BOLD}Global guard (no store):${RESET}"
echo -e "    guard_demo  / Guard@StoreLense1 → Security Guard (after server migration)"
echo -e "${BOLD}${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
