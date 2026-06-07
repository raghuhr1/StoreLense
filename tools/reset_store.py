#!/usr/bin/env python3
"""
StoreLense Store Reset — delete all data for a store

Deletes transactional + configuration data for one store (identified by store code).
Products and EPC tags are shared across stores and are NEVER deleted.

Deletion scope by default:
  ✓ SOH sessions, results, variance
  ✓ Inventory state, EPC registry
  ✓ Refill tasks and task items
  ✓ Daily KPI records
  ✓ RFID reads (raw + processed)

With --full:
  ✓ All of the above, PLUS:
  ✓ RFID readers
  ✓ Zones
  ✓ Users assigned to this store
  ✓ The store record itself

Usage:
  # Dry-run (show row counts, delete nothing)
  python3 tools/reset_store.py --store-code P036 --dry-run

  # Delete transactional data only (keep store, zones, readers, users)
  python3 tools/reset_store.py --store-code P036 --pg-container deploy-postgres-1

  # Delete everything including the store record itself
  python3 tools/reset_store.py --store-code P036 --full --pg-container deploy-postgres-1

  # Write SQL to file for review before executing
  python3 tools/reset_store.py --store-code P036 --sql-out /tmp/reset_p036.sql
  cat /tmp/reset_p036.sql | docker exec -i deploy-postgres-1 psql -U postgres -d storelense
"""

import argparse
import subprocess
import sys

GREEN  = '\033[92m'; RED = '\033[91m'; YELLOW = '\033[93m'
CYAN   = '\033[96m'; BOLD = '\033[1m'; RESET  = '\033[0m'

def ok(msg):   print(f"  {GREEN}✔{RESET}  {msg}")
def fail(msg): print(f"  {RED}✘{RESET}  {msg}", file=sys.stderr)
def info(msg): print(f"  {CYAN}→{RESET}  {msg}")
def step(msg): print(f"\n{BOLD}{CYAN}▶ {msg}{RESET}")
def warn(msg): print(f"  {YELLOW}⚠{RESET}  {msg}")


def _sq(v):
    if v is None:
        return 'NULL'
    return "'" + str(v).replace("'", "''") + "'"


def build_sql(store_code: str, full: bool, dry_run: bool) -> str:
    out = []
    w = lambda s='': out.append(s)

    w(f"-- StoreLense store reset — store code: {store_code}")
    w(f"-- Mode: {'FULL (includes store record)' if full else 'TRANSACTIONAL DATA ONLY'}")
    w(f"-- Dry-run: {dry_run}")
    w()

    if dry_run:
        # Count-only queries — no deletions
        w("-- ── Row counts (dry-run — nothing will be deleted) ─────────────────")
        w(f"\\echo 'Store code: {store_code}'")
        w()
        w(f"SELECT id, store_code, name FROM store.stores WHERE store_code = {_sq(store_code)};")
        w()
        w("\\echo '--- Transactional data ---'")
        w(f"SELECT 'soh.soh_sessions'           AS tbl, COUNT(*) FROM soh.soh_sessions         WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'soh.soh_results'             AS tbl, COUNT(*) FROM soh.soh_results           WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'soh.soh_variance'            AS tbl, COUNT(*) FROM soh.soh_variance          WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'inventory.inventory_state'   AS tbl, COUNT(*) FROM inventory.inventory_state WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'inventory.epc_registry'      AS tbl, COUNT(*) FROM inventory.epc_registry   WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'refill.refill_tasks'         AS tbl, COUNT(*) FROM refill.refill_tasks       WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
        w(f"SELECT 'refill.refill_task_items'    AS tbl, COUNT(*) FROM refill.refill_task_items  WHERE task_id IN (SELECT id FROM refill.refill_tasks WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)}));")
        w(f"SELECT 'reporting.daily_kpi'         AS tbl, COUNT(*) FROM reporting.daily_kpi       WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")

        if full:
            w()
            w("\\echo '--- Configuration data (--full scope) ---'")
            w(f"SELECT 'store.rfid_readers' AS tbl, COUNT(*) FROM store.rfid_readers WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
            w(f"SELECT 'store.zones'        AS tbl, COUNT(*) FROM store.zones        WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
            w(f"SELECT 'auth.users'         AS tbl, COUNT(*) FROM auth.users         WHERE store_id = (SELECT id FROM store.stores WHERE store_code = {_sq(store_code)});")
            w(f"SELECT 'store.stores'       AS tbl, COUNT(*) FROM store.stores       WHERE store_code = {_sq(store_code)};")

        w()
        w("\\echo 'Dry-run complete — no data was deleted.'")
        return '\n'.join(out)

    # Real deletion
    w("\\set ON_ERROR_STOP on")
    w("BEGIN;")
    w()

    # Resolve store_id once into a temp variable
    w("-- ── Resolve store_id ───────────────────────────────────────────────────")
    w(f"\\echo 'Resolving store_id for code {store_code}…'")
    w(f"CREATE TEMP TABLE _sid AS SELECT id FROM store.stores WHERE store_code = {_sq(store_code)};")
    w("DO $$ BEGIN")
    w("  IF NOT EXISTS (SELECT 1 FROM _sid) THEN")
    w(f"    RAISE EXCEPTION 'Store code {store_code} not found — aborting.';")
    w("  END IF;")
    w("END $$;")
    w()

    # Transactional data — delete in FK-safe order
    w("-- ── SOH data ───────────────────────────────────────────────────────────")
    w("\\echo 'Deleting SOH variance…'")
    w("DELETE FROM soh.soh_variance WHERE store_id IN (SELECT id FROM _sid);")

    w("\\echo 'Deleting SOH results…'")
    w("DELETE FROM soh.soh_results WHERE store_id IN (SELECT id FROM _sid);")

    w("\\echo 'Deleting SOH sessions…'")
    w("DELETE FROM soh.soh_sessions WHERE store_id IN (SELECT id FROM _sid);")
    w()

    w("-- ── Inventory data ─────────────────────────────────────────────────────")
    w("\\echo 'Deleting EPC registry…'")
    w("DELETE FROM inventory.epc_registry WHERE store_id IN (SELECT id FROM _sid);")

    w("\\echo 'Deleting inventory state…'")
    w("DELETE FROM inventory.inventory_state WHERE store_id IN (SELECT id FROM _sid);")
    w()

    w("-- ── Refill data ────────────────────────────────────────────────────────")
    w("\\echo 'Deleting refill task items…'")
    w("DELETE FROM refill.refill_task_items WHERE task_id IN (SELECT id FROM refill.refill_tasks WHERE store_id IN (SELECT id FROM _sid));")

    w("\\echo 'Deleting refill tasks…'")
    w("DELETE FROM refill.refill_tasks WHERE store_id IN (SELECT id FROM _sid);")
    w()

    w("-- ── Reporting data ─────────────────────────────────────────────────────")
    w("\\echo 'Deleting daily KPI records…'")
    w("DELETE FROM reporting.daily_kpi WHERE store_id IN (SELECT id FROM _sid);")
    w()

    if full:
        w("-- ── Configuration data (--full) ────────────────────────────────────────")
        w("\\echo 'Deleting RFID readers…'")
        w("DELETE FROM store.rfid_readers WHERE store_id IN (SELECT id FROM _sid);")

        w("\\echo 'Deleting zones…'")
        w("DELETE FROM store.zones WHERE store_id IN (SELECT id FROM _sid);")

        w("\\echo 'Deleting users assigned to this store…'")
        w("DELETE FROM auth.users WHERE store_id IN (SELECT id FROM _sid);")

        w("\\echo 'Deleting store record…'")
        w(f"DELETE FROM store.stores WHERE store_code = {_sq(store_code)};")
        w()

    w("COMMIT;")
    w(f"\\echo 'Reset complete for store {store_code}.'")
    return '\n'.join(out)


def run_sql(sql_text: str, args) -> bool:
    sql_bytes = sql_text.encode('utf-8')

    if getattr(args, 'pg_host', None):
        import os
        env = os.environ.copy()
        if getattr(args, 'pg_password', None):
            env['PGPASSWORD'] = args.pg_password
        cmd = ['psql', '-h', args.pg_host, '-p', str(args.pg_port),
               '-U', args.pg_user, '-d', args.pg_db]
    else:
        container = args.pg_container
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
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120
        )
    except FileNotFoundError as e:
        fail(f"Command not found: {cmd[0]} — {e}")
        return False
    except subprocess.TimeoutExpired:
        fail("SQL timed out after 2 minutes")
        return False

    stdout = result.stdout.decode('utf-8', errors='replace')
    stderr = result.stderr.decode('utf-8', errors='replace')
    combined = stdout + stderr

    print(combined.rstrip())

    if result.returncode != 0 or 'ERROR' in combined:
        fail(f"psql exited {result.returncode}")
        return False

    return True


def main():
    ap = argparse.ArgumentParser(
        description='Delete all data for a StoreLense store',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument('--store-code',    required=True,  help='Store code to reset (e.g. P036)')
    ap.add_argument('--full',          action='store_true',
                    help='Also delete store record, zones, readers, and store-assigned users')
    ap.add_argument('--dry-run',       action='store_true',
                    help='Print row counts only — do not delete anything')
    ap.add_argument('--sql-out',       default=None,
                    help='Write SQL to file instead of executing')
    ap.add_argument('--pg-container',  default=None,   help='Docker container name (auto-detected)')
    ap.add_argument('--pg-user',       default='postgres')
    ap.add_argument('--pg-db',         default='storelense')
    ap.add_argument('--pg-host',       default=None,   help='Postgres host (skips docker exec)')
    ap.add_argument('--pg-port',       default='5432')
    ap.add_argument('--pg-password',   default='postgres')
    args = ap.parse_args()

    store_code = args.store_code.upper()

    print(f"\n{BOLD}{CYAN}  StoreLense Store Reset{RESET}")
    print(f"  Store code : {store_code}")
    print(f"  Mode       : {'DRY-RUN (no changes)' if args.dry_run else 'FULL DELETE (store record + config)' if args.full else 'TRANSACTIONAL DATA ONLY'}")
    print()

    if not args.dry_run and not args.sql_out:
        print(f"  {YELLOW}This will permanently delete data for store {store_code}.{RESET}")
        confirm = input(f"  Type the store code to confirm: ").strip().upper()
        if confirm != store_code:
            print("  Aborted — store code did not match.")
            sys.exit(0)

    step("Generating SQL")
    sql = build_sql(store_code, args.full, args.dry_run)
    ok(f"SQL generated ({len(sql):,} bytes)")

    if args.sql_out:
        with open(args.sql_out, 'w', encoding='utf-8') as f:
            f.write(sql)
        ok(f"SQL written to: {args.sql_out}")
        print()
        print(f"  {BOLD}To execute:{RESET}")
        print(f"  cat {args.sql_out} | docker exec -i deploy-postgres-1 psql -U postgres -d storelense")
        return

    step("Executing SQL")
    success = run_sql(sql, args)

    if success:
        print()
        ok(f"Store {store_code} {'data counted' if args.dry_run else 'data deleted'} successfully.")
    else:
        fail("Reset failed — check error output above.")
        sys.exit(1)


if __name__ == '__main__':
    main()
