# Database Design

**Project:** StoreLense — RFID Store Operations Platform  
**Database:** PostgreSQL 15+  
**Date:** 2026-06-03  
**Version:** 1.0

---

## 1. Overview

The database uses **schema-per-service** isolation on a shared PostgreSQL cluster (Phase 1). Each microservice owns exactly one schema and never issues cross-schema JOINs at runtime — cross-domain references are resolved via application-layer calls or Kafka events.

Nine schemas are defined:

| Schema | Owner Service | Purpose |
|---|---|---|
| `auth` | auth-service | Users, roles, sessions, tokens |
| `stores` | store-service | Store master, zones, RFID readers, config |
| `products` | product-service | Product master, EPC tags, barcodes, categories |
| `inventory` | inventory-service | Current on-hand state and EPC registry |
| `soh` | soh-service | Count sessions, results, variance |
| `refill` | refill-service | Tasks, assignments, fulfilment |
| `rfid` | rfid-processing-service | Raw reads and scan sessions |
| `reporting` | reporting-service | KPI snapshots and materialized summaries |
| `audit` | cross-cutting | Immutable audit log (all schemas write here) |

---

## 2. Conventions

| Convention | Rule |
|---|---|
| Primary keys | `UUID` — `gen_random_uuid()` default |
| Timestamps | `TIMESTAMPTZ` (UTC stored, timezone-aware) |
| `updated_at` | Managed by trigger `fn_set_updated_at()` |
| Soft delete | `is_active BOOLEAN DEFAULT true` — never hard-delete master data |
| Status columns | `VARCHAR(20)` with `CHECK` constraints enumerating valid values |
| Foreign keys | Named `fk_<table>_<column>` — enforced within schema, NOT across schemas |
| Indexes | Named `idx_<table>_<columns>` |
| Unique constraints | Named `uq_<table>_<columns>` |
| Audit | All write operations on critical tables emit a row to `audit.audit_log` via trigger |
| EPC storage | Uppercase hex string, no spaces, e.g. `3034257BF400B71400000064` |

---

## 3. Entity Relationship Summary

```
auth.users ──< auth.user_roles >── auth.roles
auth.users ──< auth.refresh_tokens

stores.stores ──< stores.zones ──< stores.rfid_readers
stores.stores ──< stores.store_config  (1:1)

products.product_categories ──< products.products ──< products.epc_tags
products.products ──< products.barcodes

inventory.inventory_state  ( store_id × product_id × zone_id )
inventory.epc_registry     ( epc × store_id → current status )

soh.soh_sessions ──< soh.soh_session_items
soh.soh_sessions ──< soh.soh_results  (1:1)
soh.soh_results  ──< soh.soh_variance

refill.refill_tasks ──< refill.refill_task_items
refill.refill_tasks ──< refill.refill_assignments  (1 active at a time)

rfid.rfid_sessions ──< rfid.rfid_reads  (partitioned by read_at)

reporting.kpi_daily        ( store_id × kpi_date )
reporting.report_snapshots ( generated files metadata )

audit.audit_log            ( partitioned by event_time, monthly )
```

---

## 4. Schema: `auth`

### 4.1 `auth.roles`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK, DEFAULT gen_random_uuid() | |
| name | VARCHAR(50) | NOT NULL, UNIQUE | ADMIN, STORE_MANAGER, STORE_ASSOCIATE, REFILL_ASSOCIATE |
| description | TEXT | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 4.2 `auth.users`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK, DEFAULT gen_random_uuid() | |
| username | VARCHAR(100) | NOT NULL, UNIQUE | Login identifier |
| email | VARCHAR(255) | NOT NULL, UNIQUE | Encrypted at rest |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt, cost 12 |
| first_name | VARCHAR(100) | NOT NULL | Encrypted at rest |
| last_name | VARCHAR(100) | NOT NULL | Encrypted at rest |
| store_id | UUID | NULLABLE | NULL = Admin (all stores) |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | Soft-disable |
| last_login_at | TIMESTAMPTZ | | |
| password_changed_at | TIMESTAMPTZ | | |
| failed_login_attempts | INT | NOT NULL, DEFAULT 0 | Reset on success |
| locked_until | TIMESTAMPTZ | | NULL = not locked |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Trigger-managed |
| created_by | UUID | FK → auth.users.id | NULL for seed admin |

### 4.3 `auth.user_roles`

| Column | Type | Constraints |
|---|---|---|
| user_id | UUID | NOT NULL, FK → auth.users.id |
| role_id | UUID | NOT NULL, FK → auth.roles.id |
| granted_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() |
| granted_by | UUID | FK → auth.users.id |
| PRIMARY KEY | (user_id, role_id) | |

### 4.4 `auth.refresh_tokens`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| user_id | UUID | NOT NULL, FK → auth.users.id | |
| token_hash | VARCHAR(255) | NOT NULL, UNIQUE | SHA-256 of opaque token |
| expires_at | TIMESTAMPTZ | NOT NULL | 7 days from issue |
| issued_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| revoked_at | TIMESTAMPTZ | | NULL = active |
| device_fingerprint | VARCHAR(255) | | Browser/device hint |
| ip_address | INET | | |

---

## 5. Schema: `stores`

### 5.1 `stores.stores`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_code | VARCHAR(20) | NOT NULL, UNIQUE | Human-readable code |
| name | VARCHAR(255) | NOT NULL | |
| address_line1 | VARCHAR(255) | | |
| address_line2 | VARCHAR(255) | | |
| city | VARCHAR(100) | | |
| state_province | VARCHAR(100) | | |
| postal_code | VARCHAR(20) | | |
| country_code | CHAR(2) | NOT NULL, DEFAULT 'AU' | ISO 3166-1 alpha-2 |
| timezone | VARCHAR(50) | NOT NULL, DEFAULT 'UTC' | IANA timezone |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| erp_store_code | VARCHAR(50) | | ERP cross-reference |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 5.2 `stores.zones`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL, FK → stores.stores.id | |
| zone_code | VARCHAR(50) | NOT NULL | UNIQUE per store |
| name | VARCHAR(255) | NOT NULL | |
| zone_type | VARCHAR(30) | CHECK (floor, backroom, fitting_room, stockroom, display) | |
| display_order | INT | NOT NULL, DEFAULT 0 | |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (store_id, zone_code) | | |

### 5.3 `stores.rfid_readers`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL, FK → stores.stores.id | |
| zone_id | UUID | FK → stores.zones.id | NULL = unassigned |
| reader_code | VARCHAR(50) | NOT NULL | UNIQUE per store |
| reader_type | VARCHAR(20) | NOT NULL, CHECK (fixed, handheld, bluetooth_sled) | |
| ip_address | INET | | Fixed readers only |
| mac_address | MACADDR | | |
| firmware_version | VARCHAR(50) | | |
| antenna_count | SMALLINT | NOT NULL, DEFAULT 4 | |
| tx_power_dbm | NUMERIC(5,2) | | |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| last_heartbeat_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (store_id, reader_code) | | |

### 5.4 `stores.store_config`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL, FK → stores.stores.id, UNIQUE | 1:1 with store |
| rfid_power_dbm | NUMERIC(5,2) | NOT NULL, DEFAULT 30.0 | |
| rfid_session | SMALLINT | NOT NULL, DEFAULT 2 | Gen2 session (0–3) |
| rfid_target | VARCHAR(5) | NOT NULL, DEFAULT 'A' | A or B |
| soh_schedule_cron | VARCHAR(100) | | Cron expression |
| refill_auto_assign | BOOLEAN | NOT NULL, DEFAULT false | |
| erp_sync_enabled | BOOLEAN | NOT NULL, DEFAULT true | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

---

## 6. Schema: `products`

### 6.1 `products.product_categories`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| parent_id | UUID | FK → products.product_categories.id | NULL = root category |
| code | VARCHAR(50) | NOT NULL, UNIQUE | |
| name | VARCHAR(255) | NOT NULL | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 6.2 `products.products`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| sku | VARCHAR(100) | NOT NULL, UNIQUE | Internal SKU |
| name | VARCHAR(500) | NOT NULL | |
| description | TEXT | | |
| category_id | UUID | FK → products.product_categories.id | |
| brand | VARCHAR(255) | | |
| supplier_code | VARCHAR(100) | | |
| erp_product_code | VARCHAR(100) | | ERP cross-reference |
| unit_of_measure | VARCHAR(20) | NOT NULL, DEFAULT 'EACH' | |
| weight_grams | INT | | |
| is_rfid_enabled | BOOLEAN | NOT NULL, DEFAULT true | |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| erp_synced_at | TIMESTAMPTZ | | Last ERP sync time |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 6.3 `products.barcodes`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| product_id | UUID | NOT NULL, FK → products.products.id | |
| barcode_type | VARCHAR(20) | NOT NULL, CHECK (ean13, ean8, upc_a, qr_code, code128) | |
| barcode_value | VARCHAR(100) | NOT NULL, UNIQUE | |
| is_primary | BOOLEAN | NOT NULL, DEFAULT false | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 6.4 `products.epc_tags`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| epc | VARCHAR(128) | NOT NULL, UNIQUE | Uppercase hex, no spaces |
| epc_encoding | VARCHAR(20) | NOT NULL, DEFAULT 'SGTIN_96' | SGTIN_96, SGTIN_198 |
| product_id | UUID | FK → products.products.id | NULL until decoded |
| company_prefix | VARCHAR(20) | | GS1 company prefix |
| item_reference | VARCHAR(20) | | GS1 item reference |
| serial_number | VARCHAR(30) | | GS1 serial |
| is_encoded | BOOLEAN | NOT NULL, DEFAULT false | |
| encoded_at | TIMESTAMPTZ | | |
| encoded_by | UUID | | user_id |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

---

## 7. Schema: `inventory`

### 7.1 `inventory.inventory_state`

One row per store × product × zone. Updated by rfid-processing-service after each session.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL | No FK (cross-schema) |
| product_id | UUID | NOT NULL | No FK (cross-schema) |
| zone_id | UUID | | NULL = store-level aggregate |
| quantity_on_hand | INT | NOT NULL, DEFAULT 0 | Last counted qty |
| quantity_expected | INT | NOT NULL, DEFAULT 0 | From ERP |
| last_counted_at | TIMESTAMPTZ | | |
| last_soh_session_id | UUID | | |
| accuracy_pct | NUMERIC(5,2) | | (on_hand / expected) * 100 |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (store_id, product_id, zone_id) | | |

### 7.2 `inventory.epc_registry`

Current status of every EPC seen at a store.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| epc | VARCHAR(128) | NOT NULL | |
| store_id | UUID | NOT NULL | |
| product_id | UUID | | Resolved from EPC decode |
| zone_id | UUID | | Last seen zone |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'in_store', CHECK (in_store, sold, missing, damaged, transferred) | |
| last_seen_at | TIMESTAMPTZ | | |
| last_seen_by_reader_id | UUID | | |
| first_seen_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (epc, store_id) | | |

---

## 8. Schema: `soh`

### 8.1 `soh.soh_sessions`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL | |
| zone_id | UUID | | NULL = full-store session |
| session_type | VARCHAR(20) | NOT NULL, DEFAULT 'manual', CHECK (manual, scheduled, full_store, spot_check) | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'created', CHECK (created, in_progress, completed, cancelled, failed) | |
| started_by | UUID | NOT NULL | user_id |
| started_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| completed_at | TIMESTAMPTZ | | |
| cancelled_at | TIMESTAMPTZ | | |
| cancellation_reason | TEXT | | |
| total_epc_reads | INT | NOT NULL, DEFAULT 0 | All reads in session |
| unique_epc_count | INT | NOT NULL, DEFAULT 0 | Deduplicated |
| notes | TEXT | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 8.2 `soh.soh_session_items`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| session_id | UUID | NOT NULL, FK → soh.soh_sessions.id | |
| product_id | UUID | NOT NULL | |
| zone_id | UUID | | |
| counted_quantity | INT | NOT NULL, DEFAULT 0 | |
| expected_quantity | INT | NOT NULL, DEFAULT 0 | |
| variance | INT | GENERATED ALWAYS AS (counted_quantity - expected_quantity) STORED | |
| variance_pct | NUMERIC(5,2) | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (session_id, product_id, zone_id) | | |

### 8.3 `soh.soh_results`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| session_id | UUID | NOT NULL, FK → soh.soh_sessions.id, UNIQUE | 1:1 |
| store_id | UUID | NOT NULL | Denormalised for reporting |
| total_products_counted | INT | NOT NULL, DEFAULT 0 | |
| total_units_counted | INT | NOT NULL, DEFAULT 0 | |
| total_units_expected | INT | NOT NULL, DEFAULT 0 | |
| accuracy_pct | NUMERIC(5,2) | | (units_counted / units_expected) × 100 |
| variance_count | INT | NOT NULL, DEFAULT 0 | Products with variance ≠ 0 |
| overcount_items | INT | NOT NULL, DEFAULT 0 | |
| undercount_items | INT | NOT NULL, DEFAULT 0 | |
| result_generated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 8.4 `soh.soh_variance`

Detail rows for each product with non-zero variance. Used for investigation and reporting.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| session_id | UUID | NOT NULL, FK → soh.soh_sessions.id | |
| result_id | UUID | NOT NULL, FK → soh.soh_results.id | |
| store_id | UUID | NOT NULL | |
| product_id | UUID | NOT NULL | |
| zone_id | UUID | | |
| counted_qty | INT | NOT NULL | |
| expected_qty | INT | NOT NULL | |
| variance_qty | INT | NOT NULL | |
| variance_pct | NUMERIC(5,2) | | |
| variance_type | VARCHAR(15) | NOT NULL, CHECK (overcount, undercount, match) | |
| requires_investigation | BOOLEAN | NOT NULL, DEFAULT false | |
| investigation_notes | TEXT | | |
| resolved_at | TIMESTAMPTZ | | |
| resolved_by | UUID | | user_id |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

---

## 9. Schema: `refill`

### 9.1 `refill.refill_tasks`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL | |
| task_type | VARCHAR(20) | NOT NULL, DEFAULT 'replenishment', CHECK (replenishment, urgency, cycle_count) | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'pending', CHECK (pending, assigned, in_progress, completed, cancelled) | |
| priority | SMALLINT | NOT NULL, DEFAULT 5, CHECK (1–10) | 1 = highest |
| source | VARCHAR(20) | NOT NULL, DEFAULT 'manual', CHECK (manual, soh_trigger, scheduled, erp) | |
| source_session_id | UUID | | SOH session that triggered |
| due_date | DATE | | |
| notes | TEXT | | |
| created_by | UUID | NOT NULL | user_id |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| completed_at | TIMESTAMPTZ | | |
| cancelled_at | TIMESTAMPTZ | | |
| cancellation_reason | TEXT | | |

### 9.2 `refill.refill_task_items`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| task_id | UUID | NOT NULL, FK → refill.refill_tasks.id | |
| product_id | UUID | NOT NULL | |
| zone_id | UUID | | Target zone |
| requested_quantity | INT | NOT NULL, DEFAULT 0 | |
| fulfilled_quantity | INT | NOT NULL, DEFAULT 0 | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'pending', CHECK (pending, partial, fulfilled, skipped) | |
| skip_reason | TEXT | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (task_id, product_id, zone_id) | | |

### 9.3 `refill.refill_assignments`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| task_id | UUID | NOT NULL, FK → refill.refill_tasks.id, UNIQUE | One active assignment |
| assigned_to | UUID | NOT NULL | user_id |
| assigned_by | UUID | NOT NULL | user_id |
| assigned_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| accepted_at | TIMESTAMPTZ | | |
| started_at | TIMESTAMPTZ | | |
| completed_at | TIMESTAMPTZ | | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'assigned', CHECK (assigned, accepted, in_progress, completed, reassigned) | |
| notes | TEXT | | |

---

## 10. Schema: `rfid`

### 10.1 `rfid.rfid_sessions`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL | |
| soh_session_id | UUID | | Links to soh.soh_sessions.id |
| reader_id | UUID | | |
| device_id | VARCHAR(100) | | Zebra device serial/ID |
| session_type | VARCHAR(20) | NOT NULL, DEFAULT 'soh', CHECK (soh, refill_verify, spot_check) | |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'open', CHECK (open, closed, cancelled) | |
| started_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| closed_at | TIMESTAMPTZ | | |
| total_read_count | INT | NOT NULL, DEFAULT 0 | |
| unique_epc_count | INT | NOT NULL, DEFAULT 0 | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

### 10.2 `rfid.rfid_reads` (Partitioned)

Partitioned by `read_at` (RANGE, monthly). Expected volume: millions of rows per day across 400 stores.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PK (within partition) | Not UUID for insert performance |
| rfid_session_id | UUID | NOT NULL, FK → rfid.rfid_sessions.id | |
| store_id | UUID | NOT NULL | Partition key helper |
| reader_id | UUID | | |
| epc | VARCHAR(128) | NOT NULL | |
| rssi | NUMERIC(6,2) | | dBm signal strength |
| antenna_port | SMALLINT | | |
| read_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Partition key |
| processed | BOOLEAN | NOT NULL, DEFAULT false | |
| processed_at | TIMESTAMPTZ | | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

**Partitioning:** `PARTITION BY RANGE (read_at)` — one child table per month, auto-created by a scheduled function. Old partitions (> 12 months) detached and archived to S3.

---

## 11. Schema: `reporting`

### 11.1 `reporting.kpi_daily`

Populated nightly by a scheduled job aggregating from soh, refill, and rfid schemas.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | NOT NULL | |
| kpi_date | DATE | NOT NULL | |
| inventory_accuracy_pct | NUMERIC(5,2) | | |
| soh_sessions_count | INT | NOT NULL, DEFAULT 0 | |
| refill_tasks_created | INT | NOT NULL, DEFAULT 0 | |
| refill_tasks_completed | INT | NOT NULL, DEFAULT 0 | |
| refill_completion_rate_pct | NUMERIC(5,2) | | |
| avg_refill_time_minutes | NUMERIC(8,2) | | |
| total_epc_reads | BIGINT | NOT NULL, DEFAULT 0 | |
| unique_skus_counted | INT | NOT NULL, DEFAULT 0 | |
| variance_items_count | INT | NOT NULL, DEFAULT 0 | |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| UNIQUE | (store_id, kpi_date) | | |

### 11.2 `reporting.report_snapshots`

Metadata for generated report files stored in S3.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | |
| store_id | UUID | | NULL = multi-store report |
| report_type | VARCHAR(50) | NOT NULL, CHECK (soh_accuracy, refill_compliance, inventory_variance, store_kpi, executive_summary) | |
| report_date | DATE | NOT NULL | |
| generated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |
| generated_by | UUID | | user_id |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'pending', CHECK (pending, generating, completed, failed) | |
| file_key | VARCHAR(500) | | S3 object key |
| file_format | VARCHAR(10) | CHECK (pdf, csv, xlsx) | |
| file_size_bytes | BIGINT | | |
| parameters | JSONB | | Report filter parameters |
| error_message | TEXT | | |

---

## 12. Schema: `audit`

### 12.1 `audit.audit_log` (Partitioned)

Append-only. Partitioned by `event_time` (monthly). Never updated or deleted — archived to S3 after 90 days.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGSERIAL | PK (within partition) | |
| event_time | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Partition key |
| user_id | UUID | | NULL = system action |
| store_id | UUID | | |
| schema_name | VARCHAR(50) | NOT NULL | |
| table_name | VARCHAR(100) | NOT NULL | |
| operation | VARCHAR(10) | NOT NULL, CHECK (INSERT, UPDATE, DELETE) | |
| record_id | UUID | | PK of changed row |
| old_values | JSONB | | NULL for INSERT |
| new_values | JSONB | | NULL for DELETE |
| ip_address | INET | | |
| correlation_id | VARCHAR(100) | | From X-Correlation-Id header |

---

## 13. Index Strategy

### Performance-critical indexes

```
-- auth
idx_users_store_id              ON auth.users (store_id) WHERE is_active = true
idx_refresh_tokens_user_active  ON auth.refresh_tokens (user_id) WHERE revoked_at IS NULL

-- stores
idx_stores_active               ON stores.stores (is_active, store_code)
idx_zones_store                 ON stores.zones (store_id) WHERE is_active = true
idx_rfid_readers_store_zone     ON stores.rfid_readers (store_id, zone_id)
idx_rfid_readers_heartbeat      ON stores.rfid_readers (last_heartbeat_at)

-- products
idx_products_sku                ON products.products (sku)   -- already unique
idx_products_erp_code           ON products.products (erp_product_code) WHERE erp_product_code IS NOT NULL
idx_epc_tags_product            ON products.epc_tags (product_id)
idx_barcodes_product            ON products.barcodes (product_id)

-- inventory
idx_inventory_state_store       ON inventory.inventory_state (store_id, product_id)
idx_epc_registry_store_status   ON inventory.epc_registry (store_id, status, last_seen_at DESC)
idx_epc_registry_product        ON inventory.epc_registry (product_id, store_id)

-- soh
idx_soh_sessions_store_status   ON soh.soh_sessions (store_id, status, started_at DESC)
idx_soh_sessions_started_by     ON soh.soh_sessions (started_by)
idx_soh_variance_session        ON soh.soh_variance (session_id)
idx_soh_variance_store_date     ON soh.soh_variance (store_id, created_at DESC)

-- refill
idx_refill_tasks_store_status   ON refill.refill_tasks (store_id, status, priority, due_date)
idx_refill_tasks_created_by     ON refill.refill_tasks (created_by)
idx_refill_assignments_user     ON refill.refill_assignments (assigned_to) WHERE status != 'completed'

-- rfid (per partition, applied to parent)
idx_rfid_reads_session          ON rfid.rfid_reads (rfid_session_id, read_at)
idx_rfid_reads_store_epc        ON rfid.rfid_reads (store_id, epc, read_at DESC)
idx_rfid_reads_unprocessed      ON rfid.rfid_reads (processed, read_at) WHERE processed = false

-- reporting
idx_kpi_daily_store_date        ON reporting.kpi_daily (store_id, kpi_date DESC)  -- already unique

-- audit
idx_audit_log_record            ON audit.audit_log (schema_name, table_name, record_id, event_time DESC)
idx_audit_log_user              ON audit.audit_log (user_id, event_time DESC)
```

---

## 14. Partitioning Strategy

| Table | Partition Type | Partition Key | Retention |
|---|---|---|---|
| `rfid.rfid_reads` | RANGE monthly | `read_at` | 12 months online, then S3 archive |
| `audit.audit_log` | RANGE monthly | `event_time` | 90 days online, then S3 archive |

Child partitions are created one month ahead by a scheduled PostgreSQL function (`fn_create_monthly_partitions()`).

---

## 15. Materialized Views

| View | Source | Refresh | Purpose |
|---|---|---|---|
| `reporting.mv_store_accuracy_7d` | soh_results | Nightly | Rolling 7-day accuracy per store |
| `reporting.mv_refill_kpi_30d` | refill_tasks, refill_assignments | Nightly | 30-day refill KPIs per store |
| `reporting.mv_product_variance_30d` | soh_variance | Nightly | Top variance products per store |

---

## 16. Migration Strategy

| Tool | Flyway (Spring Boot integration) |
|---|---|
| Location | `src/main/resources/db/migration/` per service |
| Naming | `V{major}_{minor}__{description}.sql` |
| Baseline | `V1_0__initial_schema.sql` per service |
| Cross-service | No shared migrations — each service owns its schema |
| Rollback | Down migrations (`U{version}__description.sql`) for development only |
| Production | Forward-only; rollback = new migration |
