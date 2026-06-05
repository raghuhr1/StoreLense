# StoreLense — ERP Data Upload Guide

Upload ERP Stock on Hand (SOH) expected quantities and Product master data from XLS files so RFID scan results can be compared against them.

---

## Quick Start

```bash
# 1. Install dependencies
pip install requests openpyxl

# 2. Generate the XLS templates
python tools/upload_erp_data.py --create-templates

# 3. Fill in your data, then upload
python tools/upload_erp_data.py \
  --url      http://<server>:8080 \
  --username admin \
  --password Admin@StoreLense1 \
  --products  product_list_template.xlsx \
  --inventory erp_soh_inventory_template.xlsx
```

---

## XLS File Formats

---

### File 1 — Product List (`product_list_template.xlsx`)

Sheet name: **Products**

| Column | Required | Type | Example | Description |
|---|---|---|---|---|
| `product_code` | YES | Text | `ERP-P-00123` | Your ERP's product identifier — used to link StoreLense products back to ERP records |
| `sku` | YES | Text | `APP-DNM-001` | Unique SKU code — must be unique across all products |
| `name` | YES | Text | `Classic Denim Jeans` | Product display name |
| `description` | no | Text | `Regular fit blue denim` | Optional product description |
| `category` | no | Text | `APPAREL` | One of: `APPAREL`, `FOOTWEAR`, `ACCESSORIES`, `HOMEWARES`, `ELECTRONICS` |
| `brand` | no | Text | `StoreLense Basic` | Brand name |
| `uom` | no | Text | `EACH` | Unit of measure: `EACH`, `PAIR`, `BOX`, `ROLL` (default: EACH) |
| `weight_grams` | no | Integer | `650` | Weight in grams |
| `rfid_enabled` | YES | TRUE/FALSE | `TRUE` | Whether this product has RFID tags |
| `epc_tags` | no | Text | `3034257BF400B71400000001,3034257BF400B71400000002` | Comma-separated EPC hex values for this product's tags |

**Sample rows:**

```
product_code   sku           name                    rfid_enabled  epc_tags
ERP-P-00001    APP-DNM-001   Classic Denim Jeans     TRUE          3034257BF400B71400000001,3034257BF400B71400000002
ERP-P-00002    APP-TEE-001   Cotton Crew Tee White   TRUE          3034257BF400B71400000010
ERP-P-00003    FTW-SNK-001   Classic White Sneaker   TRUE          3034257BF400B71400000030
ERP-P-00004    ACC-BAG-001   Canvas Tote Bag         FALSE
```

**Rules:**
- Header row must be row 1 — column order does not matter
- Rows with empty `sku` are skipped
- If a SKU already exists, the product is **updated** (upsert behaviour)
- EPC tags are associated one by one; duplicate EPCs return 409 and are skipped safely

---

### File 2 — ERP SOH Expected Inventory (`erp_soh_inventory_template.xlsx`)

Sheet name: **ERP_SOH_Expected**

| Column | Required | Type | Example | Description |
|---|---|---|---|---|
| `store_code` | YES | Text | `SYD001` | Store code — must exactly match the Store Code in StoreLense |
| `product_code` | no | Text | `ERP-P-00001` | ERP product code (informational — product resolved via SKU) |
| `sku` | YES | Text | `APP-DNM-001` | Product SKU — must exist in StoreLense (upload products first) |
| `qty_expected` | YES | Integer | `50` | How many units the ERP expects to be in this store/zone |
| `zone_code` | no | Text | `FLOOR-A` | Zone code — must match a zone configured in StoreLense. Leave blank for store-level total |
| `valid_date` | no | Date | `2026-06-05` | Date this data is valid for (YYYY-MM-DD). Default: today |
| `notes` | no | Text | `post-delivery adjustment` | Optional notes |

**Sample rows:**

```
store_code  sku           qty_expected  zone_code    valid_date   notes
SYD001      APP-DNM-001   50            FLOOR-A      2026-06-05
SYD001      APP-DNM-001   20            BACKROOM-1   2026-06-05
SYD001      APP-TEE-001   100           FLOOR-A      2026-06-05   post-receiving
MEL001      APP-DNM-001   45            FLOOR-A      2026-06-05
MEL001      APP-TEE-001   90            FLOOR-A      2026-06-05
```

**Rules:**
- Multiple rows per store+SKU are allowed — one per zone
- Leave `zone_code` blank for a store-level total (no zone breakdown)
- `qty_expected` must be a non-negative integer
- If a store+product+zone row already exists, `qty_expected` is **updated** (upsert)
- Products must exist in StoreLense before inventory can be uploaded — run product upload first

---

## How the Data Is Used

```
ERP XLS file
    │
    ▼
upload_erp_data.py
    │
    ├─ POST /api/products           → creates/updates product master
    ├─ POST /api/products/{id}/epc  → links EPC tags to products
    └─ POST /api/inventory/expected → sets quantity_expected in inventory_state
                                          │
                                          ▼
                              inventory.inventory_state
                              ┌─────────────────────────────┐
                              │ store_id      = SYD001       │
                              │ product_id    = <uuid>       │
                              │ zone_id       = FLOOR-A      │
                              │ quantity_expected = 50  ◄────┤ from XLS upload
                              │ quantity_on_hand  = 48  ◄────┤ from RFID scan
                              │ accuracy_pct      = 96.00    │
                              └─────────────────────────────┘
                                          │
                                          ▼
                              GET /api/inventory/state
                              GET /api/inventory/low-accuracy
                              Reports → accuracy_pct charts
```

The `quantity_on_hand` is updated by RFID scanning. The `quantity_expected` comes from ERP (via XLS or live sync). The ratio between them is the **inventory accuracy %** shown on the dashboard.

---

## API Endpoints Called by the Script

### Product Upload

```http
# Create product
POST /api/products
Authorization: Bearer <token>
Content-Type: application/json

{
  "sku":            "APP-DNM-001",
  "name":           "Classic Denim Jeans",
  "erpProductCode": "ERP-P-00001",
  "brand":          "StoreLense Basic",
  "unitOfMeasure":  "EACH",
  "weightGrams":    650,
  "rfidEnabled":    true
}

# Response 201
{ "data": { "id": "<uuid>", "sku": "APP-DNM-001", ... } }
```

```http
# Link EPC tag to product
POST /api/products/{productId}/epc?epc=3034257BF400B71400000001
Authorization: Bearer <token>

# Response 201
{ "data": null }
```

```http
# Update existing product (upsert)
PUT /api/products/{productId}
Authorization: Bearer <token>
Content-Type: application/json

{ "name": "Updated Name", "rfidEnabled": true }
```

### Expected Inventory Upload

```http
POST /api/inventory/expected
Authorization: Bearer <token>
Content-Type: application/json

{
  "storeId":          "a1b2c3d4-...",
  "productId":        "p1p2p3p4-...",
  "zoneId":           "z1z2z3z4-...",   ← null for store-level
  "quantityExpected": 50,
  "validDate":        "2026-06-05",
  "notes":            "from XLS import",
  "source":           "xls_import"
}

# Response 200/201
{ "data": { "storeId": "...", "productId": "...", "quantityExpected": 50, ... } }
```

---

## All Script Options

```
usage: upload_erp_data.py [options]

Connection:
  --url        API Gateway URL        [env: STORELENSE_URL, default: http://localhost:8080]
  --username   Admin username          [env: STORELENSE_USER, default: admin]
  --password   Admin password          [env: STORELENSE_PASS, default: Admin@StoreLense1]

Actions:
  --create-templates   Generate sample XLS files (product_list_template.xlsx
                       and erp_soh_inventory_template.xlsx) then exit
  --products FILE      Upload product list from XLS
  --inventory FILE     Upload ERP expected inventory (SOH) from XLS
  --export-sql FILE    Instead of calling API, generate a SQL script for
                       direct database import
  --dry-run            Validate files and print actions without writing anything
```

### Environment Variables (avoid typing credentials every time)

```bash
export STORELENSE_URL=http://150.241.247.238:8080
export STORELENSE_USER=admin
export STORELENSE_PASS=MySecurePassword

python tools/upload_erp_data.py --products products.xlsx --inventory inventory.xlsx
```

---

## Direct Database Import (Alternative to API)

If the API endpoint is not available, use `--export-sql` to generate SQL that updates the database directly:

```bash
# Generate SQL file
python tools/upload_erp_data.py \
  --inventory erp_soh_inventory_template.xlsx \
  --export-sql import_expected_qty.sql

# Copy SQL to server and run
scp import_expected_qty.sql ubuntu@150.241.247.238:/opt/storelense/

# On the server:
docker exec -i deploy-postgres-1 psql \
  -U storelense_app -d storelense \
  < /opt/storelense/import_expected_qty.sql
```

**What the generated SQL does:**

```sql
BEGIN;

-- SYD001 / APP-DNM-001 / zone=FLOOR-A  qty=50
INSERT INTO inventory.inventory_state
  (store_id, product_id, zone_id, quantity_expected, quantity_on_hand, updated_at)
VALUES
  ('a1b2c3d4-...', 'p1p2p3p4-...', 'z1z2z3z4-...', 50, 0, now())
ON CONFLICT (store_id, product_id, zone_id) DO UPDATE
  SET quantity_expected = EXCLUDED.quantity_expected,
      accuracy_pct      = ROUND(100.0 * quantity_on_hand / NULLIF(quantity_expected, 0), 2),
      updated_at        = now();

COMMIT;
```

The `ON CONFLICT ... DO UPDATE` (upsert) means:
- If the store+product+zone row **does not exist** → it is created with `quantity_expected` set and `quantity_on_hand = 0`
- If it **already exists** (e.g. previous RFID scan data) → only `quantity_expected` is updated; `quantity_on_hand` from RFID scans is preserved

---

## Verifying the Upload

After uploading, check the data through the API or database:

```bash
# Via API
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

STORE_ID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stores \
  | python3 -c "import sys,json; stores=json.load(sys.stdin)['data']['content']; print(next(s['id'] for s in stores if s['storeCode']=='SYD001'))")

# View inventory state (shows qty_expected vs qty_on_hand)
curl -s "http://localhost:8080/api/inventory/state?storeId=$STORE_ID" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# View products below 95% accuracy
curl -s "http://localhost:8080/api/inventory/low-accuracy?storeId=$STORE_ID&threshold=95" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

```bash
# Via direct DB query
docker exec -it deploy-postgres-1 psql -U storelense_app -d storelense -c "
SELECT
  s.store_code,
  p.sku,
  p.name,
  z.name        AS zone,
  i.quantity_expected,
  i.quantity_on_hand,
  i.accuracy_pct,
  i.updated_at
FROM inventory.inventory_state i
JOIN stores.stores   s ON s.id = i.store_id
JOIN products.products p ON p.id = i.product_id
LEFT JOIN stores.zones z ON z.id = i.zone_id
WHERE s.store_code = 'SYD001'
ORDER BY p.sku, z.name;
"
```

---

## End-to-End Workflow

```
1. Export product list from ERP as XLS
         ↓
2. Export expected inventory quantities from ERP as XLS
         ↓
3. python upload_erp_data.py --products products.xlsx
   → Products created in StoreLense
   → EPC tags linked to products
         ↓
4. python upload_erp_data.py --inventory inventory.xlsx
   → quantity_expected set per store × product × zone
         ↓
5. RFID scan session started (Zebra app or web)
   → RFID reads uploaded via POST /api/rfid/ingest/batch
   → quantity_on_hand updated by rfid-processing-service
         ↓
6. POST /api/soh/sessions/{id}/complete
   → accuracy_pct = (quantity_on_hand / quantity_expected) × 100
   → variance rows created for mismatched products
         ↓
7. GET /api/inventory/state         → see accuracy per product
   GET /api/inventory/low-accuracy  → see products below 95%
   GET /api/reporting/kpi/range     → see accuracy trend over time
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `store_code 'SYD001' not found` | Store not configured in StoreLense | Create the store via Admin → Stores first |
| `SKU 'APP-DNM-001' not found` | Product not in StoreLense | Run product upload first (`--products`) |
| `zone_code 'FLOOR-A' not found` | Zone not configured | Create zone in Admin → Stores → Zones |
| `qty_expected is not an integer` | XLS cell formatted as text | Format the column as Number in Excel |
| `EPC format error` | EPC not valid hex | EPCs must be uppercase hex, 16–128 chars |
| `Login failed 401` | Wrong credentials | Check `--username` and `--password` |
| `Connection refused` | Wrong URL | Check `--url` matches your server IP |
| `403 Forbidden` | Not ADMIN role | Only ADMIN users can upload products |
