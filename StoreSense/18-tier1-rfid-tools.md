# Document 18 — Tier 1 RFID Tools: Implementation Guide

**Project:** StoreLense Mobile (android-soh)  
**Version:** 1.0  
**Status:** Implemented

---

## 1. Overview

Tier 1 adds three RFID-native tools to the Android app that mirror capabilities found in enterprise tools like ItemOptix, Truevue (Sensormatic), and TagID. These run fully offline for search and use live RFID reads for locating and counting.

| Feature | Description | Offline? |
|---------|-------------|----------|
| **Product Search** | Search the full product catalog by name, SKU, brand, category | Yes — SQLite LIKE |
| **Item Locator** | Use RSSI signal strength to find a specific tagged item | RFID required |
| **Quick Spot Count** | Rapidly scan a zone/fixture to see what products are present | RFID required |

---

## 2. Architecture

### 2.1 Layer Diagram

```
┌──────────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                          │
│  ProductSearchScreen  ItemLocatorScreen  QuickSpotCountScreen│
└───────────────┬──────────────────────────────────────────────┘
                │ StateFlow / SharedFlow
┌───────────────▼──────────────────────────────────────────────┐
│  ViewModel Layer                                             │
│  ProductSearchViewModel  ItemLocatorViewModel                │
│  QuickSpotCountViewModel                                     │
└───────────────┬──────────────────────────────────────────────┘
                │ suspend / Flow
┌───────────────▼────────────┬─────────────────────────────────┐
│  ProductRepository         │  RfidReader (interface)         │
│  (API + Room cache)        │  ↓ EmDkRfidReader (Zebra)       │
└───────────────┬────────────┘  MockRfidReader (debug)         │
                │                                              │
┌───────────────▼──────────────────────────────────────────────┐
│  Room Database (v2)                                          │
│  products table + indices (sku, erpCode, storeId)            │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 New Files

```
android-soh/app/src/main/java/com/storelense/mobile/
├── data/
│   ├── local/
│   │   ├── entity/Entities.kt          ← + ProductEntity
│   │   ├── dao/Daos.kt                 ← + ProductDao
│   │   └── AppDatabase.kt              ← v2, MIGRATION_1_2
│   ├── remote/
│   │   ├── ApiService.kt               ← + product endpoints
│   │   └── dto/Dtos.kt                 ← + ProductDto, EpcLocationDto
│   └── repository/
│       └── ProductRepository.kt        ← NEW
├── work/
│   └── ProductSyncWorker.kt            ← NEW
├── ui/
│   ├── products/
│   │   ├── ProductSearchViewModel.kt   ← NEW
│   │   └── ProductSearchScreen.kt      ← NEW
│   ├── locator/
│   │   ├── ItemLocatorViewModel.kt     ← NEW
│   │   └── ItemLocatorScreen.kt        ← NEW
│   ├── spotcount/
│   │   ├── QuickSpotCountViewModel.kt  ← NEW
│   │   └── QuickSpotCountScreen.kt     ← NEW
│   ├── home/HomeScreen.kt              ← updated: 3 new cards
│   └── navigation/AppNavigation.kt     ← updated: 4 new routes
└── di/DatabaseModule.kt                ← updated: productDao()
```

---

## 3. Product Search

### 3.1 How It Works

The product catalog is downloaded from `GET /api/products?storeId=<id>` in pages of 200, stored in the local Room `products` table, and searched offline using SQLite LIKE queries.

**Search ranks results:**
1. Name starts with query (most relevant)
2. SKU starts with query
3. Anywhere match (name, sku, brand, category, erpCode, description)

### 3.2 Offline Sync

| Trigger | Mechanism |
|---------|-----------|
| Login | `ProductSyncWorker` one-time work via WorkManager (`ExistingWorkPolicy.REPLACE`) |
| Background | `ProductSyncWorker` periodic every 6 hours when network is connected |
| Manual | Sync icon button in Product Search top bar |

`ProductSyncWorker` is a `@HiltWorker` (Hilt-injected `CoroutineWorker`). If the network fails it retries with exponential backoff. If `auth.storeId` is null (not logged in) it exits silently.

### 3.3 ProductDao — Key Queries

```kotlin
// Offline search (LIKE on 6 columns, relevance-ordered)
@Query("""
    SELECT * FROM products
    WHERE (storeId = :storeId OR :storeId = '')
      AND (name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%'
        OR brand LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'
        OR erpCode LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
    ORDER BY CASE WHEN name LIKE :query || '%' THEN 0
                  WHEN sku LIKE :query || '%' THEN 1 ELSE 2 END, name ASC
    LIMIT 50
""")
suspend fun search(query: String, storeId: String): List<ProductEntity>

// EPC/SKU lookup (used by Item Locator and Spot Count)
@Query("SELECT * FROM products WHERE sku = :epc OR erpCode = :epc LIMIT 1")
suspend fun getByEpc(epc: String): ProductEntity?
```

### 3.4 UI States

| State | What the user sees |
|-------|--------------------|
| No catalog | Red banner "No offline catalog — tap Sync" |
| Has catalog | Green banner "N products available offline" |
| Typing | 300ms debounce, then spinner |
| Results | Cards with name, SKU chip, brand chip, stock badge (green/amber/red) |
| No match | "No results for 'X'" with hint to sync |

### 3.5 Stock Badge Colour Rules

| On-hand / Expected | Badge colour |
|-------------------|--------------|
| ≥ 90% | Green |
| 60–89% | Amber |
| < 60% | Red |

---

## 4. Item Locator

### 4.1 How It Works

The user enters an EPC or SKU, taps **Start Locating**, and the RFID reader begins a continuous scan. All detected tags are collected. For each tag:

1. RSSI readings are smoothed over a rolling 5-read window (reduces antenna flutter).
2. The smoothed value is mapped to a proximity level.
3. The UI updates the hot/cold radar and the nearby tag list.

### 4.2 RSSI → Proximity Mapping

| Smoothed RSSI | Level | UI Colour |
|--------------|-------|-----------|
| ≥ −50 dBm | HOT | Red |
| −50 to −65 dBm | NEAR | Orange |
| −65 to −75 dBm | MEDIUM | Yellow |
| < −75 dBm | FAR | Blue |

> Typical Zebra FX9600 range: −90 dBm (far end of read range) to −35 dBm (directly in front of antenna at 1W).

### 4.3 Proximity Radar UI

- **Idle:** Grey Radar icon, static circle.
- **Scanning:** Coloured circle pulses (infinite animation, 800ms period).
- **NEAR / HOT:** Colour transitions through yellow → orange → red.
- **Found (HOT + target EPC matched):** Phase changes to `LocatorPhase.Found`.

### 4.4 Nearby Tag List

All detected tags are shown ordered by descending RSSI. Each row:
- Left colour bar = proximity colour
- Product name (if found in local catalog) or EPC suffix
- RSSI value and proximity label
- GPS pin icon if this tag is the current target

### 4.5 Deep-Link Navigation

The Item Locator screen accepts an optional initial EPC via navigation argument:

```
// Launch with no pre-selected EPC
nav.navigate(Routes.ITEM_LOCATOR)

// Launch pre-targeted to a specific EPC (e.g. from Product Search → Locate)
nav.navigate(Routes.itemLocator("E2003412B7A0003300010001"))
```

---

## 5. Quick Spot Count

### 5.1 How It Works

A lightweight zone/fixture scan that answers: **"What is physically present in this area right now?"**

1. User optionally names the zone (e.g. "Floor 2 – Rack A3").
2. Taps **Start Scanning** — RFID reader scans continuously.
3. Each new unique EPC is added to the list; duplicates are discarded.
4. For each EPC, the app does a Room lookup to resolve it to a product name.
5. User can **Pause / Resume** at any time.
6. On **Finish**, the RFID reader disconnects and a grouped summary is shown.

### 5.2 Phases

```
Idle ──Start──► Scanning ──Pause──► Paused ──Resume──► Scanning
                    └──Finish──►  Done
```

Any phase transitions back to `Idle` via the **Reset** (↺) button in the top bar.

### 5.3 Done Summary

The final summary groups items by SKU and shows:
- Product name (from local catalog) or raw EPC suffix if unknown
- Count of EPC tags for that SKU (e.g. "× 3" = 3 units of that product scanned)

This is useful for:
- Confirming a fixture restock was completed
- Quickly checking what's on a display stand
- Post-replenishment verification (compare against refill task)

### 5.4 Stats Bar Colours

| Phase | Stats bar colour |
|-------|-----------------|
| Scanning | Primary blue (pulsing active state) |
| Paused | Orange |
| Done | Green |

---

## 6. Room Database Migration

The product catalog requires a Room schema migration from **version 1 → 2**.

### Migration SQL (MIGRATION_1_2)

```sql
CREATE TABLE IF NOT EXISTS products (
    id          TEXT NOT NULL PRIMARY KEY,
    sku         TEXT NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    brand       TEXT,
    category    TEXT,
    erpCode     TEXT,
    storeId     TEXT,
    onHandQty   INTEGER NOT NULL DEFAULT 0,
    expectedQty INTEGER NOT NULL DEFAULT 0,
    imageUrl    TEXT,
    lastSyncedAt INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_products_sku     ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_erpCode  ON products(erpCode);
CREATE INDEX IF NOT EXISTS idx_products_storeId  ON products(storeId);
```

`DatabaseModule` uses `addMigrations(AppDatabase.MIGRATION_1_2)` instead of `fallbackToDestructiveMigration()`, so existing EPC scan data (sessions, reads) is preserved on upgrade.

---

## 7. Backend API Dependencies

### Required Endpoints

| Method | Path | Used By |
|--------|------|---------|
| `GET` | `/api/products?storeId=&page=&size=` | ProductSyncWorker (paged, 200/page) |
| `GET` | `/api/products/{id}` | Direct product lookup |
| `GET` | `/api/inventory/epc/{epc}` | EpcLocationDto (last known zone) |

### ProductDto Contract

```json
{
  "id": "uuid",
  "sku": "P-1234",
  "name": "Levi's 511 Slim Fit 32x30",
  "description": "...",
  "brand": "Levi's",
  "category": "Denim",
  "erp_code": "MAT-001234",
  "storeId": "store-uuid",
  "onHandQty": 12,
  "expectedQty": 15,
  "imageUrl": null
}
```

> `erp_code` uses snake_case (Gson `@SerializedName` maps to `erpCode` in the DTO).

---

## 8. Navigation Routes

```kotlin
Routes.PRODUCT_SEARCH   = "product_search"
Routes.ITEM_LOCATOR     = "item_locator"
Routes.ITEM_LOCATOR_EPC = "item_locator/{epc}"   // optional deep-link
Routes.SPOT_COUNT       = "spot_count"
```

All are accessible from the **RFID Tools** section on the Home screen.

---

## 9. Background Sync Schedule

| Worker | Period | Policy | Constraint |
|--------|--------|--------|-----------|
| `ProductSyncWorker` | 6 hours | `KEEP` | Network connected |
| `RefillSyncWorker` | 15 minutes | `KEEP` | Network connected |
| Login one-time sync | On login | `REPLACE` | Network connected |

`KEEP` policy means if the periodic work is already scheduled, the existing schedule is retained (prevents clock drift from repeated calls to `enqueueUniquePeriodicWork`).

---

## 10. Testing Checklist

### Product Search
- [ ] Install fresh app — shows "No offline catalog" banner
- [ ] Log in — one-time sync fires; catalog count updates after ~10 seconds on WiFi
- [ ] Search "shirt" — results appear within 1 second using offline DB
- [ ] Search returns max 50 results ordered by relevance
- [ ] Tap sync icon manually — progress spinner shows, count refreshes
- [ ] Network off — search still works from cached data

### Item Locator
- [ ] Enter EPC → product name resolves from local catalog below input
- [ ] Tap Start — radar circle animates, tag list populates
- [ ] Move device toward tagged item — RSSI increases, colour transitions FAR→HOT
- [ ] Target EPC appears with GPS pin icon in tag list
- [ ] Tap Stop — RFID reader disconnects, phase returns to Idle
- [ ] Navigate back — RFID reader disconnects cleanly (onCleared called)

### Quick Spot Count
- [ ] Enter zone name (optional) → tap Start
- [ ] Tags appear in list as they're scanned; counter increments
- [ ] Each EPC only counted once (deduplicated set)
- [ ] Product names resolve for known EPC/SKU values
- [ ] Pause → counter stops; Resume → counter continues
- [ ] Finish → Done summary shows grouped SKU counts
- [ ] Reset (↺) button clears all data and returns to Idle

---

## 11. Known Constraints

| Constraint | Detail |
|-----------|--------|
| EPC→Product lookup | `getByEpc` matches on `sku` OR `erpCode` column. EPC128 format items that don't map to either will show "Unknown" in Locator/SpotCount. |
| RSSI accuracy | Zebra RFID RSSI varies ±5 dBm with antenna orientation. The 5-read rolling average reduces jitter but rapid movement still causes transitions. |
| Catalog size | LIKE queries on 50,000 rows with `name LIKE '%query%'` take ~80–120ms on a Zebra TC57. `name LIKE 'query%'` (prefix) is faster; relevance ordering exploits this. |
| Offline stock figures | `onHandQty`/`expectedQty` in the local catalog reflect the last sync, not real-time inventory. They are informational only in the RFID tools. |
| Android 10+ (API 29+) | App targets API 29 minimum. WorkManager, Room, and EMDK RFID all work on API 29–35. |
