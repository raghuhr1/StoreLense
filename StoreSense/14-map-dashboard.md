# 14 — Map Dashboard (PAN India Store View)

## Overview

A full-screen interactive map showing all Pantaloons stores across India, with live
inventory accuracy colour-coding per store. Gives operations leadership a single-screen
view of store health without drilling into individual dashboards.

---

## Library: `react-leaflet` + OpenStreetMap

**Recommended over Google Maps / Mapbox** — no API key, no billing, open-source tiles,
works in corporate intranets, ~150 KB bundle addition.

```bash
npm install react-leaflet leaflet
npm install -D @types/leaflet
```

---

## Data Model Changes Required

Add two columns to `stores.stores`:

```sql
ALTER TABLE stores.stores
    ADD COLUMN IF NOT EXISTS latitude  NUMERIC(9,6),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(9,6);
```

Seed manually — one-time effort (~30 Pantaloons cities, 10 minutes):

```sql
UPDATE stores.stores SET latitude = 28.6139, longitude = 77.2090 WHERE store_code = 'P036'; -- Delhi
UPDATE stores.stores SET latitude = 19.0760, longitude = 72.8777 WHERE store_code = 'P037'; -- Mumbai
UPDATE stores.stores SET latitude = 12.9716, longitude = 77.5946 WHERE store_code = 'P004'; -- Bengaluru
-- … one row per store
```

Backend DTO (`StoreResponse`) needs `latitude` and `longitude` fields added.

---

## Page: `/map`

### Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  [StoreLense logo]  Dashboard  Inventory  Analytics  ★ Map  ...    │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─ Legend ───────────────────────────────────┐   │
│  │ Date picker │  │ ● Green  ≥95%  (12)  ● Amber 80–94% (6)   │   │
│  │  [today]    │  │ ● Red   <80%   (3)   ○ Grey  No data  (2) │   │
│  └─────────────┘  └────────────────────────────────────────────┘   │
│                                                                     │
│          ┌──────────────────────────────────────────────┐          │
│          │                                              │          │
│          │          [ INDIA MAP - full screen ]         │          │
│          │                                              │          │
│          │   ●Mumbai  ●Delhi  ●Bengaluru               │          │
│          │        ●Hyderabad    ●Chennai                │          │
│          │                                              │          │
│          └──────────────────────────────────────────────┘          │
│                                                                     │
│  Click a store pin → popup with accuracy, SKU count, last scan,    │
│  and "Open Dashboard →" link                                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Map configuration

- Center: `lat 20.5, lng 78.9` (geographic centre of India)
- Default zoom: `5`
- Tile layer: `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`
- Each store rendered as `<CircleMarker>` (no external PNG needed)

### Pin colour logic

| Colour | Condition              | Source field                        |
|--------|------------------------|-------------------------------------|
| Green  | accuracy ≥ 95%         | `kpi_daily.inventory_accuracy_pct`  |
| Amber  | accuracy 80–94%        | same                                |
| Red    | accuracy < 80%         | same                                |
| Grey   | no KPI row for date    | absence of row                      |

Pin radius: proportional to `unique_skus_counted` (min 8px, max 20px).

### Popup content (on pin click)

```
┌─────────────────────────────────┐
│ Pantaloons Delhi (P036)         │
│ Accuracy:  96.4%   ████████████ │
│ SKUs:      4,210               │
│ Last scan: Jun 06 11:00 AM     │
│            [Open Dashboard →]  │
└─────────────────────────────────┘
```

---

## Data Flow

No new backend endpoints needed for the initial version:

1. `storesApi.list({ size: 100 })` — already exists; returns all stores (needs lat/lng added to DTO)
2. Per-store `reportingApi.kpiRange(storeId, today, today)` in parallel — already exists
3. Join on `storeId` client-side → build pin array

For the final version (if stores grow past ~50), add one new backend endpoint:

```
GET /api/reporting/kpi/network-summary?date=2026-06-07
→ [{ storeId, storeCode, storeName, latitude, longitude, accuracyPct, skuCount, lastScan }]
```

This avoids N parallel KPI calls from the browser.

---

## Implementation Checklist

| # | Task                                                        | Effort  |
|---|-------------------------------------------------------------|---------|
| 1 | Add `latitude`/`longitude` to stores table + seed data      | 1–2 hrs |
| 2 | Add lat/lng to `StoreResponse` DTO + store-service query    | 30 min  |
| 3 | `npm install react-leaflet leaflet @types/leaflet`          | 5 min   |
| 4 | Add Leaflet CSS to `app/layout.tsx` global imports          | 5 min   |
| 5 | Create `frontend/src/app/(protected)/map/page.tsx`          | 2–3 hrs |
| 6 | Add `/map` nav link to sidebar                              | 10 min  |
| 7 | (Optional) Backend `network-summary` endpoint               | 1–2 hrs |

**Total: ~1 working day** (excludes lat/lng data entry for all stores).

---

## What to Skip (V1)

- **Clustering** — not needed until 100+ stores
- **Heatmap layer** — marginal value, complex
- **Real-time WebSocket updates** — daily KPI granularity is sufficient
- **Satellite tile layer** — standard OSM is cleaner for business data
- **Geofencing / radius alerts** — future roadmap
