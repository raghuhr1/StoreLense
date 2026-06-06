#!/usr/bin/env bash
# =============================================================================
#  StoreLense — Demo Data Seeder
#  Creates realistic demo stores, users, products, inventory, SOH sessions
#  and refill tasks so the app shows meaningful data on first launch.
#
#  Usage:
#    bash tools/seed_demo_data.sh
#    bash tools/seed_demo_data.sh --url http://150.241.247.238:8080
#    bash tools/seed_demo_data.sh --url http://localhost:8080 --password MyPass1!
#
#  Requirements: bash, curl, jq
# =============================================================================
set -euo pipefail

# ── Defaults (override via flags) ─────────────────────────────────────────────
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

# ── Colours ───────────────────────────────────────────────────────────────────
GREEN='\033[92m'; RED='\033[91m'; YELLOW='\033[93m'
CYAN='\033[96m'; BOLD='\033[1m'; RESET='\033[0m'
ok()   { echo -e "  ${GREEN}✔${RESET}  $*"; }
fail() { echo -e "  ${RED}✘${RESET}  $*"; }
info() { echo -e "  ${CYAN}→${RESET}  $*"; }
step() { echo -e "\n${BOLD}${CYAN}▶ $*${RESET}"; }

# ── Helpers ───────────────────────────────────────────────────────────────────
post() {
  local path="$1" body="$2"
  curl -s -X POST "$GATEWAY$path" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "$body" --max-time 15
}

get() {
  local path="$1"
  curl -s "$GATEWAY$path" \
    -H "Authorization: Bearer $TOKEN" \
    --max-time 15
}

extract() { echo "$1" | jq -r "$2" 2>/dev/null || echo ""; }

created_id() {
  local resp="$1" label="$2"
  local id
  id=$(extract "$resp" '.data.id')
  if [[ -z "$id" || "$id" == "null" ]]; then
    local msg
    msg=$(extract "$resp" '.message')
    fail "$label — $msg"
    echo ""
  else
    ok "$label → $id"
    echo "$id"
  fi
}

# ── Banner ────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${CYAN}  StoreLense Demo Data Seeder${RESET}"
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
  echo ""
  echo "Make sure the app is running:  docker compose ps"
  echo "Or check credentials with:     bash deploy/test.ps1"
  exit 1
fi
ok "Logged in — token received"

# =============================================================================
# STEP 1 — Stores
# =============================================================================
step "Creating Stores"

SYD_ID=$(created_id "$(post /api/stores '{
  "storeCode":   "PT001",
  "name":        "BNG ORION ",
  "addressLine1":"100 COMERCIAL Street",
  "city":        "BANGALORE",
  "stateProvince":"NSW",
  "postalCode":  "2000",
  "countryCode": "IN",
  "timezone":    "Australia/Sydney",
  "erpStoreCode":"ERP-SYD-001"
}')" "Store: Sydney CBD")

MEL_ID=$(created_id "$(post /api/stores '{
  "storeCode":   "MEL001",
  "name":        "Melbourne Central",
  "addressLine1":"211 La Trobe Street",
  "city":        "Melbourne",
  "stateProvince":"VIC",
  "postalCode":  "3000",
  "countryCode": "AU",
  "timezone":    "Australia/Melbourne",
  "erpStoreCode":"ERP-MEL-001"
}')" "Store: Melbourne Central")

BNE_ID=$(created_id "$(post /api/stores '{
  "storeCode":   "BNE001",
  "name":        "Brisbane Queen Street",
  "addressLine1":"Queen Street Mall",
  "city":        "Brisbane",
  "stateProvince":"QLD",
  "postalCode":  "4000",
  "countryCode": "AU",
  "timezone":    "Australia/Brisbane",
  "erpStoreCode":"ERP-BNE-001"
}')" "Store: Brisbane Queen Street")

# =============================================================================
# STEP 2 — Zones (per store)
# =============================================================================
step "Creating Zones"

create_zones() {
  local store_id="$1" store_name="$2"
  post "/api/stores/$store_id/zones" '{"zoneCode":"FLOOR-A","name":"Ground Floor","zoneType":"floor","displayOrder":1}'       > /dev/null
  post "/api/stores/$store_id/zones" '{"zoneCode":"FLOOR-B","name":"Level 1","zoneType":"floor","displayOrder":2}'            > /dev/null
  post "/api/stores/$store_id/zones" '{"zoneCode":"FITTING","name":"Fitting Rooms","zoneType":"fitting_room","displayOrder":3}' > /dev/null
  post "/api/stores/$store_id/zones" '{"zoneCode":"BACKROOM","name":"Backroom","zoneType":"backroom","displayOrder":4}'        > /dev/null
  post "/api/stores/$store_id/zones" '{"zoneCode":"STOCKROOM","name":"Stockroom","zoneType":"stockroom","displayOrder":5}'     > /dev/null
  ok "Zones created for $store_name"
}

[[ -n "$SYD_ID" ]] && create_zones "$SYD_ID" "Sydney CBD"
[[ -n "$MEL_ID" ]] && create_zones "$MEL_ID" "Melbourne Central"
[[ -n "$BNE_ID" ]] && create_zones "$BNE_ID" "Brisbane Queen Street"

# =============================================================================
# STEP 3 — Products
# =============================================================================
step "Creating Products"

make_product() {
  local sku="$1" name="$2" brand="$3" uom="$4" rfid="$5" erp="$6" desc="$7"
  post /api/products "{
    \"sku\":\"$sku\",\"name\":\"$name\",\"description\":\"$desc\",
    \"brand\":\"$brand\",\"unitOfMeasure\":\"$uom\",
    \"rfidEnabled\":$rfid,\"erpProductCode\":\"$erp\"
  }"
}

P1=$(created_id "$(make_product "APP-DNM-BLU-001" "Classic Blue Denim Jeans"    "StoreLense Basic" "EACH" "true"  "ERP-P-00001" "Regular fit, 100% cotton denim")"     "Product: Denim Jeans Blue")
P2=$(created_id "$(make_product "APP-DNM-BLK-001" "Black Skinny Jeans"          "StoreLense Basic" "EACH" "true"  "ERP-P-00002" "Skinny fit, black stretch denim")"   "Product: Skinny Jeans Black")
P3=$(created_id "$(make_product "APP-TEE-WHT-001" "White Cotton Crew Tee"       "StoreLense Basic" "EACH" "true"  "ERP-P-00003" "100% cotton crew neck t-shirt")"     "Product: Cotton Tee White")
P4=$(created_id "$(make_product "APP-TEE-BLK-001" "Black Cotton Crew Tee"       "StoreLense Basic" "EACH" "true"  "ERP-P-00004" "100% cotton crew neck t-shirt")"     "Product: Cotton Tee Black")
P5=$(created_id "$(make_product "APP-POL-NAV-001" "Navy Polo Shirt"             "StoreLense Basic" "EACH" "true"  "ERP-P-00005" "Pique cotton polo shirt")"           "Product: Polo Shirt Navy")
P6=$(created_id "$(make_product "APP-HOD-GRY-001" "Grey Pullover Hoodie"        "StoreLense Sport" "EACH" "true"  "ERP-P-00006" "Fleece lined, kangaroo pocket")"     "Product: Hoodie Grey")
P7=$(created_id "$(make_product "APP-JKT-KHA-001" "Khaki Chino Jacket"          "StoreLense Basic" "EACH" "true"  "ERP-P-00007" "Cotton chino, button front")"        "Product: Jacket Khaki")
P8=$(created_id "$(make_product "FTW-SNK-WHT-001" "White Canvas Sneaker"        "StoreLense Sport" "PAIR" "true"  "ERP-P-00008" "Lace-up canvas low top")"            "Product: Sneaker White")
P9=$(created_id "$(make_product "FTW-RNR-BLK-001" "Black Running Shoe"          "StoreLense Sport" "PAIR" "true"  "ERP-P-00009" "Breathable mesh, cushioned sole")"   "Product: Runner Black")
P10=$(created_id "$(make_product "FTW-SND-TAN-001" "Tan Leather Sandal"          "StoreLense Basic" "PAIR" "true"  "ERP-P-00010" "Genuine leather upper")"             "Product: Sandal Tan")
P11=$(created_id "$(make_product "ACC-BAG-TAN-001" "Canvas Tote Bag"             "StoreLense Basic" "EACH" "false" "ERP-P-00011" "Reusable canvas tote")"              "Product: Tote Bag Tan")
P12=$(created_id "$(make_product "ACC-CAP-BLK-001" "Black Snapback Cap"          "StoreLense Sport" "EACH" "true"  "ERP-P-00012" "Adjustable snapback, embroidered")"  "Product: Cap Black")
P13=$(created_id "$(make_product "HMW-TWL-WHT-001" "White Bath Towel 600gsm"     "StoreLense Home"  "EACH" "false" "ERP-P-00013" "Premium 600gsm cotton towel")"       "Product: Towel White")
P14=$(created_id "$(make_product "APP-SCK-PKG-001" "Cotton Socks 5-Pack"         "StoreLense Basic" "PACK" "true"  "ERP-P-00014" "Ankle socks, 5 pairs per pack")"     "Product: Socks 5pk")
P15=$(created_id "$(make_product "APP-SHT-STR-001" "Striped Linen Shirt"         "StoreLense Basic" "EACH" "true"  "ERP-P-00015" "Linen blend, relaxed fit")"          "Product: Linen Shirt Stripe")

# ── EPC tags for RFID-enabled products ───────────────────────────────────────
step "Registering EPC Tags"

register_epcs() {
  local pid="$1" prefix="$2" count="$3" sku="$4"
  if [[ -z "$pid" ]]; then return; fi
  local i
  for i in $(seq 1 "$count"); do
    local epc
    epc=$(printf "%s%08d" "$prefix" "$i")
    curl -s -X POST "$GATEWAY/api/products/$pid/epc?epc=$epc" \
      -H "Authorization: Bearer $TOKEN" --max-time 10 > /dev/null
  done
  ok "EPCs registered for $sku ($count tags)"
}

register_epcs "$P1"  "3034001BF400B714" 60 "Denim Jeans Blue"
register_epcs "$P2"  "3034002BF400B714" 45 "Skinny Jeans Black"
register_epcs "$P3"  "3034003BF400B714" 120 "Cotton Tee White"
register_epcs "$P4"  "3034004BF400B714" 100 "Cotton Tee Black"
register_epcs "$P5"  "3034005BF400B714" 80 "Polo Shirt Navy"
register_epcs "$P6"  "3034006BF400B714" 70 "Hoodie Grey"
register_epcs "$P7"  "3034007BF400B714" 40 "Jacket Khaki"
register_epcs "$P8"  "3034008BF400B714" 55 "Sneaker White"
register_epcs "$P9"  "3034009BF400B714" 50 "Runner Black"
register_epcs "$P10" "3034010BF400B714" 30 "Sandal Tan"
register_epcs "$P12" "3034012BF400B714" 90 "Cap Black"
register_epcs "$P14" "3034014BF400B714" 110 "Socks 5pk"
register_epcs "$P15" "3034015BF400B714" 60 "Linen Shirt Stripe"

# =============================================================================
# STEP 4 — Users
# =============================================================================
step "Creating Users"

make_user() {
  local user pass first last email role store_arg
  user="$1"; pass="$2"; first="$3"; last="$4"; email="$5"; role="$6"; store_arg="$7"
  post /api/users "{
    \"username\":\"$user\",\"password\":\"$pass\",
    \"firstName\":\"$first\",\"lastName\":\"$last\",
    \"email\":\"$email\",\"roles\":[\"$role\"]
    $store_arg
  }"
}

# Sydney store users
SYD_MGR=$(created_id "$(make_user "mgr_syd" "Manager@Syd1!" "Sarah"  "Chen"   "s.chen@storelense.demo"   "STORE_MANAGER"   ",\"storeId\":\"$SYD_ID\"")" "User: mgr_syd (STORE_MANAGER)")
SYD_ASC=$(created_id "$(make_user "asc_syd" "Assoc@Syd1!"   "James"  "Wilson" "j.wilson@storelense.demo"  "STORE_ASSOCIATE"  ",\"storeId\":\"$SYD_ID\"")" "User: asc_syd (STORE_ASSOCIATE)")
SYD_RFL=$(created_id "$(make_user "rfl_syd" "Refill@Syd1!"  "Emma"   "Nguyen" "e.nguyen@storelense.demo"  "REFILL_ASSOCIATE" ",\"storeId\":\"$SYD_ID\"")" "User: rfl_syd (REFILL_ASSOCIATE)")

# Melbourne store users
MEL_MGR=$(created_id "$(make_user "mgr_mel" "Manager@Mel1!" "Michael" "Patel"  "m.patel@storelense.demo"   "STORE_MANAGER"   ",\"storeId\":\"$MEL_ID\"")" "User: mgr_mel (STORE_MANAGER)")
MEL_ASC=$(created_id "$(make_user "asc_mel" "Assoc@Mel1!"   "Jessica" "Brown"  "j.brown@storelense.demo"   "STORE_ASSOCIATE"  ",\"storeId\":\"$MEL_ID\"")" "User: asc_mel (STORE_ASSOCIATE)")
MEL_RFL=$(created_id "$(make_user "rfl_mel" "Refill@Mel1!"  "Daniel"  "Smith"  "d.smith@storelense.demo"   "REFILL_ASSOCIATE" ",\"storeId\":\"$MEL_ID\"")" "User: rfl_mel (REFILL_ASSOCIATE)")

# Brisbane store users
BNE_MGR=$(created_id "$(make_user "mgr_bne" "Manager@Bne1!" "Lisa"   "Zhang"  "l.zhang@storelense.demo"   "STORE_MANAGER"   ",\"storeId\":\"$BNE_ID\"")" "User: mgr_bne (STORE_MANAGER)")
BNE_ASC=$(created_id "$(make_user "asc_bne" "Assoc@Bne1!"   "Ryan"   "Kim"    "r.kim@storelense.demo"     "STORE_ASSOCIATE"  ",\"storeId\":\"$BNE_ID\"")" "User: asc_bne (STORE_ASSOCIATE)")

# =============================================================================
# STEP 5 — Expected Inventory (ERP SOH data for accuracy comparison)
# =============================================================================
step "Setting ERP Expected Inventory"

set_expected() {
  local store_id="$1" product_id="$2" qty="$3" label="$4"
  if [[ -z "$store_id" || -z "$product_id" ]]; then return; fi
  local resp
  resp=$(post /api/inventory/expected "{
    \"storeId\":\"$store_id\",
    \"productId\":\"$product_id\",
    \"quantityExpected\":$qty,
    \"source\":\"demo_seed\"
  }")
  local success
  success=$(extract "$resp" '.success')
  if [[ "$success" == "true" ]]; then
    ok "Expected qty=$qty → $label"
  else
    info "Expected qty=$qty → $label ($(extract "$resp" '.message'))"
  fi
}

# Sydney expected quantities
[[ -n "$P1"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P1"  50  "SYD / Denim Jeans Blue"
[[ -n "$P2"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P2"  40  "SYD / Skinny Jeans Black"
[[ -n "$P3"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P3"  100 "SYD / Cotton Tee White"
[[ -n "$P4"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P4"  90  "SYD / Cotton Tee Black"
[[ -n "$P5"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P5"  70  "SYD / Polo Shirt Navy"
[[ -n "$P6"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P6"  60  "SYD / Hoodie Grey"
[[ -n "$P7"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P7"  30  "SYD / Jacket Khaki"
[[ -n "$P8"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P8"  45  "SYD / Sneaker White"
[[ -n "$P9"  && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P9"  40  "SYD / Runner Black"
[[ -n "$P10" && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P10" 25  "SYD / Sandal Tan"
[[ -n "$P12" && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P12" 80  "SYD / Cap Black"
[[ -n "$P14" && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P14" 95  "SYD / Socks 5pk"
[[ -n "$P15" && -n "$SYD_ID" ]] && set_expected "$SYD_ID" "$P15" 50  "SYD / Linen Shirt Stripe"

# Melbourne expected quantities
[[ -n "$P1"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P1"  45  "MEL / Denim Jeans Blue"
[[ -n "$P2"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P2"  35  "MEL / Skinny Jeans Black"
[[ -n "$P3"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P3"  85  "MEL / Cotton Tee White"
[[ -n "$P4"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P4"  80  "MEL / Cotton Tee Black"
[[ -n "$P5"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P5"  60  "MEL / Polo Shirt Navy"
[[ -n "$P8"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P8"  40  "MEL / Sneaker White"
[[ -n "$P9"  && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P9"  35  "MEL / Runner Black"
[[ -n "$P12" && -n "$MEL_ID" ]] && set_expected "$MEL_ID" "$P12" 70  "MEL / Cap Black"

# Brisbane expected quantities
[[ -n "$P3"  && -n "$BNE_ID" ]] && set_expected "$BNE_ID" "$P3"  70  "BNE / Cotton Tee White"
[[ -n "$P4"  && -n "$BNE_ID" ]] && set_expected "$BNE_ID" "$P4"  65  "BNE / Cotton Tee Black"
[[ -n "$P8"  && -n "$BNE_ID" ]] && set_expected "$BNE_ID" "$P8"  30  "BNE / Sneaker White"
[[ -n "$P15" && -n "$BNE_ID" ]] && set_expected "$BNE_ID" "$P15" 40  "BNE / Linen Shirt Stripe"

# =============================================================================
# STEP 6 — SOH Sessions (simulate completed counts with realistic accuracy)
# =============================================================================
step "Creating SOH Sessions"

create_soh_session() {
  local store_id="$1" label="$2"
  if [[ -z "$store_id" ]]; then return; fi

  local resp
  resp=$(post /api/soh/sessions "{
    \"storeId\":\"$store_id\",
    \"sessionType\":\"full_store\",
    \"notes\":\"Weekly count — demo data\"
  }")
  local sid
  sid=$(extract "$resp" '.data.id')
  if [[ -z "$sid" || "$sid" == "null" ]]; then
    fail "SOH session for $label: $(extract "$resp" '.message')"
    return
  fi
  ok "SOH session created for $label → $sid"

  # Simulate a small RFID batch upload so session has reads
  local batch_resp
  batch_resp=$(post /api/rfid/ingest/batch "{
    \"rfidSessionId\":\"$sid\",
    \"storeId\":\"$store_id\",
    \"deviceId\":\"demo-seed-scanner\",
    \"reads\":[
      {\"epc\":\"3034003BF400B71400000001\",\"rssi\":-65.0,\"antennaPort\":0},
      {\"epc\":\"3034003BF400B71400000002\",\"rssi\":-68.2,\"antennaPort\":0},
      {\"epc\":\"3034004BF400B71400000001\",\"rssi\":-71.0,\"antennaPort\":1},
      {\"epc\":\"3034004BF400B71400000002\",\"rssi\":-69.5,\"antennaPort\":1},
      {\"epc\":\"3034001BF400B71400000001\",\"rssi\":-67.0,\"antennaPort\":0},
      {\"epc\":\"3034005BF400B71400000001\",\"rssi\":-72.0,\"antennaPort\":1},
      {\"epc\":\"3034008BF400B71400000001\",\"rssi\":-64.0,\"antennaPort\":0},
      {\"epc\":\"3034012BF400B71400000001\",\"rssi\":-70.0,\"antennaPort\":1}
    ]
  }")
  local pub
  pub=$(extract "$batch_resp" '.data.published')
  info "  RFID batch: $pub reads published"

  # Brief pause for Kafka processing
  sleep 3

  # Complete the session
  local complete_resp
  complete_resp=$(post "/api/soh/sessions/$sid/complete" '{}')
  local acc
  acc=$(extract "$complete_resp" '.data.accuracyPct')
  ok "  Session completed — accuracy: ${acc}%"
}

[[ -n "$SYD_ID" ]] && create_soh_session "$SYD_ID" "Sydney CBD"
[[ -n "$MEL_ID" ]] && create_soh_session "$MEL_ID" "Melbourne Central"
[[ -n "$BNE_ID" ]] && create_soh_session "$BNE_ID" "Brisbane Queen Street"

# =============================================================================
# STEP 7 — Refill Tasks
# =============================================================================
step "Creating Refill Tasks"

create_refill() {
  local store_id="$1" type="$2" priority="$3" notes="$4" product_id="$5" qty="$6" label="$7"
  if [[ -z "$store_id" || -z "$product_id" ]]; then return; fi

  local resp
  resp=$(post /api/refill/tasks "{
    \"storeId\":\"$store_id\",
    \"taskType\":\"$type\",
    \"priority\":$priority,
    \"source\":\"manual\",
    \"notes\":\"$notes\",
    \"items\":[{\"productId\":\"$product_id\",\"requestedQuantity\":$qty}]
  }")
  local tid
  tid=$(extract "$resp" '.data.id')
  if [[ -z "$tid" || "$tid" == "null" ]]; then
    fail "Refill task $label: $(extract "$resp" '.message')"
  else
    ok "Refill task: $label (priority $priority) → $tid"
  fi
}

# Sydney refill tasks (mix of priorities and types)
[[ -n "$P1"  && -n "$SYD_ID" ]] && create_refill "$SYD_ID" "replenishment" 2 "Denim section low — restock from backroom" "$P1"  20 "SYD / Denim Jeans Restock"
[[ -n "$P3"  && -n "$SYD_ID" ]] && create_refill "$SYD_ID" "replenishment" 3 "White tees running low on floor"           "$P3"  30 "SYD / White Tee Restock"
[[ -n "$P8"  && -n "$SYD_ID" ]] && create_refill "$SYD_ID" "urgency"       1 "Weekend sale — sneakers almost out"        "$P8"  15 "SYD / Sneaker URGENT"
[[ -n "$P6"  && -n "$SYD_ID" ]] && create_refill "$SYD_ID" "replenishment" 5 "Hoodies — routine restock"                 "$P6"  25 "SYD / Hoodie Restock"

# Melbourne refill tasks
[[ -n "$P4"  && -n "$MEL_ID" ]] && create_refill "$MEL_ID" "replenishment" 3 "Black tees low after weekend"              "$P4"  25 "MEL / Black Tee Restock"
[[ -n "$P9"  && -n "$MEL_ID" ]] && create_refill "$MEL_ID" "urgency"       1 "Running shoes — stock-out risk"            "$P9"  10 "MEL / Runner URGENT"
[[ -n "$P5"  && -n "$MEL_ID" ]] && create_refill "$MEL_ID" "replenishment" 4 "Polo shirts — scheduled restock"           "$P5"  20 "MEL / Polo Restock"

# Brisbane refill task
[[ -n "$P15" && -n "$BNE_ID" ]] && create_refill "$BNE_ID" "replenishment" 3 "Linen shirts — new season stock"           "$P15" 20 "BNE / Linen Shirt Restock"

# =============================================================================
# DONE
# =============================================================================
echo ""
echo -e "${BOLD}${GREEN}══════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}${GREEN}  Demo data seeded successfully!${RESET}"
echo -e "${BOLD}${GREEN}══════════════════════════════════════════════════${RESET}"
echo ""
echo -e "  ${BOLD}Stores created:${RESET}   Sydney CBD · Melbourne Central · Brisbane Queen St"
echo -e "  ${BOLD}Products:${RESET}         15 products · RFID tags registered"
echo -e "  ${BOLD}Users:${RESET}"
echo -e "    mgr_syd / Manager\@Syd1!   → Store Manager, Sydney"
echo -e "    asc_syd / Assoc\@Syd1!    → Store Associate, Sydney"
echo -e "    rfl_syd / Refill\@Syd1!   → Refill Associate, Sydney"
echo -e "    mgr_mel / Manager\@Mel1!   → Store Manager, Melbourne"
echo -e "    asc_mel / Assoc\@Mel1!    → Store Associate, Melbourne"
echo -e "    rfl_mel / Refill\@Mel1!   → Refill Associate, Melbourne"
echo -e "    mgr_bne / Manager\@Bne1!   → Store Manager, Brisbane"
echo -e "    asc_bne / Assoc\@Bne1!    → Store Associate, Brisbane"
echo ""
echo -e "  ${BOLD}Login:${RESET}  http://$(echo "$GATEWAY" | sed 's|http://||; s|:8080||'):3000"
echo ""
