"""
Generate tools/seed_pantaloons_p037.sh from the parsed p036_data.json.
Picks up to MAX_PER_DEPT styles per brand that have at least one barcode,
ordered by expected quantity descending so the most stocked items are seeded.
"""
import json, re, os

MAX_PER_DEPT = 8        # styles per brand
MAX_EPC_PER_STYLE = 30  # cap EPC registrations so seed stays fast

data = json.load(open(r'd:\StoreLense\tools\p036_data.json'))
styles   = data['styles']
expected = data['style_expected']
barcodes = data['barcodes']

# ── 1. Select products ────────────────────────────────────────────────────────
from collections import defaultdict
by_dept = defaultdict(list)
for sku, info in styles.items():
    dept = info['dept']
    exp  = expected.get(sku, 0)
    bc   = barcodes.get(sku, [])
    if not bc:          # skip styles with no barcodes (can't do RFID)
        continue
    by_dept[dept].append((sku, info, exp, bc))

# Sort each dept by expected qty desc, take top N
selected = []   # (sku, info, exp, bc_list)
for dept, items in sorted(by_dept.items()):
    items.sort(key=lambda x: -x[2])
    selected.extend(items[:MAX_PER_DEPT])

print(f'Selected {len(selected)} products across {len(by_dept)} departments')

# ── 2. Build clean product name ───────────────────────────────────────────────
def clean_name(sku, raw_desc, dept):
    """Remove leading SKU code and trailing colour/size noise from description."""
    name = raw_desc.replace(sku, '').strip()
    # Collapse multiple spaces
    name = re.sub(r'\s{2,}', ' ', name).strip()
    if not name or len(name) < 3:
        name = sku
    return name

# ── 3. Build the bash seed script ─────────────────────────────────────────────
lines = []

HEADER = r"""#!/usr/bin/env bash
# =============================================================================
#  StoreLense — Pantaloons P037 Demo Seeder
#  Real Pantaloons store data: 16 brands, """ + str(len(selected)) + r""" styles, RFID-enabled
#
#  Usage:
#    bash tools/seed_pantaloons_p037.sh
#    bash tools/seed_pantaloons_p037.sh --url http://<host>:8080
#    bash tools/seed_pantaloons_p037.sh --url http://<host>:8080 --password MyPass1!
#
#  Requirements: bash, curl, jq
# =============================================================================
set -uo pipefail

GATEWAY="${STORELENSE_URL:-http://localhost:8080}"
ADMIN_USER="${STORELENSE_USER:-admin}"
ADMIN_PASS="${STORELENSE_PASS:-Admin@StoreLense1}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)      GATEWAY="$2";    shift 2 ;;
    --username) ADMIN_USER="$2"; shift 2 ;;
    --password) ADMIN_PASS="$2"; shift 2 ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

GREEN='\033[92m'; RED='\033[91m'; YELLOW='\033[93m'
CYAN='\033[96m'; BOLD='\033[1m'; RESET='\033[0m'
ok()   { echo -e "  ${GREEN}✔${RESET}  $*"; }
fail() { echo -e "  ${RED}✘${RESET}  $*"; }
info() { echo -e "  ${CYAN}→${RESET}  $*"; }
step() { echo -e "\n${BOLD}${CYAN}▶ $*${RESET}"; }

post() {
  local path="$1" body="$2"
  curl -s -X POST "$GATEWAY$path" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$body" --max-time 15
}

get() {
  local path="$1"
  curl -s "$GATEWAY$path" -H "Authorization: Bearer $TOKEN" --max-time 15
}

extract()    { echo "$1" | jq -r "$2" 2>/dev/null || echo ""; }
created_id() {
  local resp="$1" label="$2"
  local id; id=$(extract "$resp" '.data.id')
  if [[ -z "$id" || "$id" == "null" ]]; then
    fail "$label — $(extract "$resp" '.message')"
    echo ""
  else
    ok "$label → $id"
    echo "$id"
  fi
}

echo ""
echo -e "${BOLD}${CYAN}  StoreLense — Pantaloons P037 Seeder${RESET}"
echo -e "  Gateway: ${GATEWAY}"
echo ""

# =============================================================================
# STEP 0 — Login
# =============================================================================
step "Authenticating as $ADMIN_USER"
LOGIN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
  --max-time 15)
TOKEN=$(extract "$LOGIN" '.data.accessToken')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  fail "Login failed: $(extract "$LOGIN" '.message')"
  exit 1
fi
ok "Logged in"

relogin() {
  local resp
  resp=$(curl -s -X POST "$GATEWAY/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
    --max-time 15)
  local tok
  tok=$(extract "$resp" '.data.accessToken')
  if [[ -n "$tok" && "$tok" != "null" ]]; then
    TOKEN="$tok"
    ok "Token refreshed"
  else
    fail "Re-login failed: $(extract "$resp" '.message')"
  fi
}
"""
lines.append(HEADER)

# ── STEP 1: Store ─────────────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 1 — Store
# =============================================================================
step "Creating Store: Pantaloons P037"

STORE_CREATE=$(post /api/stores '{
  "storeCode":    "P037",
  "name":         "Pantaloons P037",
  "addressLine1": "Ground Floor, Main Mall",
  "city":         "Mumbai",
  "stateProvince":"Maharashtra",
  "postalCode":   "400001",
  "countryCode":  "IN",
  "timezone":     "Asia/Kolkata",
  "erpStoreCode": "ERP-P037"
}')
STORE_ID=$(extract "$STORE_CREATE" '.data.id')
if [[ -n "$STORE_ID" && "$STORE_ID" != "null" ]]; then
  ok "Store: Pantaloons P037 -> $STORE_ID"
else
  info "Store creation failed ($(extract "$STORE_CREATE" '.message')) — checking if P037 already exists"
  STORE_LIST=$(get "/api/stores?page=0&size=100")
  STORE_ID=$(echo "$STORE_LIST" | jq -r '.data.content[]? | select(.storeCode=="P037") | .id' 2>/dev/null || echo "")
  if [[ -n "$STORE_ID" && "$STORE_ID" != "null" ]]; then
    ok "Found existing store P037 -> $STORE_ID"
  else
    fail "Could not create or find store P037 — cannot continue"
    exit 1
  fi
fi
""")

# ── STEP 2: Zones ─────────────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 2 — Zones
# =============================================================================
step "Creating Zones"

create_zone() {
  local resp
  resp=$(post "/api/stores/$STORE_ID/zones" "$2")
  local id; id=$(extract "$resp" '.data.id // .id // empty')
  if [[ -n "$id" && "$id" != "null" ]]; then
    ok "Zone: $1 -> $id"
  else
    info "Zone $1: $(extract "$resp" '.message // .error // .')"
  fi
}

create_zone "GF-WOMENSWEAR"  '{"zoneCode":"GF-WOMENSWEAR","name":"Ground Floor Womenswear","zoneType":"floor","displayOrder":1}'
create_zone "GF-MENSWEAR"    '{"zoneCode":"GF-MENSWEAR","name":"Ground Floor Menswear","zoneType":"floor","displayOrder":2}'
create_zone "FF-KIDS"        '{"zoneCode":"FF-KIDS","name":"First Floor Kids","zoneType":"floor","displayOrder":3}'
create_zone "FF-ETHNIC"      '{"zoneCode":"FF-ETHNIC","name":"First Floor Ethnic","zoneType":"floor","displayOrder":4}'
create_zone "TRIAL"          '{"zoneCode":"TRIAL","name":"Trial Rooms","zoneType":"fitting_room","displayOrder":5}'
create_zone "BACKROOM"       '{"zoneCode":"BACKROOM","name":"Backroom","zoneType":"backroom","displayOrder":6}'
create_zone "STOCKROOM"      '{"zoneCode":"STOCKROOM","name":"Stockroom","zoneType":"stockroom","displayOrder":7}'
""")

# ── STEP 3: Products ──────────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 3 — Products
# =============================================================================
step "Creating Products"

make_product() {
  local sku="$1" name="$2" brand="$3" desc="$4" erp="$5"
  local body
  body=$(printf '{"sku":"%s","name":"%s","description":"%s","brand":"%s","unitOfMeasure":"EACH","rfidEnabled":true,"erpProductCode":"%s"}' \\
    "$sku" "$name" "$desc" "$brand" "$erp")
  post /api/products "$body"
}
""")

var_names = []
for i, (sku, info, exp, bc) in enumerate(selected):
    var  = f'P{i+1:03d}'
    dept = info['dept'].replace("'", "").replace('"', '')
    name = clean_name(sku, info['desc'], dept)
    name = name.replace("'", "").replace('"', '').replace('\\', '')[:60]
    desc = f"{dept} - {name}"[:80].replace("'", "").replace('"', '')
    erp  = f"ERP-{sku}"
    var_names.append((var, sku, exp, bc))
    lines.append(f'{var}=$(created_id "$(make_product "{sku}" "{name}" "{dept}" "{desc}" "{erp}")" "Product: {sku} [{dept}]")')

# ── STEP 4: EPC Tags ──────────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 4 — Register EPC / Barcodes
# =============================================================================
step "Registering Barcodes as EPC tags"

register_epcs() {
  local pid="$1"; shift
  if [[ -z "$pid" ]]; then return; fi
  local sku="$1"; shift
  local count=0
  for epc in "$@"; do
    curl -s -X POST "$GATEWAY/api/products/$pid/epc?epc=$epc" \\
      -H "Authorization: Bearer $TOKEN" --max-time 10 > /dev/null
    (( count++ )) || true
  done
  ok "  $sku — $count EPC tags registered"
}
""")

for var, sku, exp, bc in var_names:
    limited = bc[:MAX_EPC_PER_STYLE]
    epc_args = ' '.join(f'"{e}"' for e in limited)
    lines.append(f'[[ -n "${var}" ]] && register_epcs "${var}" "{sku}" {epc_args}')

# Re-login after potentially long EPC step (JWT expires in 15 min)
lines.append("""
# EPC registration can take >15 min; refresh token before continuing
relogin
""")

# ── STEP 5: Users ─────────────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 5 — Users
# =============================================================================
step "Creating Store Users"

make_user() {
  post /api/users "{
    \\"username\\":\\"$1\\",\\"password\\":\\"$2\\",
    \\"firstName\\":\\"$3\\",\\"lastName\\":\\"$4\\",
    \\"email\\":\\"$5\\",\\"roles\\":[\\"$6\\"],
    \\"storeId\\":\\"$STORE_ID\\"
  }"
}

[[ -n "$STORE_ID" ]] && {
  created_id "$(make_user mgr_p037   "Manager@P0371!"  "Ravi"    "Sharma"   "r.sharma@pantaloons.demo"   STORE_MANAGER)"    "User: mgr_p037 (Manager)"
  created_id "$(make_user asc_p037   "Assoc@P0371!"    "Priya"   "Nair"     "p.nair@pantaloons.demo"     STORE_ASSOCIATE)"  "User: asc_p037 (Associate)"
  created_id "$(make_user rfl_p037   "Refill@P0371!"   "Arjun"   "Mehta"    "a.mehta@pantaloons.demo"    REFILL_ASSOCIATE)" "User: rfl_p037 (Refill)"
  created_id "$(make_user asc2_p037  "Assoc2@P0371!"   "Sunita"  "Patil"    "s.patil@pantaloons.demo"    STORE_ASSOCIATE)"  "User: asc2_p037 (Associate)"
}
""")

# ── STEP 6: Expected Inventory ────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 6 — ERP Expected Inventory
# =============================================================================
step "Setting Expected Inventory"

set_expected() {
  local pid="$1" qty="$2" label="$3"
  if [[ -z "$pid" || "$qty" -le 0 ]]; then return; fi
  local resp
  resp=$(post /api/inventory/expected "{
    \\"storeId\\":\\"$STORE_ID\\",
    \\"productId\\":\\"$pid\\",
    \\"quantityExpected\\":$qty,
    \\"source\\":\\"erp_sync\\"
  }")
  local ok_flag; ok_flag=$(extract "$resp" '.success')
  if [[ "$ok_flag" == "true" ]]; then
    ok "  Expected $qty × $label"
  else
    info "  Expected $qty × $label — $(extract "$resp" '.message')"
  fi
}

[[ -n "$STORE_ID" ]] && {""")

for var, sku, exp, bc in var_names:
    if exp > 0:
        lines.append(f'  [[ -n "${var}" ]] && set_expected "${var}" {exp} "{sku}"')

lines.append("}")

# ── STEP 7: SOH Session ───────────────────────────────────────────────────────
# Pick first 12 barcodes from first 12 selected styles for the RFID batch
rfid_reads = []
for var, sku, exp, bc in var_names[:12]:
    rfid_reads.append(f'      {{"epc":"{bc[0]}","rssi":-{60 + len(rfid_reads) % 15:.1f},"antennaPort":{len(rfid_reads) % 4}}}')

reads_json = ',\n'.join(rfid_reads)

lines.append(f"""
# =============================================================================
# STEP 7 — SOH Session (simulate weekly count)
# =============================================================================
step "Creating SOH Session"

if [[ -n "$STORE_ID" ]]; then
  SOH_RESP=$(post /api/soh/sessions '{{
    "storeId":"'"$STORE_ID"'",
    "sessionType":"full_store",
    "notes":"Weekly SOH count - Pantaloons P037 demo seed"
  }}')
  SID=$(extract "$SOH_RESP" '.data.id')
  if [[ -n "$SID" && "$SID" != "null" ]]; then
    ok "SOH session created → $SID"

    BATCH=$(post /api/rfid/ingest/batch '{{
      "rfidSessionId":"'"$SID"'",
      "storeId":"'"$STORE_ID"'",
      "deviceId":"demo-seed-scanner",
      "reads":[
{reads_json}
      ]
    }}')
    info "  RFID batch published: $(extract "$BATCH" '.data.published') reads"
    sleep 3

    DONE=$(post "/api/soh/sessions/$SID/complete" '{{}}')
    ACC=$(extract "$DONE" '.data.accuracyPct')
    ok "  Session completed — accuracy: ${{ACC}}%"
  else
    fail "SOH session: $(extract "$SOH_RESP" '.message')"
  fi
fi
""")

# Refresh token before refill tasks (Steps 5-7 may have pushed past 15 min)
lines.append("""
relogin
""")

# ── STEP 8: Refill Tasks ──────────────────────────────────────────────────────
lines.append("""
# =============================================================================
# STEP 8 — Refill Tasks
# =============================================================================
step "Creating Refill Tasks"

create_refill() {
  local pid="$1" qty="$2" prio="$3" notes="$4" label="$5"
  if [[ -z "$pid" || -z "$STORE_ID" ]]; then return; fi
  local resp
  resp=$(post /api/refill/tasks "{
    \\"storeId\\":\\"$STORE_ID\\",
    \\"taskType\\":\\"replenishment\\",
    \\"priority\\":$prio,
    \\"source\\":\\"manual\\",
    \\"notes\\":\\"$notes\\",
    \\"items\\":[{\\"productId\\":\\"$pid\\",\\"requestedQuantity\\":$qty}]
  }")
  local tid; tid=$(extract "$resp" '.data.id')
  if [[ -n "$tid" && "$tid" != "null" ]]; then
    ok "Refill: $label (priority $prio) → $tid"
  else
    fail "Refill $label: $(extract "$resp" '.message')"
  fi
}
""")

# Pick 8 refill tasks from highest expected qty items across different depts
refill_candidates = sorted(var_names, key=lambda x: -x[2])
used_depts = set()
refill_items = []
for var, sku, exp, bc in refill_candidates:
    dept = styles[sku]['dept']
    if dept not in used_depts and exp > 0:
        used_depts.add(dept)
        refill_items.append((var, sku, exp, dept))
    if len(refill_items) >= 8:
        break

for idx, (var, sku, exp, dept) in enumerate(refill_items):
    qty  = max(5, min(20, exp // 5))
    prio = (idx % 5) + 1
    note = f"Restock from backroom - {dept}"
    lines.append(f'[[ -n "${var}" ]] && create_refill "${var}" {qty} {prio} "{note}" "{sku} ({dept})"')

# ── DONE ──────────────────────────────────────────────────────────────────────
brand_list = ' · '.join(sorted(by_dept.keys()))
lines.append(f"""
# =============================================================================
echo ""
echo -e "${{BOLD}}${{GREEN}}══════════════════════════════════════════════════${{RESET}}"
echo -e "${{BOLD}}${{GREEN}}  Pantaloons P037 demo data seeded!${{RESET}}"
echo -e "${{BOLD}}${{GREEN}}══════════════════════════════════════════════════${{RESET}}"
echo ""
echo -e "  ${{BOLD}}Store:${{RESET}}      P037 – Pantaloons"
echo -e "  ${{BOLD}}Products:${{RESET}}   {len(selected)} styles across {len(by_dept)} brands"
echo -e "  ${{BOLD}}Brands:${{RESET}}     {brand_list}"
echo -e "  ${{BOLD}}Users:${{RESET}}"
echo -e "    mgr_p037  / Manager@P0371!  → Store Manager"
echo -e "    asc_p037  / Assoc@P0371!   → Store Associate"
echo -e "    rfl_p037  / Refill@P0371!  → Refill Associate"
echo -e "    asc2_p037 / Assoc2@P0371!  → Store Associate"
echo ""
echo -e "  ${{BOLD}}Login:${{RESET}}  http://$(echo "$GATEWAY" | sed 's|http://||; s|:8080||'):3000"
echo ""
""")

out_path = r'd:\StoreLense\tools\seed_pantaloons_p037.sh'
with open(out_path, 'w', newline='\n', encoding='utf-8') as f:   # LF line endings for Linux
    f.write('\n'.join(lines))
print(f'Written: {out_path}')
print(f'Products: {len(selected)} | Brands: {len(by_dept)} | EPC registrations: {sum(min(len(bc), MAX_EPC_PER_STYLE) for _,_,_,bc in var_names)}')
