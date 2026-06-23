#!/usr/bin/env python3
"""
StoreLense — Product Catalog Import
=====================================
Reads an Excel file with columns:
  EPC | EAN | Description | Size | info

For each row:
  1. Creates a product  (EPC value = SKU, Description = name, info = brand)
  2. Associates the EAN barcode  (products.barcodes)
  3. Associates the EPC RFID tag (products.epc_tags)
  4. Sets expected quantity in the target store (inventory_state)

After import the Android app can scan RFID tags and SOH sessions will
show Expected vs Scanned correctly.

Usage:
  python tools/import_product_catalog.py --file tools/eyewear_soh_20260611.xlsx --store-code LK001
  python tools/import_product_catalog.py --file products.xlsx --store-code LK001 --qty 2
  python tools/import_product_catalog.py --file products.xlsx --store-code LK001 --dry-run
  python tools/import_product_catalog.py --file products.xlsx --store-code LK001 --sql-out import.sql

Column name matching is case-insensitive and handles common variations.

Requirements:
  pip install requests openpyxl
"""

import argparse
import json
import os
import sys
import time
import uuid as _uuid
from datetime import date

try:
    import requests
except ImportError:
    sys.exit("Missing: pip install requests openpyxl")
try:
    import openpyxl
except ImportError:
    sys.exit("Missing: pip install requests openpyxl")

# ── ANSI colours ───────────────────────────────────────────────────────────────
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

def ok(msg):   print(f"  {GREEN}[OK]{RESET}   {msg}")
def err(msg):  print(f"  {RED}[ERR]{RESET}  {msg}")
def warn(msg): print(f"  {YELLOW}[WARN]{RESET} {msg}")
def info(msg): print(f"  {CYAN}[INFO]{RESET} {msg}")
def step(msg): print(f"\n{BOLD}{CYAN}▶ {msg}{RESET}")

# ── Column alias map (case-insensitive) ────────────────────────────────────────
_EPC_ALIASES         = ["epc", "epc tag", "epc_tag", "rfid", "rfid tag", "tag id", "tag_id"]
_EAN_ALIASES         = ["ean", "barcode", "ean13", "upc", "gtin", "bar code", "ean code"]
_DESCRIPTION_ALIASES = ["description", "desc", "product name", "name", "item description", "item name"]
_SIZE_ALIASES        = ["size", "size_code", "variant", "colour", "color"]
_INFO_ALIASES        = ["info", "brand", "model", "make", "label", "additional info", "details"]
_QTY_ALIASES         = ["expected", "expected_qty", "qty", "quantity", "qty_expected", "stock", "expected qty"]


def _find_col(header_lower: list[str], aliases: list[str]) -> int | None:
    for alias in aliases:
        try:
            return header_lower.index(alias.lower())
        except ValueError:
            pass
    return None


def _val(row: list, idx: int | None) -> str:
    if idx is None or idx >= len(row):
        return ""
    v = row[idx]
    return str(v).strip() if v is not None else ""


# ── Excel reader ───────────────────────────────────────────────────────────────

def load_excel(path: str) -> tuple[list[str], list[list]]:
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    rows_iter = ws.iter_rows(values_only=True)
    raw_hdr = next(rows_iter, None)
    if raw_hdr is None:
        sys.exit(f"{RED}File is empty: {path}{RESET}")

    header = [str(h).strip() if h is not None else f"col{i}" for i, h in enumerate(raw_hdr)]
    rows   = [list(r) for r in rows_iter if any(v is not None for v in r)]
    wb.close()
    info(f"Columns found: {header}")
    info(f"Data rows    : {len(rows)}")
    return header, rows


# ── API client ─────────────────────────────────────────────────────────────────

class Client:
    def __init__(self, base_url: str, username: str, password: str):
        self.base = base_url.rstrip("/")
        self.s = requests.Session()
        self.s.headers["Content-Type"] = "application/json"
        self._login(username, password)

    def _login(self, username, password):
        r = self.s.post(f"{self.base}/api/auth/login",
                        json={"username": username, "password": password}, timeout=15)
        if r.status_code != 200:
            sys.exit(f"{RED}Login failed ({r.status_code}): {r.text[:200]}{RESET}")
        tok  = r.json().get("data", {}).get("accessToken")
        role = r.json().get("data", {}).get("role")
        self.s.headers["Authorization"] = f"Bearer {tok}"
        ok(f"Logged in as '{username}' ({role})")

    def get(self, path, **kw):  return self.s.get(f"{self.base}{path}", timeout=15, **kw)
    def post(self, path, body=None, **kw):
        return self.s.post(f"{self.base}{path}", json=body, timeout=15, **kw)
    def put(self, path, body=None, **kw):
        return self.s.put(f"{self.base}{path}", json=body, timeout=15, **kw)

    def find_store(self, store_code: str) -> str | None:
        r = self.get("/api/stores?size=200")
        if r.status_code != 200:
            return None
        for s in r.json().get("data", {}).get("content", []):
            if s.get("storeCode") == store_code:
                return s["id"]
        return None

    def find_product_by_sku(self, sku: str) -> str | None:
        r = self.get(f"/api/products/by-sku/{sku}")
        if r.status_code == 200:
            return r.json().get("data", {}).get("id")
        return None


# ── SQL generator (fast, no API needed for products/epcs) ─────────────────────

def _sq(v):
    if v is None: return "NULL"
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, (int, float)): return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def generate_sql(rows_data: list[dict], store_id: str) -> str:
    lines = [
        "-- StoreLense Product Catalog Import",
        f"-- Generated: {date.today().isoformat()}",
        f"-- Store: {store_id}",
        "\\set ON_ERROR_STOP on",
        "BEGIN;",
        "",
        "-- 1. Products",
    ]

    prod_vals = []
    for p in rows_data:
        sku  = p["sku"]
        name = p["name"][:250]
        desc = (p["description"] or name)[:500]
        brand = (p["brand"] or "")[:255]
        prod_vals.append(
            f"  (gen_random_uuid(), {_sq(sku)}, {_sq(name)}, {_sq(desc)}, "
            f"{_sq(brand)}, {_sq('ERP-' + sku)}, 'EACH', true, true)"
        )

    if prod_vals:
        lines.append("INSERT INTO products.products")
        lines.append("  (id, sku, name, description, brand, erp_product_code, unit_of_measure, is_rfid_enabled, is_active)")
        lines.append("VALUES")
        lines.append(",\n".join(prod_vals))
        lines.append("ON CONFLICT (sku) DO UPDATE SET")
        lines.append("  name = EXCLUDED.name, brand = EXCLUDED.brand, updated_at = now();")
        lines.append("")

    lines.append("-- 2. SKU → product_id map")
    lines.append("CREATE TEMP TABLE IF NOT EXISTS _sp (sku TEXT PRIMARY KEY, pid UUID);")
    lines.append("TRUNCATE _sp;")
    in_list = ", ".join(_sq(p["sku"]) for p in rows_data)
    lines.append(f"INSERT INTO _sp SELECT sku, id FROM products.products WHERE sku IN ({in_list});")
    lines.append("")

    lines.append("-- 3. EAN barcodes")
    ean_vals = [p for p in rows_data if p.get("ean")]
    if ean_vals:
        vals = [f"  ({_sq(p['sku'])}, {_sq(p['ean'])}, 'ean13', true)" for p in ean_vals]
        lines.append("INSERT INTO products.barcodes (id, product_id, barcode_type, barcode_value, is_primary)")
        lines.append("SELECT gen_random_uuid(), s.pid, v.bt, v.bv, v.pri")
        lines.append("FROM (VALUES")
        lines.append(",\n".join(
            f"  ({_sq(p['sku'])}, {_sq(p['ean'])}, 'ean13'::text, true)" for p in ean_vals
        ))
        lines.append(") AS v(sku, bv, bt, pri) JOIN _sp s ON s.sku = v.sku")
        lines.append("ON CONFLICT (barcode_type, barcode_value) DO NOTHING;")
        lines.append("")

    lines.append("-- 4. EPC tags")
    epc_vals = [p for p in rows_data if p.get("epc")]
    if epc_vals:
        lines.append("INSERT INTO products.epc_tags (id, epc, epc_encoding, product_id, is_encoded, encoded_at, is_active)")
        lines.append("SELECT gen_random_uuid(), v.epc, 'SGTIN_96', s.pid, true, now(), true")
        lines.append("FROM (VALUES")
        lines.append(",\n".join(f"  ({_sq(p['epc'])}, {_sq(p['sku'])})" for p in epc_vals))
        lines.append(") AS v(epc, sku) JOIN _sp s ON s.sku = v.sku")
        lines.append("ON CONFLICT (epc) DO NOTHING;")
        lines.append("")

    lines.append("-- 5. Inventory state (expected quantities)")
    inv_vals = [p for p in rows_data if p.get("qty_expected", 0) > 0]
    if inv_vals:
        lines.append(f"-- Clearing existing store-level inventory_state for store {store_id}")
        lines.append("")
        for p in inv_vals:
            lines.append(
                f"INSERT INTO inventory.inventory_state "
                f"(id, store_id, product_id, zone_id, quantity_on_hand, quantity_expected, last_counted_at, accuracy_pct) "
                f"SELECT gen_random_uuid(), {_sq(store_id)}, s.pid, NULL, 0, {p['qty_expected']}, now(), NULL "
                f"FROM _sp s WHERE s.sku = {_sq(p['sku'])} "
                f"ON CONFLICT (store_id, product_id, zone_id) DO UPDATE "
                f"  SET quantity_expected = EXCLUDED.quantity_expected, updated_at = now();"
            )
        lines.append("")

    lines.append("-- 6. EPC registry (mark all EPCs as in_store)")
    if epc_vals:
        lines.append("INSERT INTO inventory.epc_registry (id, epc, store_id, product_id, status, first_seen_at, last_seen_at)")
        lines.append(f"SELECT gen_random_uuid(), v.epc, {_sq(store_id)}, s.pid, 'in_store', now(), now()")
        lines.append("FROM (VALUES")
        lines.append(",\n".join(f"  ({_sq(p['epc'])}, {_sq(p['sku'])})" for p in epc_vals))
        lines.append(") AS v(epc, sku) JOIN _sp s ON s.sku = v.sku")
        lines.append(f"ON CONFLICT (epc, store_id) DO UPDATE SET status = 'in_store', last_seen_at = now(), updated_at = now();")
        lines.append("")

    lines.append("COMMIT;")
    lines.append("")
    lines.append("-- Verify:")
    lines.append("-- SELECT p.sku, p.name, b.barcode_value AS ean, e.epc, i.quantity_expected")
    lines.append("-- FROM products.products p")
    lines.append("-- LEFT JOIN products.barcodes b ON b.product_id = p.id AND b.is_primary = true")
    lines.append("-- LEFT JOIN products.epc_tags e ON e.product_id = p.id")
    lines.append("-- LEFT JOIN inventory.inventory_state i ON i.product_id = p.id")
    lines.append(f"-- WHERE i.store_id = '{store_id}';")
    return "\n".join(lines)


# ── REST import ────────────────────────────────────────────────────────────────

def import_via_api(client: Client, rows_data: list[dict], store_id: str, dry_run: bool):
    stats = {"created": 0, "updated": 0, "ean": 0, "epc": 0, "inv": 0, "errors": 0}

    for p in rows_data:
        sku   = p["sku"]
        label = f"{sku} — {p['name']}"

        if dry_run:
            action = "UPDATE" if client.find_product_by_sku(sku) else "CREATE"
            info(f"[DRY-RUN] {action}  {label}  ean={p.get('ean')}  epc={p.get('epc')}  qty={p.get('qty_expected')}")
            continue

        existing_id = client.find_product_by_sku(sku)

        payload = {
            "sku":           sku,
            "name":          p["name"],
            "description":   p["description"] or p["name"],
            "brand":         p["brand"] or "",
            "rfidEnabled":   True,
            "unitOfMeasure": "EACH",
            "erpProductCode": f"ERP-{sku}",
        }
        if p.get("ean"):
            payload["ean"] = p["ean"]

        if existing_id:
            r = client.put(f"/api/products/{existing_id}", payload)
            if r.status_code == 200:
                product_id = existing_id
                ok(f"Updated  {label}")
                stats["updated"] += 1
            else:
                err(f"Update failed for {sku}: {r.status_code} {r.text[:100]}")
                stats["errors"] += 1
                continue
        else:
            r = client.post("/api/products", payload)
            if r.status_code == 201:
                product_id = r.json()["data"]["id"]
                ok(f"Created  {label}")
                stats["created"] += 1
            else:
                err(f"Create failed for {sku}: {r.status_code} {r.text[:100]}")
                stats["errors"] += 1
                continue

        # Associate EPC tag
        if p.get("epc"):
            r2 = client.s.post(f"{client.base}/api/products/{product_id}/epc",
                                params={"epc": p["epc"]}, timeout=10)
            if r2.status_code in (200, 201, 409):
                ok(f"  EPC {p['epc']} linked")
                stats["epc"] += 1
            else:
                warn(f"  EPC link failed: {r2.status_code} {r2.text[:80]}")

        # Set expected inventory
        if p.get("qty_expected", 0) > 0:
            r3 = client.post("/api/inventory/expected", {
                "storeId":          store_id,
                "productId":        product_id,
                "quantityExpected": p["qty_expected"],
                "source":           "catalog_import",
            })
            if r3.status_code in (200, 201, 204):
                stats["inv"] += 1
            else:
                warn(f"  Expected qty set failed: {r3.status_code} {r3.text[:80]}")

        time.sleep(0.05)

    if not dry_run:
        print()
        print(f"  created={stats['created']}  updated={stats['updated']}  "
              f"epc_linked={stats['epc']}  inv_set={stats['inv']}  errors={stats['errors']}")
    return stats


# ── Main ───────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(
        description="StoreLense — Product Catalog Import (EPC/EAN/Description/Size/info format)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("--file",       required=True, help="Path to the Excel file (.xlsx)")
    ap.add_argument("--store-code", required=True, help="Store code, e.g. LK001")
    ap.add_argument("--qty",        type=int, default=1,
                    help="Default expected quantity per product (default: 1). "
                         "Overridden by 'expected' column in the file if present.")
    ap.add_argument("--url",        default=os.getenv("STORELENSE_URL", "http://localhost:8080"))
    ap.add_argument("--username",   default=os.getenv("STORELENSE_USER", "admin"))
    ap.add_argument("--password",   default=os.getenv("STORELENSE_PASS", "Admin@StoreLense1"))
    ap.add_argument("--dry-run",    action="store_true",
                    help="Print what would be imported without writing anything")
    ap.add_argument("--sql-out",    metavar="FILE",
                    help="Write a SQL import script to FILE instead of calling the API. "
                         "Run on server: cat FILE | docker exec -i deploy-postgres-1 psql -U postgres -d storelense")
    args = ap.parse_args()

    if not os.path.exists(args.file):
        sys.exit(f"{RED}File not found: {args.file}{RESET}")

    print(f"\n{BOLD}{CYAN}StoreLense Product Catalog Import{RESET}")
    print(f"  File      : {args.file}")
    print(f"  Store     : {args.store_code}")
    print(f"  Gateway   : {args.url}")
    if args.dry_run:
        print(f"  {YELLOW}MODE: DRY-RUN — no data will be written{RESET}")
    if args.sql_out:
        print(f"  {YELLOW}MODE: SQL export → {args.sql_out}{RESET}")

    # Load Excel
    step("Reading Excel file")
    header, raw_rows = load_excel(args.file)
    header_lower = [h.lower() for h in header]

    epc_col  = _find_col(header_lower, _EPC_ALIASES)
    ean_col  = _find_col(header_lower, _EAN_ALIASES)
    desc_col = _find_col(header_lower, _DESCRIPTION_ALIASES)
    size_col = _find_col(header_lower, _SIZE_ALIASES)
    info_col = _find_col(header_lower, _INFO_ALIASES)
    qty_col  = _find_col(header_lower, _QTY_ALIASES)

    info(f"Column mapping:")
    info(f"  EPC         → col {epc_col}  ({header[epc_col] if epc_col is not None else 'NOT FOUND'})")
    info(f"  EAN         → col {ean_col}  ({header[ean_col] if ean_col is not None else 'NOT FOUND'})")
    info(f"  Description → col {desc_col} ({header[desc_col] if desc_col is not None else 'NOT FOUND'})")
    info(f"  Size        → col {size_col} ({header[size_col] if size_col is not None else 'not found — optional'})")
    info(f"  Info/Brand  → col {info_col} ({header[info_col] if info_col is not None else 'not found — optional'})")
    info(f"  Expected qty→ col {qty_col}  ({header[qty_col] if qty_col is not None else f'not found — using --qty={args.qty}'})")

    if desc_col is None and epc_col is None:
        sys.exit(f"{RED}Could not find a Description or EPC column. Check column headers.{RESET}")

    # Parse rows
    step("Parsing product data")
    rows_data = []
    seen_skus = set()

    for raw in raw_rows:
        epc   = _val(raw, epc_col)
        ean   = _val(raw, ean_col)
        desc  = _val(raw, desc_col)
        size  = _val(raw, size_col)
        brand = _val(raw, info_col)

        if not epc and not desc:
            continue

        # Build SKU: use EPC if available, else sanitise description
        if epc:
            sku = epc
        else:
            sku = desc[:50].replace(" ", "-").upper()

        # Append size to make SKU unique when same description has multiple sizes
        if size:
            sku = f"{sku}-{size}"

        if sku in seen_skus:
            warn(f"Duplicate SKU skipped: {sku}")
            continue
        seen_skus.add(sku)

        # Build display name
        name = desc or sku
        if size:
            name = f"{name} ({size})"

        # Expected qty: from column or default
        qty_expected = args.qty
        if qty_col is not None:
            raw_qty = _val(raw, qty_col)
            try:
                qty_expected = max(0, int(float(raw_qty))) if raw_qty else args.qty
            except (ValueError, TypeError):
                qty_expected = args.qty

        rows_data.append({
            "sku":          sku,
            "name":         name,
            "description":  desc,
            "brand":        brand,
            "epc":          epc,
            "ean":          ean,
            "qty_expected": qty_expected,
        })

    ok(f"Parsed {len(rows_data)} products")
    for p in rows_data[:5]:
        info(f"  sku={p['sku']}  ean={p['ean']}  epc={p['epc']}  name={p['name']}  qty={p['qty_expected']}")
    if len(rows_data) > 5:
        info(f"  ... and {len(rows_data) - 5} more")

    # Authenticate & resolve store
    step("Connecting to StoreLense")
    client = Client(args.url, args.username, args.password)

    store_id = client.find_store(args.store_code)
    if not store_id:
        sys.exit(f"{RED}Store '{args.store_code}' not found in StoreLense. "
                 f"Create it first in Settings → Stores.{RESET}")
    ok(f"Store {args.store_code} → {store_id}")

    # SQL mode
    if args.sql_out:
        step(f"Generating SQL → {args.sql_out}")
        sql = generate_sql(rows_data, store_id)
        with open(args.sql_out, "w", encoding="utf-8") as f:
            f.write(sql)
        ok(f"SQL written to {args.sql_out}")
        print()
        print(f"  {BOLD}Run on server:{RESET}")
        print(f"  cat {args.sql_out} | docker exec -i deploy-postgres-1 psql -U postgres -d storelense")
        print()
        return

    # REST import
    step(f"Importing {len(rows_data)} products via API")
    import_via_api(client, rows_data, store_id, dry_run=args.dry_run)

    if not args.dry_run:
        print(f"\n{BOLD}{GREEN}{'═'*55}{RESET}")
        print(f"{BOLD}{GREEN}  Import complete!{RESET}")
        print(f"{BOLD}{GREEN}{'═'*55}{RESET}")
        print(f"  Store      : {args.store_code}  ({store_id})")
        print(f"  Products   : {len(rows_data)}")
        print()
        print(f"  {BOLD}Next steps:{RESET}")
        print(f"  1. Open the Android app → select store {args.store_code}")
        print(f"  2. Start a new SOH session → scan RFID tags")
        print(f"  3. Complete session → view accuracy vs expected")
        print()


if __name__ == "__main__":
    main()
