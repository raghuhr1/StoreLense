#!/usr/bin/env python3
"""
StoreLense XLS → API Seeder
Reads Pantaloons variance XLS file(s) and seeds:
  • Store + Zones (get-or-create)
  • Products (get-or-create by SKU) with EPC barcodes
  • ERP expected inventory quantities  (from last/only XLS file)
  • Cycle-count SOH sessions            (one per XLS file / day)

Columns expected in each XLS:
  dept | style | barcode | item barcode | description | color | size |
  cost | price | expected | scan | Back Stock | Sales Floor |
  variance | difference | ext cost | ext price | loss | scan%

Usage — single file:
  python3 tools/seed_from_xls.py \\
      --url http://host:8080 \\
      --file P036/P036/variance_P037_20260303.xlsx \\
      --store-code P036 --store-name "Pantaloons P036"

Usage — full directory (8 days = 8 SOH sessions):
  python3 tools/seed_from_xls.py \\
      --url http://host:8080 \\
      --dir P036/P036 \\
      --store-code P036 --store-name "Pantaloons P036"

Flags:
  --url           Gateway base URL          (default: http://localhost:8080)
  --username      Admin username            (default: admin)
  --password      Admin password            (default: Admin@StoreLense1)
  --file          Single XLS file path
  --dir           Directory containing .xlsx files  (processed in filename order)
  --store-code    Store code                (default: P036)
  --store-name    Store display name        (default: Pantaloons <store-code>)
  --workers       Parallel API workers      (default: 10)
  --skip-products Skip product + EPC creation  (products must already exist)
  --skip-expected Skip setting expected inventory
  --sessions-only Only create SOH sessions  (implies skip-products + skip-expected)
"""

import argparse
import json
import os
import sys
import time
from collections import defaultdict
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
def _call(method, url, body=None, token=None, timeout=25):
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {'success': False, 'message': str(e)}
    except Exception as e:
        return {'success': False, 'message': str(e)}


class Api:
    def __init__(self, base_url, username, password):
        self.base  = base_url.rstrip('/')
        self._user = username
        self._pass = password
        self.token = None
        self._refresh()

    def _refresh(self):
        resp = _call('POST', f'{self.base}/api/auth/login',
                     {'username': self._user, 'password': self._pass})
        tok = (resp.get('data') or {}).get('accessToken')
        if not tok:
            fail(f"Login failed: {resp.get('message')}")
            sys.exit(1)
        self.token = tok

    def get(self, path, **kw):
        return _call('GET', f'{self.base}{path}', token=self.token, **kw)

    def post(self, path, body=None, **kw):
        return _call('POST', f'{self.base}{path}', body, token=self.token, **kw)

    # thread-safe token getter (token refresh is done before each big batch)
    @property
    def tok(self):
        return self.token


# ── XLS parsing ───────────────────────────────────────────────────────────────
def load_xls(path):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    rows_iter = ws.iter_rows(values_only=True)
    raw_hdr = next(rows_iter)
    header = [str(h).strip().lower() if h else f'col{i}' for i, h in enumerate(raw_hdr)]
    rows = [dict(zip(header, r)) for r in rows_iter if any(v is not None for v in r)]
    wb.close()
    return rows


def clean_barcode(v):
    if v is None or str(v).strip() == '?':
        return None
    s = str(v).strip().split('.')[0]
    return s.zfill(14) if s.isdigit() else None


def date_from_filename(filename):
    """Extract YYYYMMDD from filename like variance_P037_20260303.xlsx → 2026-03-03."""
    base = os.path.basename(filename).replace('.xlsx', '').replace('.xls', '')
    for part in reversed(base.split('_')):
        if part.isdigit() and len(part) == 8:
            return f'{part[:4]}-{part[4:6]}-{part[6:]}'
    return base


# ── Data aggregation ──────────────────────────────────────────────────────────
def aggregate_products(rows):
    """
    Returns dict: style → {dept, style, name, epcs: [], expected_total, scan_total}
    expected_total = sum of max(0, expected) across all barcodes
    scan_total     = count of distinct barcodes with scan >= 1
    """
    products = {}
    for r in rows:
        style = (r.get('style') or '').strip()
        if not style:
            continue
        dept    = (r.get('dept') or '').strip()
        desc    = (r.get('description') or style).strip()
        barcode = clean_barcode(r.get('barcode'))
        expected = max(0, int(r.get('expected') or 0))
        scan     = int(r.get('scan') or 0)

        if style not in products:
            products[style] = {
                'dept': dept, 'style': style, 'name': desc,
                'epcs': [], 'epc_set': set(),
                'expected_total': 0, 'scan_total': 0,
            }
        p = products[style]
        p['expected_total'] += expected
        if scan >= 1:
            p['scan_total'] += 1      # count distinct barcodes found
        if barcode and barcode not in p['epc_set']:
            p['epcs'].append(barcode)
            p['epc_set'].add(barcode)
    # drop helper set before returning
    for p in products.values():
        del p['epc_set']
    return products


def get_scanned_epcs(rows):
    """Unique barcodes where scan >= 1 (for RFID ingest)."""
    seen = set()
    result = []
    for r in rows:
        if int(r.get('scan') or 0) >= 1:
            bc = clean_barcode(r.get('barcode'))
            if bc and bc not in seen:
                result.append(bc)
                seen.add(bc)
    return result


# ── API operations ────────────────────────────────────────────────────────────
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
    # fallback: find existing
    resp2 = api.get('/api/stores?size=200')
    for s in _extract_list(resp2.get('data') or []):
        if s.get('storeCode') == store_code:
            info(f"Store {store_code} already exists → {s['id']}")
            return s['id']
    fail(f"Cannot create or find store {store_code}: {resp.get('message')}")
    sys.exit(1)


def ensure_zones(api, store_id):
    zones = [
        ('FLOOR-GF',   'Ground Floor',   'floor',        1),
        ('FLOOR-FF',   'First Floor',    'floor',        2),
        ('FITTING',    'Fitting Rooms',  'fitting_room', 3),
        ('BACKROOM',   'Backroom',       'backroom',     4),
        ('STOCKROOM',  'Stockroom',      'stockroom',    5),
    ]
    for code, name, ztype, order in zones:
        api.post(f'/api/stores/{store_id}/zones', {
            'zoneCode': code, 'name': name,
            'zoneType': ztype, 'displayOrder': order,
        })
    ok("Zones ensured")


def _extract_list(resp_data):
    """Handle both paginated {content:[]} and plain [] API responses."""
    if isinstance(resp_data, list):
        return resp_data
    if isinstance(resp_data, dict):
        return resp_data.get('content') or []
    return []


def ensure_readers(api, store_id, store_code):
    zones_resp = api.get(f'/api/stores/{store_id}/zones')
    content = _extract_list(zones_resp.get('data') or [])
    floor_id = next((z['id'] for z in content if z.get('zoneType') == 'floor'), None)
    back_id  = next((z['id'] for z in content if z.get('zoneType') == 'backroom'), None)

    readers = [
        (f'{store_code}-FIXED-01',    'fixed',    floor_id, '192.168.10.10', 'R700-3.4.1',    4),
        (f'{store_code}-FIXED-02',    'fixed',    back_id,  '192.168.10.11', 'R700-3.4.1',    4),
        (f'{store_code}-HANDHELD-01', 'handheld', None,     '192.168.10.30', 'RFD8500-2.1.0', 1),
    ]
    for code, rtype, zone_id, ip, fw, antennas in readers:
        body = {
            'readerCode': code, 'readerType': rtype,
            'zoneId': zone_id, 'ipAddress': ip,
            'firmwareVersion': fw, 'antennaCount': antennas,
            'txPowerDbm': 30.0,
        }
        api.post(f'/api/stores/{store_id}/readers', body)
    ok("RFID readers ensured")


def fetch_all_product_ids(api):
    """Page through /api/products and return {sku: id}."""
    result = {}
    page = 0
    while True:
        resp = api.get(f'/api/products?size=200&page={page}')
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
    # already exists → look up by SKU
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
    batches_done = 0
    for i in range(0, total, batch_size):
        batch = epcs[i:i + batch_size]
        reads = [{'epc': e, 'rssi': -65.0, 'antennaPort': j % 4}
                 for j, e in enumerate(batch)]
        api.post('/api/rfid/ingest/batch', {
            'rfidSessionId': session_id,
            'storeId': store_id,
            'deviceId': 'xls-seed-scanner',
            'reads': reads,
        }, timeout=60)
        batches_done += 1
        progress(min(i + batch_size, total), total, f"EPCs submitted")
    print()  # newline after progress bar


# ── Main ──────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(
        description='Seed StoreLense from Pantaloons variance XLS file(s)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument('--url',           default='http://localhost:8080')
    ap.add_argument('--username',      default='admin')
    ap.add_argument('--password',      default='Admin@StoreLense1')
    ap.add_argument('--file',          help='Single XLS file to seed')
    ap.add_argument('--dir',           help='Directory of XLS files (all .xlsx, sorted)')
    ap.add_argument('--store-code',    default='P036')
    ap.add_argument('--store-name',    default=None)
    ap.add_argument('--workers',       type=int, default=10,
                    help='Parallel API worker threads (default 10)')
    ap.add_argument('--skip-products', action='store_true',
                    help='Skip product + EPC creation')
    ap.add_argument('--skip-expected', action='store_true',
                    help='Skip setting ERP expected inventory')
    ap.add_argument('--sessions-only', action='store_true',
                    help='Only create SOH sessions (products must already exist)')
    args = ap.parse_args()

    if args.sessions_only:
        args.skip_products = True
        args.skip_expected = True

    store_code = args.store_code
    store_name = args.store_name or f'Pantaloons {store_code}'

    # ── Collect XLS files
    xls_files = []
    if args.dir:
        for f in sorted(os.listdir(args.dir)):
            if f.lower().endswith('.xlsx') or f.lower().endswith('.xls'):
                xls_files.append(os.path.join(args.dir, f))
    elif args.file:
        xls_files = [args.file]
    else:
        ap.error('Provide --file or --dir')
    if not xls_files:
        fail('No XLS files found')
        sys.exit(1)

    print(f"\n{BOLD}{CYAN}  StoreLense XLS Seeder{RESET}")
    print(f"  Gateway  : {args.url}")
    print(f"  Store    : {store_code} — {store_name}")
    print(f"  XLS files: {len(xls_files)}")
    for f in xls_files:
        print(f"             {os.path.basename(f)}")
    print()

    # ── Authenticate
    step("Authenticating")
    api = Api(args.url, args.username, args.password)
    ok("Logged in")
    base_url = api.base

    # ── Store + infra
    step(f"Store: {store_code}")
    store_id = get_or_create_store(api, store_code, store_name)
    ensure_zones(api, store_id)
    ensure_readers(api, store_id, store_code)

    # ── Load all XLS files
    step("Loading XLS data")
    all_files = []   # list of (basename, date_str, rows)
    for path in xls_files:
        bname = os.path.basename(path)
        date_str = date_from_filename(path)
        info(f"Reading {bname} …")
        rows = load_xls(path)
        all_files.append((bname, date_str, rows))
        ok(f"  {bname}: {len(rows):,} rows, date={date_str}")

    # ── Union of all products across all days
    step("Aggregating unique products across all days")
    union_products: dict = {}   # style → product dict
    for _, _, rows in all_files:
        day_prods = aggregate_products(rows)
        for style, p in day_prods.items():
            if style not in union_products:
                union_products[style] = {**p, 'epc_set': set(p['epcs'])}
            else:
                for epc in p['epcs']:
                    if epc not in union_products[style]['epc_set']:
                        union_products[style]['epcs'].append(epc)
                        union_products[style]['epc_set'].add(epc)

    for p in union_products.values():
        p.pop('epc_set', None)

    total_styles  = len(union_products)
    total_epcs    = sum(len(p['epcs']) for p in union_products.values())
    ok(f"  {total_styles:,} unique styles,  {total_epcs:,} unique EPC barcodes")

    # Use LAST file for expected/scan inventory state
    _, last_date, last_rows = all_files[-1]
    last_day_prods = aggregate_products(last_rows)
    info(f"Inventory state will use last file: {last_date}")

    # ── Create / look up products
    style_to_id: dict[str, str] = {}

    if not args.skip_products:
        step(f"Creating {total_styles:,} products ({args.workers} workers)")
        api._refresh()   # fresh token before bulk work
        token_snap = api.tok

        done_count = 0
        errors = 0

        def _make_product(item):
            style, p = item
            pid = _create_product(base_url, token_snap,
                                  style, p['name'], p['dept'])
            return style, pid

        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futures = {ex.submit(_make_product, (s, p)): s
                       for s, p in union_products.items()}
            for fut in as_completed(futures):
                style, pid = fut.result()
                if pid:
                    style_to_id[style] = pid
                else:
                    errors += 1
                done_count += 1
                if done_count % 500 == 0 or done_count == total_styles:
                    progress(done_count, total_styles, "products")
        print()
        ok(f"  {len(style_to_id):,} products ready  ({errors} errors)")

        # ── Register EPCs
        step(f"Registering {total_epcs:,} EPC barcodes ({args.workers} workers)")
        api._refresh()
        token_snap = api.tok

        epc_tasks = [(style_to_id[s], epc)
                     for s, p in union_products.items()
                     if s in style_to_id
                     for epc in p['epcs']]

        epc_done = epc_errors = 0

        def _reg_epc(t):
            pid, epc = t
            _register_epc(base_url, token_snap, pid, epc)

        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futures = [ex.submit(_reg_epc, t) for t in epc_tasks]
            for fut in as_completed(futures):
                try:
                    fut.result()
                except Exception:
                    epc_errors += 1
                epc_done += 1
                if epc_done % 2000 == 0 or epc_done == len(epc_tasks):
                    progress(epc_done, len(epc_tasks), "EPCs registered")
        print()
        ok(f"  EPC registration done  ({epc_errors} errors)")

    else:
        step("Fetching existing product IDs from API")
        style_to_id = fetch_all_product_ids(api)
        ok(f"  {len(style_to_id):,} products loaded")

    # ── Set expected inventory (from last day)
    if not args.skip_expected:
        step(f"Setting ERP expected inventory  (from {last_date})")
        api._refresh()
        token_snap = api.tok

        exp_tasks = [(s, p['expected_total'])
                     for s, p in last_day_prods.items()
                     if s in style_to_id and p['expected_total'] > 0]

        exp_done = 0

        def _set_exp(t):
            style, qty = t
            _set_expected(base_url, token_snap, store_id, style_to_id[style], qty)

        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            futures = [ex.submit(_set_exp, t) for t in exp_tasks]
            for fut in as_completed(futures):
                fut.result()
                exp_done += 1
                if exp_done % 500 == 0 or exp_done == len(exp_tasks):
                    progress(exp_done, len(exp_tasks), "expected set")
        print()
        ok(f"  Expected inventory set for {exp_done:,} styles")

    # ── SOH / Cycle-Count sessions — one per XLS file/day
    step(f"Creating {len(all_files)} cycle-count SOH sessions")
    sessions_created = 0

    for idx, (bname, date_str, rows) in enumerate(all_files, 1):
        scan_epcs   = get_scanned_epcs(rows)
        day_prods   = aggregate_products(rows)
        total_exp   = sum(p['expected_total'] for p in day_prods.values())
        accuracy_pct = round(len(scan_epcs) / total_exp * 100, 1) if total_exp else 0

        print(f"\n  [{idx}/{len(all_files)}] {date_str}")
        info(f"  expected={total_exp:,}  scanned={len(scan_epcs):,}  "
             f"accuracy≈{accuracy_pct}%")

        if idx % 2 == 0:
            api._refresh()

        # Create session
        sess_resp = api.post('/api/soh/sessions', {
            'storeId': store_id,
            'sessionType': 'full_store',
            'notes': f'Pantaloons variance count {date_str} — {bname}',
        })
        sid = (sess_resp.get('data') or {}).get('id')
        if not sid:
            fail(f"  Session create failed: {sess_resp.get('message')}")
            continue
        ok(f"  Session {sid[:8]}… created")

        # Submit scanned EPCs as RFID reads
        if scan_epcs:
            submit_rfid_batch(api, sid, store_id, scan_epcs, batch_size=300)
        else:
            warn("  No scanned EPCs for this day")

        time.sleep(1)   # let Kafka settle

        # Complete session
        done_resp = api.post(f'/api/soh/sessions/{sid}/complete', {})
        result_acc = (done_resp.get('data') or {}).get('accuracyPct')
        ok(f"  Session complete — system accuracy: {result_acc}%")
        sessions_created += 1

    # ── KPI aggregation
    step("Triggering KPI aggregation")
    api._refresh()
    today = datetime.now().strftime('%Y-%m-%d')
    resp  = api.post(
        f'/api/reporting/kpi/aggregate?storeId={store_id}&date={today}',
        timeout=30)
    if resp.get('success'):
        ok(f"KPI aggregated for {store_code} ({today})")
    else:
        info(f"KPI: {resp.get('message')}")

    # ── Summary
    print(f"\n{BOLD}{GREEN}{'═'*52}{RESET}")
    print(f"{BOLD}{GREEN}  Seeding complete!{RESET}")
    print(f"{BOLD}{GREEN}{'═'*52}{RESET}")
    print(f"  Store       : {store_code}  ({store_id})")
    print(f"  Products    : {len(style_to_id):,} styles")
    print(f"  EPC tags    : {total_epcs:,} barcodes")
    print(f"  Sessions    : {sessions_created} cycle-count days")
    print(f"  Inventory   : expected from {last_date}")
    print()
    print(f"  {BOLD}Login:{RESET}  {api.base.replace(':8080','').replace('http://','http://')}:3000")
    print()


if __name__ == '__main__':
    main()
