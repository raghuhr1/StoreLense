#!/usr/bin/env python3
"""
StoreLense XLS Seeder  — SQL bulk-insert mode (fast) + REST fallback

SQL mode  --sql  (recommended — seconds instead of hours):
  Generates one large .sql file and pipes it to PostgreSQL.
  Inserts products, EPC tags, inventory state, and SOH sessions directly.
  Only 3 REST calls needed: store create, zones, readers.

REST mode (default — one API call per row, slow on large datasets):
  Use only when you cannot access PostgreSQL directly.

Usage — SQL mode (recommended):
  python3 tools/seed_from_xls.py --sql \\
      --dir /opt/Files/P036  --store-code P036 \\
      --pg-container deploy-postgres-1

  # To write SQL to a file instead of executing it:
  python3 tools/seed_from_xls.py --sql --sql-out /tmp/seed_p036.sql \\
      --dir /opt/Files/P036  --store-code P036
  cat /tmp/seed_p036.sql | docker exec -i deploy-postgres-1 psql -U postgres -d storelense

Usage — REST mode:
  python3 tools/seed_from_xls.py \\
      --dir /opt/Files/P036  --store-code P036 \\
      --url http://localhost:8080 --workers 3

Columns expected in each XLS:
  dept | style | barcode | item barcode | description | color | size |
  cost | price | expected | scan | Back Stock | Sales Floor |
  variance | difference | ext cost | ext price | loss | scan%
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
import urllib.request
import urllib.error

try:
    import openpyxl
except ImportError:
    print("ERROR: openpyxl not installed.  Run: pip install openpyxl", file=sys.stderr)
    sys.exit(1)

# ── Terminal colours ──────────────────────────────────────────────────────────
GREEN  = '\033[92m'; RED = '\033[91m'; YELLOW = '\033[93m'
CYAN   = '\033[96m'; BOLD = '\033[1m'; RESET  = '\033[0m'

def ok(msg):   print(f"  {GREEN}✔{RESET}  {msg}")
def fail(msg): print(f"  {RED}✘{RESET}  {msg}", file=sys.stderr)
def info(msg): print(f"  {CYAN}→{RESET}  {msg}")
def step(msg): print(f"\n{BOLD}{CYAN}▶ {msg}{RESET}")
def warn(msg): print(f"  {YELLOW}⚠{RESET}  {msg}")
def progress(done, total, label=""):
    pct = int(done / total * 40) if total else 0
    bar = '█' * pct + '░' * (40 - pct)
    print(f"\r  [{bar}] {done:,}/{total:,} {label}   ", end='', flush=True)

# ── HTTP helpers ──────────────────────────────────────────────────────────────
def _call(method, url, body=None, token=None, timeout=30,
          retries=4, retry_delay=2.0):
    """HTTP call with exponential-backoff retry on 5xx / connection errors."""
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    last_err = None
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            if e.code < 500:
                try:
                    return json.loads(e.read().decode())
                except Exception:
                    return {'success': False, 'message': str(e)}
            last_err = f'HTTP {e.code}'
        except Exception as e:
            last_err = str(e)
        if attempt < retries:
            wait = retry_delay * (2 ** attempt)
            time.sleep(wait)
    return {'success': False, 'message': f'Failed after {retries+1} attempts: {last_err}'}


class Api:
    def __init__(self, base_url, username, password):
        self.base  = base_url.rstrip('/')
        self._user = username
        self._pass = password
        self.token = None
        self._refresh()

    def _refresh(self):
        step("Authenticating …")
        cred_failures = 0
        for attempt in range(40):          # up to ~10 min waiting for Spring Boot
            resp = _call('POST', f'{self.base}/api/auth/login',
                         {'username': self._user, 'password': self._pass},
                         retries=1, timeout=20)
            tok = (resp.get('data') or {}).get('accessToken')
            if tok:
                self.token = tok
                ok("Authenticated")
                return
            msg = resp.get('message', '')
            # 502 / 503 / connection errors → service still starting, wait longer
            is_infra = any(x in msg for x in ('HTTP 50', 'Connection', 'timed out', 'Failed after'))
            if is_infra:
                wait = 15
                warn(f"Service not ready (attempt {attempt+1}), retrying in {wait}s …")
            else:
                cred_failures += 1
                wait = 5 * cred_failures
                warn(f"Login attempt {cred_failures} failed ({msg}), retrying in {wait}s …")
                if cred_failures >= 5:
                    fail("Login failed — check --username / --password")
                    sys.exit(1)
            time.sleep(wait)
        fail("Service unavailable after 40 attempts — is the stack running?")
        sys.exit(1)

    def get(self, path, **kw):
        return _call('GET', f'{self.base}{path}', token=self.token, **kw)

    def post(self, path, body=None, **kw):
        return _call('POST', f'{self.base}{path}', body, token=self.token, **kw)

    @property
    def tok(self):
        return self.token


# ── Column alias map — handles variations across store XLS formats ────────────
_COL_ALIASES: dict[str, list[str]] = {
    'style':       ['style', 'style no', 'style no.', 'style_no', 'styleno',
                    'article', 'article no', 'article no.', 'item no', 'item code',
                    'item_group', 'item group',                 # P004 format
                    'product code', 'sku'],
    'dept':        ['item_class', 'item class',                 # P004: 'MEN APPAREL', 'WOMEN APPAREL'
                    'item_subclass', 'item subclass',           # more specific sub-category
                    'dept', 'department', 'dept.', 'category', 'cat', 'div', 'division'],
    'description': ['description', 'desc', 'item desc', 'product name', 'item description', 'name'],
    'barcode':     ['barcode', 'bar code', 'ean', 'upc', 'item barcode', 'ean13',
                    'gtin', 'item_barcode',                     # P004 format
                    'barcode no', 'ean/upc'],
    'expected':    ['expected', 'exp qty', 'expected qty', 'erp qty', 'system qty',
                    'book qty', 'book stock', 'erp expected', 'quantity',
                    'expected_qty',                             # P004 format
                    'sys qty', 'system stock'],
    'scan':        ['scan', 'scanned', 'rfid scan', 'rfid count', 'physical', 'physical count', 'counted',
                    'handscan_qty', 'handscan qty', 'handscan', # P004 format
                    'rfid qty', 'scan qty'],
    'back stock':  ['back stock', 'backstock', 'back room', 'backroom', 'back', 'br stock', 'b/s'],
    'sales floor': ['sales floor', 'salesfloor', 'floor', 'shop floor', 'sf', 's/f', 'floor stock'],
}

def _resolve_col(row: dict, canonical: str):
    """Return value for a canonical column, trying all known aliases.
    For text fields, skips '?' placeholder values and falls through to next alias."""
    for alias in _COL_ALIASES.get(canonical, [canonical]):
        if alias in row:
            v = row[alias]
            # Skip '?' placeholders and empty strings in text fields
            if v is not None and str(v).strip() not in ('', '?', 'N/A', 'NA', '-'):
                return v
    return None


# ── XLS parsing ───────────────────────────────────────────────────────────────
def load_xls(path):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    rows_iter = ws.iter_rows(values_only=True)
    raw_hdr = next(rows_iter)
    header = [str(h).strip().lower() if h else f'col{i}' for i, h in enumerate(raw_hdr)]
    rows = [dict(zip(header, r)) for r in rows_iter if any(v is not None for v in r)]
    wb.close()

    # Always print actual column names (helps diagnose mismatches)
    non_empty_headers = [h for h in header if not h.startswith('col')]
    info(f"  XLS columns     : {non_empty_headers}")

    # Diagnostic: show which canonical columns were found / missing
    found, missing = [], []
    for canon in ('style', 'dept', 'description', 'barcode', 'expected', 'scan', 'back stock', 'sales floor'):
        matched = next((a for a in _COL_ALIASES.get(canon, [canon]) if a in header), None)
        if matched:
            found.append(f'{canon}→"{matched}"' if matched != canon else canon)
        else:
            missing.append(canon)
    info(f"  Columns matched : {', '.join(found) or 'NONE — column names do not match!'}")
    if missing:
        warn(f"  Columns missing : {', '.join(missing)}")
    return rows


def clean_barcode(v):
    if v is None or str(v).strip() == '?':
        return None
    s = str(v).strip().split('.')[0]
    return s.zfill(14) if s.isdigit() else None


import re as _re

def date_from_filename(filename):
    """Extract YYYY-MM-DD from filename.  Handles YYYYMMDD and DDMMYYYY,
    with or without separators (-, /, ., space, _)."""
    base = os.path.basename(filename)
    base = _re.sub(r'\.(xlsx?|XLS[Xx]?)$', '', base)

    # Separated formats: YYYY-MM-DD or DD-MM-YYYY (any separator)
    m = _re.search(r'(\d{4})[-/._](\d{2})[-/._](\d{2})', base)
    if m:
        y, mo, d = m.groups()
        if 1 <= int(mo) <= 12 and 1 <= int(d) <= 31:
            return f'{y}-{mo}-{d}'

    m = _re.search(r'(\d{2})[-/._](\d{2})[-/._](\d{4})', base)
    if m:
        d, mo, y = m.groups()
        if 1 <= int(mo) <= 12 and 1 <= int(d) <= 31:
            return f'{y}-{mo}-{d}'

    # Compact 8-digit: YYYYMMDD or DDMMYYYY
    for s in _re.findall(r'\d{8}', base):
        if s[:4].startswith('20') and 1 <= int(s[4:6]) <= 12 and 1 <= int(s[6:]) <= 31:
            return f'{s[:4]}-{s[4:6]}-{s[6:]}'     # YYYYMMDD
        if s[4:].startswith('20') and 1 <= int(s[2:4]) <= 12 and 1 <= int(s[:2]) <= 31:
            return f'{s[4:]}-{s[2:4]}-{s[:2]}'      # DDMMYYYY

    warn(f'Could not parse date from filename "{os.path.basename(filename)}" — using today')
    return datetime.now().strftime('%Y-%m-%d')


# ── Data aggregation ──────────────────────────────────────────────────────────
def aggregate_products(rows):
    """Returns dict: style → {dept, style, name, epcs[], expected_total, scan_total, back_stock_total, sales_floor_total}."""
    products = {}
    def _str(v): return str(v).strip() if v is not None else ''

    for r in rows:
        style = _str(_resolve_col(r, 'style'))
        if not style:
            continue
        dept        = _str(_resolve_col(r, 'dept'))
        desc        = _str(_resolve_col(r, 'description')) or style
        barcode     = clean_barcode(_resolve_col(r, 'barcode'))
        expected    = max(0, int(_resolve_col(r, 'expected') or 0))
        scan        = int(_resolve_col(r, 'scan') or 0)
        back_stock  = max(0, int(_resolve_col(r, 'back stock') or 0))
        sales_floor = max(0, int(_resolve_col(r, 'sales floor') or 0))

        if style not in products:
            products[style] = {
                'dept': dept, 'style': style, 'name': desc,
                'epcs': [], 'epc_set': set(),
                'expected_total': 0, 'scan_total': 0,
                'back_stock_total': 0, 'sales_floor_total': 0,
            }
        p = products[style]
        p['expected_total']    += expected
        p['scan_total']        += scan        # sum actual units scanned, not just presence
        p['back_stock_total']  += back_stock
        p['sales_floor_total'] += sales_floor
        if barcode and barcode not in p['epc_set']:
            p['epcs'].append(barcode)
            p['epc_set'].add(barcode)
    for p in products.values():
        del p['epc_set']
    return products


def get_scanned_epcs(rows):
    seen = set()
    result = []
    for r in rows:
        if int(r.get('scan') or 0) >= 1:
            bc = clean_barcode(r.get('barcode'))
            if bc and bc not in seen:
                result.append(bc)
                seen.add(bc)
    return result


# ── API helpers (REST mode) ───────────────────────────────────────────────────
def _extract_list(resp_data):
    if isinstance(resp_data, list):
        return resp_data
    if isinstance(resp_data, dict):
        return resp_data.get('content') or []
    return []


def get_or_create_store(api, store_code, store_name):
    resp = api.post('/api/stores', {
        'storeCode': store_code, 'name': store_name,
        'addressLine1': 'Pantaloons Store', 'city': 'Mumbai',
        'stateProvince': 'Maharashtra', 'postalCode': '400001',
        'countryCode': 'IN', 'timezone': 'Asia/Kolkata',
        'erpStoreCode': f'ERP-{store_code}',
    })
    sid = (resp.get('data') or {}).get('id')
    if sid:
        ok(f"Store {store_code} created → {sid}")
        return sid
    resp2 = api.get('/api/stores?size=200')
    for s in _extract_list(resp2.get('data') or []):
        if s.get('storeCode') == store_code:
            info(f"Store {store_code} already exists → {s['id']}")
            return s['id']
    fail(f"Cannot create or find store {store_code}: {resp.get('message')}")
    sys.exit(1)


def ensure_zones(api, store_id):
    zones = [
        ('FLOOR-GF',  'Ground Floor',  'floor',        1),
        ('FLOOR-FF',  'First Floor',   'floor',        2),
        ('FITTING',   'Fitting Rooms', 'fitting_room', 3),
        ('BACKROOM',  'Backroom',      'backroom',      4),
        ('STOCKROOM', 'Stockroom',     'stockroom',     5),
    ]
    for code, name, ztype, order in zones:
        api.post(f'/api/stores/{store_id}/zones', {
            'zoneCode': code, 'name': name,
            'zoneType': ztype, 'displayOrder': order,
        })
    ok("Zones ensured")


def ensure_readers(api, store_id, store_code):
    zones_resp = api.get(f'/api/stores/{store_id}/zones')
    content    = _extract_list(zones_resp.get('data') or [])
    floor_id   = next((z['id'] for z in content if z.get('zoneType') == 'floor'), None)
    back_id    = next((z['id'] for z in content if z.get('zoneType') == 'backroom'), None)
    readers = [
        (f'{store_code}-FIXED-01',    'fixed',    floor_id, '192.168.10.10', 'R700-3.4.1',    4),
        (f'{store_code}-FIXED-02',    'fixed',    back_id,  '192.168.10.11', 'R700-3.4.1',    4),
        (f'{store_code}-HANDHELD-01', 'handheld', None,     '192.168.10.30', 'RFD8500-2.1.0', 1),
    ]
    for code, rtype, zone_id, ip, fw, antennas in readers:
        api.post(f'/api/stores/{store_id}/readers', {
            'readerCode': code, 'readerType': rtype,
            'zoneId': zone_id, 'ipAddress': ip,
            'firmwareVersion': fw, 'antennaCount': antennas, 'txPowerDbm': 30.0,
        })
    ok("RFID readers ensured")


def fetch_all_product_ids(api):
    result = {}
    page = 0
    while True:
        resp    = api.get(f'/api/products?size=200&page={page}')
        content = _extract_list(resp.get('data') or [])
        if not content:
            break
        for p in content:
            result[p['sku']] = p['id']
        if len(content) < 200:
            break
        page += 1
    return result


def _create_product(base_url, token, style, name, dept):
    resp = _call('POST', f'{base_url}/api/products', {
        'sku': style, 'name': name[:250],
        'description': f'{dept} - {name}'[:500],
        'brand': dept, 'unitOfMeasure': 'EACH',
        'rfidEnabled': True, 'erpProductCode': f'ERP-{style}',
    }, token)
    pid = (resp.get('data') or {}).get('id')
    if pid:
        return pid
    resp2 = _call('GET', f'{base_url}/api/products/by-sku/{style}', token=token)
    return (resp2.get('data') or {}).get('id')


def _register_epc(base_url, token, product_id, epc):
    _call('POST', f'{base_url}/api/products/{product_id}/epc?epc={epc}', token=token)


def _set_expected(base_url, token, store_id, product_id, qty):
    _call('POST', f'{base_url}/api/inventory/expected', {
        'storeId': store_id, 'productId': product_id,
        'quantityExpected': qty, 'source': 'erp_sync',
    }, token)


def submit_rfid_batch(api, session_id, store_id, epcs, batch_size=300):
    total = len(epcs)
    for i in range(0, total, batch_size):
        batch = epcs[i:i + batch_size]
        reads = [{'epc': e, 'rssi': -65.0, 'antennaPort': j % 4}
                 for j, e in enumerate(batch)]
        api.post('/api/rfid/ingest/batch', {
            'rfidSessionId': session_id, 'storeId': store_id,
            'deviceId': 'xls-seed-scanner', 'reads': reads,
        }, timeout=60)
        progress(min(i + batch_size, total), total, "EPCs submitted")
    print()


# ── SQL bulk mode ─────────────────────────────────────────────────────────────

def _sq(v):
    """Safely quote a value as a SQL string literal, or return NULL."""
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v).replace("'", "''").replace('\n', ' ').replace('\r', '')
    return f"'{s}'"


def _generate_sql(store_id, union_products, all_files):
    """Build and return the complete seed SQL as a string."""
    out   = []
    w     = lambda *a: out.append(a[0] if a else '')
    BATCH = 500

    w(f"-- StoreLense bulk seed — store {store_id}")
    w(f"-- Generated: {datetime.now().isoformat()}")
    w("\\set ON_ERROR_STOP on")
    w("BEGIN;")
    w()

    styles   = list(union_products.items())
    epc_rows = [
        (style, epc)
        for style, p in union_products.items()
        for epc in p.get('epcs', [])
        if epc
    ]

    # ── 1. products.products ──────────────────────────────────────────
    w("-- ── 1. Products ──────────────────────────────────────────────────────")
    for i in range(0, len(styles), BATCH):
        chunk = styles[i:i + BATCH]
        vals  = []
        for style, p in chunk:
            nm   = (p.get('name') or style)[:250]
            dept = (p.get('dept') or 'General')[:255]
            desc = f"{dept} - {nm}"[:500]
            vals.append(
                f"  (gen_random_uuid(), {_sq(style)}, {_sq(nm)}, {_sq(desc)}, "
                f"{_sq(dept)}, {_sq('ERP-' + style)}, 'EACH', true, true)"
            )
        w("INSERT INTO products.products")
        w("  (id, sku, name, description, brand, erp_product_code, unit_of_measure, is_rfid_enabled, is_active)")
        w("VALUES")
        w(',\n'.join(vals))
        w("ON CONFLICT (sku) DO UPDATE SET")
        w("  name = EXCLUDED.name, brand = EXCLUDED.brand,")
        w("  erp_product_code = EXCLUDED.erp_product_code, updated_at = now();")
        w()

    # ── 2. Temp map: sku → actual product id (handles ON CONFLICT keeping old id) ─
    w("-- ── 2. SKU → product_id mapping ────────────────────────────────────────")
    w("CREATE TEMP TABLE IF NOT EXISTS _sp (style TEXT PRIMARY KEY, pid UUID);")
    w("TRUNCATE _sp;")
    for i in range(0, len(styles), 1000):
        in_list = ', '.join(_sq(s) for s, _ in styles[i:i + 1000])
        w(f"INSERT INTO _sp SELECT sku, id FROM products.products WHERE sku IN ({in_list});")
    w()

    # ── 3. products.epc_tags ──────────────────────────────────────────
    w("-- ── 3. EPC Tags ──────────────────────────────────────────────────────")
    for i in range(0, len(epc_rows), BATCH):
        chunk = epc_rows[i:i + BATCH]
        vals  = [f"  ({_sq(epc)}, {_sq(style)})" for style, epc in chunk]
        w("INSERT INTO products.epc_tags (id, epc, epc_encoding, product_id, is_encoded, encoded_at, is_active)")
        w("SELECT gen_random_uuid(), v.epc, 'SGTIN_96', s.pid, true, now(), true")
        w("FROM (VALUES")
        w(',\n'.join(vals))
        w(") AS v(epc, style) JOIN _sp s ON s.style = v.style")
        w("ON CONFLICT (epc) DO NOTHING;")
        w()

    # ── 4. inventory.inventory_state ─────────────────────────────────
    _, last_date, last_rows = all_files[-1]
    last_prods = aggregate_products(last_rows)

    w("-- ── 4. Inventory State ───────────────────────────────────────────────")
    w(f"DELETE FROM inventory.epc_registry   WHERE store_id = {_sq(store_id)};")
    w(f"DELETE FROM inventory.inventory_state WHERE store_id = {_sq(store_id)};")
    w()

    inv_vals = []
    for style, p in last_prods.items():
        on_hand  = p.get('scan_total', 0)
        expected = p.get('expected_total', 0)
        accuracy = round(on_hand / expected * 100, 2) if expected > 0 else None
        acc_sql  = f'{accuracy}::numeric' if accuracy is not None else 'NULL::numeric'
        inv_vals.append((style, on_hand, expected, acc_sql))

    for i in range(0, len(inv_vals), BATCH):
        chunk = inv_vals[i:i + BATCH]
        vals  = [
            f"  ({_sq(style)}, {oh}::int, {ex}::int, {acc})"
            for style, oh, ex, acc in chunk
        ]
        w("INSERT INTO inventory.inventory_state")
        w("  (id, store_id, product_id, zone_id, quantity_on_hand, quantity_expected, last_counted_at, accuracy_pct)")
        w(f"SELECT gen_random_uuid(), {_sq(store_id)}, s.pid, NULL, v.oh, v.ex, now(), v.acc")
        w("FROM (VALUES")
        w(',\n'.join(vals))
        w(") AS v(style, oh, ex, acc) JOIN _sp s ON s.style = v.style;")
        w()

    # ── 4b. Zone-specific inventory state (Backroom + Sales Floor) ────────────
    zone_stock_vals = [
        (style, p.get('back_stock_total', 0), p.get('sales_floor_total', 0))
        for style, p in last_prods.items()
        if p.get('back_stock_total', 0) > 0 or p.get('sales_floor_total', 0) > 0
    ]
    if zone_stock_vals:
        w("-- ── 4b. Zone-specific Inventory (Backroom + Sales Floor) ──────────────")
        w("CREATE TEMP TABLE IF NOT EXISTS _zones (code TEXT PRIMARY KEY, zid UUID);")
        w("TRUNCATE _zones;")
        w(f"INSERT INTO _zones SELECT zone_code, id FROM stores.zones WHERE store_id = {_sq(store_id)};")
        w()
        for i in range(0, len(zone_stock_vals), BATCH):
            chunk = zone_stock_vals[i:i + BATCH]
            vals  = [f"  ({_sq(style)}, {bs}::int, {sf}::int)" for style, bs, sf in chunk]
            w("INSERT INTO inventory.inventory_state")
            w("  (id, store_id, product_id, zone_id, quantity_on_hand, quantity_expected, last_counted_at, accuracy_pct)")
            w(f"SELECT gen_random_uuid(), {_sq(store_id)}, s.pid, z.zid, v.bs, v.bs, now(), 100.0::numeric")
            w("FROM (VALUES")
            w(',\n'.join(vals))
            w(") AS v(style, bs, sf) JOIN _sp s ON s.style = v.style")
            w("JOIN _zones z ON z.code = 'BACKROOM'")
            w("WHERE v.bs > 0;")
            w()
            w("INSERT INTO inventory.inventory_state")
            w("  (id, store_id, product_id, zone_id, quantity_on_hand, quantity_expected, last_counted_at, accuracy_pct)")
            w(f"SELECT gen_random_uuid(), {_sq(store_id)}, s.pid, z.zid, v.sf, v.sf, now(), 100.0::numeric")
            w("FROM (VALUES")
            w(',\n'.join(vals))
            w(") AS v(style, bs, sf) JOIN _sp s ON s.style = v.style")
            w("JOIN _zones z ON z.code = 'FLOOR-GF'")
            w("WHERE v.sf > 0;")
            w()

    # ── 5. inventory.epc_registry ─────────────────────────────────────
    w("-- ── 5. EPC Registry ─────────────────────────────────────────────────")
    epc_best: dict = {}
    for _, date_str, rows in all_files:
        for r in rows:
            bc    = clean_barcode(r.get('barcode'))
            style = (r.get('style') or '').strip()
            if not bc or not style:
                continue
            scan   = int(r.get('scan') or 0)
            status = 'in_store' if scan >= 1 else 'missing'
            if bc not in epc_best or status == 'in_store':
                epc_best[bc] = (style, status, date_str)

    reg_rows = list(epc_best.items())
    for i in range(0, len(reg_rows), BATCH):
        chunk = reg_rows[i:i + BATCH]
        vals  = [
            f"  ({_sq(epc)}, {_sq(style)}, {_sq(status)}, {_sq(ds + 'T10:00:00+05:30')})"
            for epc, (style, status, ds) in chunk
        ]
        w("INSERT INTO inventory.epc_registry (id, epc, store_id, product_id, status, first_seen_at, last_seen_at)")
        w(f"SELECT gen_random_uuid(), v.epc, {_sq(store_id)}, s.pid, v.status,")
        w("  v.sa::timestamptz, v.sa::timestamptz")
        w("FROM (VALUES")
        w(',\n'.join(vals))
        w(") AS v(epc, style, status, sa) JOIN _sp s ON s.style = v.style")
        w(f"ON CONFLICT (epc, store_id) DO UPDATE SET")
        w("  status = EXCLUDED.status, last_seen_at = EXCLUDED.last_seen_at, updated_at = now();")
        w()

    # ── 6. SOH sessions (one per XLS day) ────────────────────────────
    w("-- ── 6. SOH Sessions ─────────────────────────────────────────────────")
    w(f"DELETE FROM soh.soh_variance WHERE store_id = {_sq(store_id)};")
    w(f"DELETE FROM soh.soh_results  WHERE store_id = {_sq(store_id)};")
    w(f"DELETE FROM soh.soh_sessions WHERE store_id = {_sq(store_id)};")
    w()

    for bname, date_str, rows in all_files:
        sid       = str(uuid.uuid4())
        rid       = str(uuid.uuid4())
        day_prods = aggregate_products(rows)
        scan_epcs = get_scanned_epcs(rows)
        total_exp = sum(p['expected_total'] for p in day_prods.values())
        total_oh  = sum(p.get('scan_total', 0) for p in day_prods.values())
        accuracy  = round(total_oh / total_exp * 100, 2) if total_exp > 0 else 0.0
        variance_count = sum(
            1 for p in day_prods.values()
            if p.get('scan_total', 0) != p.get('expected_total', 0)
        )
        overcount  = sum(1 for p in day_prods.values()
                         if p.get('scan_total', 0) > p.get('expected_total', 0))
        undercount = sum(1 for p in day_prods.values()
                         if p.get('scan_total', 0) < p.get('expected_total', 0))
        notes      = f"Pantaloons variance count {date_str} — {bname}"

        w(f"INSERT INTO soh.soh_sessions")
        w(f"  (id, store_id, session_type, status, started_by, started_at, completed_at,")
        w(f"   total_epc_reads, unique_epc_count, notes)")
        w(f"VALUES ({_sq(sid)}, {_sq(store_id)}, 'full_store', 'completed', gen_random_uuid(),")
        w(f"  {_sq(date_str + 'T09:00:00+05:30')}::timestamptz,")
        w(f"  {_sq(date_str + 'T11:00:00+05:30')}::timestamptz,")
        w(f"  {len(scan_epcs)}, {len(scan_epcs)}, {_sq(notes)});")
        w()

        w(f"INSERT INTO soh.soh_results")
        w(f"  (id, session_id, store_id, total_products_counted, total_units_counted,")
        w(f"   total_units_expected, accuracy_pct, variance_count, overcount_items, undercount_items)")
        w(f"VALUES ({_sq(rid)}, {_sq(sid)}, {_sq(store_id)},")
        w(f"  {len(day_prods)}, {total_oh}, {total_exp}, {accuracy},")
        w(f"  {variance_count}, {overcount}, {undercount});")
        w()

        # KPI daily row for this date — covers new stores missed by V2_0 migration
        w(f"INSERT INTO reporting.kpi_daily")
        w(f"  (store_id, kpi_date, inventory_accuracy_pct, soh_sessions_count,")
        w(f"   refill_tasks_created, refill_tasks_completed, refill_completion_rate_pct,")
        w(f"   total_epc_reads, unique_skus_counted, variance_items_count)")
        w(f"VALUES ({_sq(store_id)}, {_sq(date_str)}::date,")
        w(f"  {accuracy}, 1, 0, 0, NULL,")
        w(f"  {len(scan_epcs)}, {len(day_prods)}, {variance_count})")
        w(f"ON CONFLICT (store_id, kpi_date) DO UPDATE SET")
        w(f"  inventory_accuracy_pct  = EXCLUDED.inventory_accuracy_pct,")
        w(f"  soh_sessions_count      = EXCLUDED.soh_sessions_count,")
        w(f"  total_epc_reads         = EXCLUDED.total_epc_reads,")
        w(f"  unique_skus_counted     = EXCLUDED.unique_skus_counted,")
        w(f"  variance_items_count    = EXCLUDED.variance_items_count;")
        w()

    # ── 7. Auto GRN tasks from positive ERP diff (consecutive XLS pairs) ─────
    w("-- ── 7. Auto GRN Tasks (positive ERP diff → new stock received) ──────────────")
    if len(all_files) >= 2:
        for idx in range(1, len(all_files)):
            _prev_bname, prev_date, prev_rows = all_files[idx - 1]
            _curr_bname, curr_date, curr_rows = all_files[idx]

            prev_prods = aggregate_products(prev_rows)
            curr_prods = aggregate_products(curr_rows)

            grn_items = []   # (style, units_received)
            for style, curr in curr_prods.items():
                prev_exp = prev_prods.get(style, {}).get('expected_total', 0)
                curr_exp = curr.get('expected_total', 0)
                diff = curr_exp - prev_exp
                if diff > 0:
                    grn_items.append((style, diff))

            if not grn_items:
                w(f"-- No positive ERP diff between {prev_date} and {curr_date}")
                w()
                continue

            # Deterministic task ID so re-seeding doesn't duplicate
            task_seed   = f"{store_id}|GRN|{curr_date}"
            grn_task_id = str(uuid.UUID(hashlib.md5(task_seed.encode()).hexdigest()))
            grn_number  = f"GRN-{curr_date.replace('-', '')}"
            total_units = sum(d for _, d in grn_items)

            w(f"-- {grn_number}: {len(grn_items)} SKUs, {total_units} units ({prev_date} → {curr_date})")
            w(f"INSERT INTO refill.refill_tasks")
            w(f"  (id, store_id, task_type, status, priority, source, notes, created_by, created_at, completed_at)")
            w(f"VALUES ({_sq(grn_task_id)}, {_sq(store_id)}, 'replenishment', 'completed', 3, 'erp',")
            w(f"  {_sq(grn_number + f' — {len(grn_items)} SKUs received, {total_units} units from DC')},")
            w(f"  gen_random_uuid(), {_sq(curr_date + 'T08:00:00+05:30')}::timestamptz,")
            w(f"  {_sq(curr_date + 'T10:00:00+05:30')}::timestamptz)")
            w(f"ON CONFLICT (id) DO NOTHING;")
            w()

            # Delete existing items (safe re-seed since task ON CONFLICT does nothing)
            w(f"DELETE FROM refill.refill_task_items WHERE task_id = {_sq(grn_task_id)};")

            # Insert items via _sp temp-table join (style → product UUID)
            for i in range(0, len(grn_items), BATCH):
                chunk = grn_items[i:i + BATCH]
                vals  = [f"  ({_sq(style)}, {qty}::int)" for style, qty in chunk]
                w(f"INSERT INTO refill.refill_task_items")
                w(f"  (id, task_id, product_id, requested_quantity, fulfilled_quantity, status)")
                w(f"SELECT gen_random_uuid(), {_sq(grn_task_id)}, s.pid, v.qty, v.qty, 'fulfilled'")
                w(f"FROM (VALUES")
                w(',\n'.join(vals))
                w(f") AS v(style, qty) JOIN _sp s ON s.style = v.style;")
                w()
    else:
        w("-- Only one XLS file — no consecutive pair to diff for GRN tasks")
        w()

    w("COMMIT;")
    w(f"-- ✓ {len(styles):,} products | {len(epc_rows):,} EPC tags "
      f"| {len(inv_vals):,} inventory rows | {len(reg_rows):,} EPC registry "
      f"| {len(all_files)} SOH sessions")
    return '\n'.join(out)


def run_sql(sql_text, args):
    """Execute SQL via docker exec or direct psql. Returns True on success."""
    sql_bytes = sql_text.encode('utf-8')

    if getattr(args, 'pg_host', None):
        env = os.environ.copy()
        if getattr(args, 'pg_password', None):
            env['PGPASSWORD'] = args.pg_password
        cmd = ['psql', '-h', args.pg_host, '-p', str(args.pg_port),
               '-U', args.pg_user, '-d', args.pg_db]
    else:
        container = getattr(args, 'pg_container', None)
        if not container:
            try:
                names = subprocess.check_output(
                    ['docker', 'ps', '--format', '{{.Names}}'], timeout=10
                ).decode().splitlines()
                container = next(
                    (n for n in names if 'postgres' in n.lower()),
                    'deploy-postgres-1'
                )
            except Exception:
                container = 'deploy-postgres-1'
        info(f"PostgreSQL container: {container}")
        cmd = ['docker', 'exec', '-i', container,
               'psql', '-U', args.pg_user, '-d', args.pg_db]
        env = None

    try:
        result = subprocess.run(
            cmd, input=sql_bytes, env=env,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=600
        )
    except FileNotFoundError as e:
        fail(f"Command not found: {cmd[0]}  — {e}")
        return False
    except subprocess.TimeoutExpired:
        fail("SQL execution timed out after 10 minutes")
        return False

    stdout = result.stdout.decode('utf-8', errors='replace')
    stderr = result.stderr.decode('utf-8', errors='replace')

    committed = 'COMMIT' in stdout and 'ROLLBACK' not in stdout
    if committed:
        insert_lines = [l for l in stdout.splitlines()
                        if l.startswith('INSERT') or l.startswith('UPDATE') or l.startswith('DELETE')]
        ok(f"SQL committed  ({len(insert_lines)} DML statements)")
        return True
    else:
        fail(f"SQL failed / rolled back (exit {result.returncode})")
        # Print last 50 lines so the user can see the actual error
        for line in (stdout + '\n' + stderr).splitlines()[-50:]:
            if line.strip():
                print(f"    {line}", file=sys.stderr)
        return False


# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(
        description='Seed StoreLense from Pantaloons variance XLS file(s)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    # General
    ap.add_argument('--url',           default='http://localhost:8080')
    ap.add_argument('--username',      default='admin')
    ap.add_argument('--password',      default='Admin@StoreLense1')
    ap.add_argument('--file',          help='Single XLS file')
    ap.add_argument('--dir',           default=None,
                    help='Directory of .xlsx files (default: current directory)')
    ap.add_argument('--store-code',    default=None,
                    help='Store code, e.g. P004 (default: derived from --dir folder name)')
    ap.add_argument('--store-name',    default=None)

    # SQL mode — on by default; use --no-sql to force REST mode
    ap.add_argument('--sql',           action='store_true', default=True,
                    help='Fast SQL bulk-insert mode (default: on)')
    ap.add_argument('--no-sql',        action='store_true',
                    help='Force REST API mode instead of SQL')
    ap.add_argument('--sql-out',       default=None,
                    help='Write SQL to this file and exit (do not execute)')
    ap.add_argument('--pg-container',  default=None,
                    help='Docker container running PostgreSQL (auto-detected if omitted)')
    ap.add_argument('--pg-user',       default='postgres',
                    help='PostgreSQL user (default: postgres)')
    ap.add_argument('--pg-db',         default='storelense',
                    help='PostgreSQL database (default: storelense)')
    ap.add_argument('--pg-host',       default=None,
                    help='PostgreSQL host for direct psql (skips docker exec)')
    ap.add_argument('--pg-port',       default='5432')
    ap.add_argument('--pg-password',   default='postgres')

    # REST mode
    ap.add_argument('--workers',       type=int, default=3,
                    help='Parallel REST workers (default 3, REST mode only)')
    ap.add_argument('--skip-products', action='store_true')
    ap.add_argument('--skip-expected', action='store_true')
    ap.add_argument('--sessions-only', action='store_true',
                    help='Only create SOH sessions via REST')
    args = ap.parse_args()

    if args.sessions_only:
        args.skip_products = True
        args.skip_expected = True

    if getattr(args, 'no_sql', False):
        args.sql = False

    # ── Auto-detect dir (current directory) and store code (from dir name)
    xls_dir = args.dir or (os.path.dirname(args.file) if args.file else None) or os.getcwd()
    xls_dir = os.path.abspath(xls_dir)

    store_code = args.store_code or os.path.basename(xls_dir)
    store_name = args.store_name or f'Pantaloons {store_code}'

    # ── Collect XLS files
    xls_files = []
    if args.file:
        xls_files = [args.file]
    else:
        for f in sorted(os.listdir(xls_dir)):
            if f.lower().endswith(('.xlsx', '.xls')) and not f.startswith('~'):
                xls_files.append(os.path.join(xls_dir, f))
    if not xls_files:
        fail(f'No XLS files found in {xls_dir}');  sys.exit(1)

    mode_label = 'SQL bulk-insert' if args.sql else 'REST API'
    print(f"\n{BOLD}{CYAN}  StoreLense XLS Seeder  [{mode_label}]{RESET}")
    print(f"  Gateway  : {args.url}")
    print(f"  Store    : {store_code} — {store_name}")
    print(f"  Dir      : {xls_dir}")
    print(f"  XLS files: {len(xls_files)}")
    for f in xls_files:
        print(f"             {os.path.basename(f)}")
    print()

    # ── Authenticate
    step("Authenticating")
    api = Api(args.url, args.username, args.password)
    ok("Logged in")
    base_url = api.base

    # ── Store + infra (always REST — small number of calls)
    step(f"Store: {store_code}")
    store_id = get_or_create_store(api, store_code, store_name)
    ensure_zones(api, store_id)
    ensure_readers(api, store_id, store_code)

    # ── Load all XLS files
    step("Loading XLS data")
    all_files = []
    for path in xls_files:
        bname    = os.path.basename(path)
        date_str = date_from_filename(path)
        info(f"Reading {bname} …")
        rows = load_xls(path)
        all_files.append((bname, date_str, rows))
        ok(f"  {bname}: {len(rows):,} rows, date={date_str}")

    # ── Union of all products across all days
    step("Aggregating unique products across all days")
    union_products: dict = {}
    for _, _, rows in all_files:
        for style, p in aggregate_products(rows).items():
            if style not in union_products:
                union_products[style] = {**p, 'epc_set': set(p['epcs'])}
            else:
                for epc in p['epcs']:
                    if epc not in union_products[style]['epc_set']:
                        union_products[style]['epcs'].append(epc)
                        union_products[style]['epc_set'].add(epc)
    for p in union_products.values():
        p.pop('epc_set', None)

    total_styles = len(union_products)
    total_epcs   = sum(len(p['epcs']) for p in union_products.values())
    ok(f"  {total_styles:,} unique styles,  {total_epcs:,} unique EPC barcodes")

    _, last_date, last_rows = all_files[-1]
    info(f"Inventory state will use last file: {last_date}")

    # ════════════════════════════════════════════════════════════════
    # SQL MODE — bulk insert via psql
    # ════════════════════════════════════════════════════════════════
    if args.sql:
        step("Generating bulk SQL")
        sql_text = _generate_sql(store_id, union_products, all_files)
        ok(f"  SQL generated — {len(sql_text):,} bytes")

        if args.sql_out:
            with open(args.sql_out, 'w', encoding='utf-8') as f:
                f.write(sql_text)
            ok(f"  SQL written to: {args.sql_out}")
            print()
            print(f"  {BOLD}To execute:{RESET}")
            print(f"  cat {args.sql_out} | docker exec -i deploy-postgres-1 psql -U postgres -d storelense")
        else:
            step("Executing bulk SQL")
            success = run_sql(sql_text, args)
            if not success:
                fail("SQL execution failed — data NOT committed. Fix errors above and re-run.")
                sys.exit(1)

        # KPI aggregation — call for every XLS date so new stores get full history
        step("Triggering KPI aggregation")
        api._refresh()
        kpi_dates = [d for _, d, _ in all_files]
        today = datetime.now().strftime('%Y-%m-%d')
        if today not in kpi_dates:
            kpi_dates.append(today)
        for kpi_date in kpi_dates:
            resp = api.post(f'/api/reporting/kpi/aggregate?storeId={store_id}&date={kpi_date}', timeout=30)
            if resp.get('success'):
                ok(f"KPI aggregated for {store_code} ({kpi_date})")
            else:
                info(f"KPI {kpi_date}: {resp.get('message')}")

        print(f"\n{BOLD}{GREEN}{'═'*52}{RESET}")
        print(f"{BOLD}{GREEN}  Seeding complete!{RESET}")
        print(f"{BOLD}{GREEN}{'═'*52}{RESET}")
        print(f"  Store       : {store_code}  ({store_id})")
        print(f"  Products    : {total_styles:,} styles")
        print(f"  EPC tags    : {total_epcs:,} barcodes")
        print(f"  Sessions    : {len(all_files)} cycle-count days")
        print(f"  Mode        : SQL bulk insert (no REST for data)")
        print()
        return

    # ════════════════════════════════════════════════════════════════
    # REST MODE — original API-per-row approach
    # ════════════════════════════════════════════════════════════════
    style_to_id: dict[str, str] = {}

    if not args.skip_products:
        step(f"Creating {total_styles:,} products ({args.workers} workers)")
        api._refresh()
        token_snap = api.tok

        done_count = errors = 0
        first_errors = []

        def _make_product(item):
            style, p = item
            pid = _create_product(base_url, token_snap, style, p['name'], p['dept'])
            return style, pid

        items = list(union_products.items())
        chunk_size = 500
        for chunk_start in range(0, len(items), chunk_size):
            chunk = items[chunk_start:chunk_start + chunk_size]
            if chunk_start > 0:
                api._refresh();  token_snap = api.tok

            with ThreadPoolExecutor(max_workers=args.workers) as ex:
                futures = {ex.submit(_make_product, item): item[0] for item in chunk}
                for fut in as_completed(futures):
                    style, pid = fut.result()
                    if pid:
                        style_to_id[style] = pid
                    else:
                        errors += 1
                        if len(first_errors) < 5:
                            first_errors.append(style)
                    done_count += 1
                    if done_count % 200 == 0 or done_count == total_styles:
                        progress(done_count, total_styles, "products")
        print()
        ok(f"  {len(style_to_id):,} products ready  ({errors} errors)")
        if first_errors:
            warn(f"  First failed styles: {first_errors}")
            info("  Re-fetching missed products by SKU …")
            for style in list(union_products.keys()):
                if style not in style_to_id:
                    resp2 = _call('GET', f'{base_url}/api/products/by-sku/{style}', token=token_snap)
                    pid   = (resp2.get('data') or {}).get('id')
                    if pid:
                        style_to_id[style] = pid
            ok(f"  After recovery: {len(style_to_id):,} products")

        # Register EPCs
        step(f"Registering {total_epcs:,} EPC barcodes ({args.workers} workers)")
        api._refresh();  token_snap = api.tok

        epc_tasks = [(style_to_id[s], epc)
                     for s, p in union_products.items()
                     if s in style_to_id
                     for epc in p['epcs']]

        epc_done = epc_errors = 0
        for epc_start in range(0, len(epc_tasks), 2000):
            if epc_start > 0:
                api._refresh();  token_snap = api.tok
            chunk = epc_tasks[epc_start:epc_start + 2000]

            def _reg_epc(t, _tok=token_snap):
                _register_epc(base_url, _tok, t[0], t[1])

            with ThreadPoolExecutor(max_workers=args.workers) as ex:
                futures = [ex.submit(_reg_epc, t) for t in chunk]
                for fut in as_completed(futures):
                    try:
                        fut.result()
                    except Exception:
                        epc_errors += 1
                    epc_done += 1
                    if epc_done % 1000 == 0 or epc_done == len(epc_tasks):
                        progress(epc_done, len(epc_tasks), "EPCs registered")
        print()
        ok(f"  EPC registration done  ({epc_errors} errors)")

    else:
        step("Fetching existing product IDs")
        style_to_id = fetch_all_product_ids(api)
        ok(f"  {len(style_to_id):,} products loaded")

    # Set expected inventory
    if not args.skip_expected:
        last_day_prods = aggregate_products(last_rows)
        step(f"Setting ERP expected inventory  (from {last_date})")
        api._refresh();  token_snap = api.tok

        exp_tasks = [(s, p['expected_total'])
                     for s, p in last_day_prods.items()
                     if s in style_to_id and p['expected_total'] > 0]

        exp_done = 0
        def _set_exp(t):
            _set_expected(base_url, token_snap, store_id, style_to_id[t[0]], t[1])

        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futures = [ex.submit(_set_exp, t) for t in exp_tasks]
            for fut in as_completed(futures):
                fut.result()
                exp_done += 1
                if exp_done % 500 == 0 or exp_done == len(exp_tasks):
                    progress(exp_done, len(exp_tasks), "expected set")
        print()
        ok(f"  Expected inventory set for {exp_done:,} styles")

    # SOH / Cycle-Count sessions
    step(f"Creating {len(all_files)} cycle-count SOH sessions (REST)")
    sessions_created = 0
    for idx, (bname, date_str, rows) in enumerate(all_files, 1):
        scan_epcs    = get_scanned_epcs(rows)
        day_prods    = aggregate_products(rows)
        total_exp    = sum(p['expected_total'] for p in day_prods.values())
        accuracy_pct = round(len(scan_epcs) / total_exp * 100, 1) if total_exp else 0
        print(f"\n  [{idx}/{len(all_files)}] {date_str}")
        info(f"  expected={total_exp:,}  scanned={len(scan_epcs):,}  accuracy≈{accuracy_pct}%")

        if idx % 2 == 0:
            api._refresh()

        sess_resp = api.post('/api/soh/sessions', {
            'storeId': store_id, 'sessionType': 'full_store',
            'notes': f'Pantaloons variance count {date_str} — {bname}',
        })
        sid = (sess_resp.get('data') or {}).get('id')
        if not sid:
            fail(f"  Session create failed: {sess_resp.get('message')}")
            continue
        ok(f"  Session {sid[:8]}… created")

        if scan_epcs:
            submit_rfid_batch(api, sid, store_id, scan_epcs, batch_size=300)
        else:
            warn("  No scanned EPCs for this day")

        time.sleep(1)
        done_resp  = api.post(f'/api/soh/sessions/{sid}/complete', {})
        result_acc = (done_resp.get('data') or {}).get('accuracyPct')
        ok(f"  Session complete — system accuracy: {result_acc}%")
        sessions_created += 1

    # KPI aggregation
    step("Triggering KPI aggregation")
    api._refresh()
    today = datetime.now().strftime('%Y-%m-%d')
    resp  = api.post(f'/api/reporting/kpi/aggregate?storeId={store_id}&date={today}', timeout=30)
    if resp.get('success'):
        ok(f"KPI aggregated for {store_code} ({today})")
    else:
        info(f"KPI: {resp.get('message')}")

    print(f"\n{BOLD}{GREEN}{'═'*52}{RESET}")
    print(f"{BOLD}{GREEN}  Seeding complete!{RESET}")
    print(f"{BOLD}{GREEN}{'═'*52}{RESET}")
    print(f"  Store       : {store_code}  ({store_id})")
    print(f"  Products    : {len(style_to_id):,} styles")
    print(f"  EPC tags    : {total_epcs:,} barcodes")
    print(f"  Sessions    : {sessions_created} cycle-count days")
    print(f"  Inventory   : expected from {last_date}")
    print()
    print(f"  {BOLD}Login:{RESET}  {api.base.replace(':8080', '').replace('http://', 'http://')}:3000")
    print()


if __name__ == '__main__':
    main()
