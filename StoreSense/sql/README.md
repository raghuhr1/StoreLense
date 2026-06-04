# StoreLense — SQL Scripts

PostgreSQL 15+ DDL, functions, views, and seed data.

## Execution Order

Run scripts in numbered order against a fresh `storelense` database:

| Script | Purpose |
|---|---|
| `00_setup.sql` | Extensions, schemas, DB roles, shared trigger function |
| `01_auth.sql` | `auth` schema: users, roles, tokens |
| `02_stores.sql` | `stores` schema: stores, zones, readers, config |
| `03_products.sql` | `products` schema: products, EPC tags, barcodes, categories |
| `04_inventory.sql` | `inventory` schema: inventory state, EPC registry |
| `05_soh.sql` | `soh` schema: count sessions, results, variance |
| `06_refill.sql` | `refill` schema: tasks, items, assignments |
| `07_rfid.sql` | `rfid` schema: sessions, partitioned reads |
| `08_reporting.sql` | `reporting` schema: KPI daily, report snapshots |
| `09_audit.sql` | `audit` schema: partitioned audit log + triggers |
| `10_views.sql` | Materialized views and refresh function |
| `11_functions.sql` | Utility functions (partitioning, KPI upsert, accuracy recalc) |
| `12_seed_data.sql` | Reference data: roles, system admin user, product categories |

## Quick Start (psql)

```bash
createdb storelense
psql -d storelense -v ON_ERROR_STOP=1 \
  -f 00_setup.sql \
  -f 01_auth.sql \
  -f 02_stores.sql \
  -f 03_products.sql \
  -f 04_inventory.sql \
  -f 05_soh.sql \
  -f 06_refill.sql \
  -f 07_rfid.sql \
  -f 08_reporting.sql \
  -f 09_audit.sql \
  -f 10_views.sql \
  -f 11_functions.sql \
  -f 12_seed_data.sql
```

## Spring Boot / Flyway

Each microservice runs its own Flyway migration from `src/main/resources/db/migration/`.  
Scripts here are the canonical reference. Services import only their own schema script as `V1_0__initial_schema.sql`.

## Notes

- **Partition maintenance**: `fn_create_monthly_partitions()` must run on the 1st of each month (pg_cron or Spring scheduler).
- **Materialized views**: `reporting.fn_refresh_all_materialized_views()` runs nightly after KPI aggregation.
- **Admin password**: Replace the bcrypt placeholder in `12_seed_data.sql` before deploying to any environment.
- **DB roles**: Update passwords in `00_setup.sql` — never deploy with `CHANGE_IN_ENV` defaults.
