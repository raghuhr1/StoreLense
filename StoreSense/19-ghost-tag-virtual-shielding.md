# Document 19 — Ghost Tag Classification & Virtual Shielding

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.0
**Date:** 2026-06-09
**Status:** Proposed — Pending Prioritisation

---

## 1. Executive Summary

This document covers two complementary RFID signal-intelligence features that improve inventory count accuracy without any hardware changes:

| Feature | Problem Solved | Primary Beneficiary |
|---|---|---|
| **Ghost Tag Classification** | Tags read by the scanner that do not physically exist in the scanned area — either never commissioned, decommissioned, or RF artefacts | Loss Prevention, Inventory Accuracy KPI |
| **Virtual Shielding** | Tags from adjacent zones bleeding into a scan session, inflating zone counts and misassigning items to the wrong zone | Store Associates, SOH Accuracy, Zone-Level Reporting |

Both features operate on signal data (`rssi`, `antennaPort`, `readAt`) already captured in every `EpcRead`. Neither requires new hardware, fixed readers, or changes to the store layout. All classification is done in software — on-device for immediate feedback, confirmed server-side after upload.

---

## 2. Business Requirements

### 2.1 Context

StoreLense targets ≥ 98% inventory accuracy (BR-01). Two classes of error undermine this target beyond genuine stock discrepancies:

1. **Ghost reads** — the system counts an item that is not really there, inflating on-hand figures
2. **Zone misassignment from RF bleed** — an item is counted in the wrong zone, causing false replenishment triggers and inaccurate floor vs. backroom splits

Both produce inventory records that look correct on the count report but diverge from physical reality, leading to failed customer service events, incorrect replenishment, and loss-prevention blind spots.

### 2.2 Business Requirements

| ID | Requirement | Measure |
|---|---|---|
| BR-GV-01 | The system shall identify and flag EPC reads that have no registered product master entry | Ghost tag alert raised within 1 scan session |
| BR-GV-02 | Ghost tag alerts shall be visible to Store Managers on the web dashboard and to associates on the Android app | Dashboard panel populated within 30 seconds of session upload |
| BR-GV-03 | The system shall distinguish between ghost tags caused by unknown EPCs (unregistered) and ghost tags caused by RF bleed from adjacent zones | Separate classification shown on session result |
| BR-GV-04 | Zone-level inventory counts shall exclude with high confidence EPCs that are physically located in an adjacent zone | Bleed-through rate < 5% of total reads per zone scan |
| BR-GV-05 | Associates shall be able to review and override any EPC classification before it is committed to the inventory record | Override action captured in audit log |
| BR-GV-06 | Shielding and ghost classification shall not require fixed RFID reader infrastructure — all logic must function with the Zebra handheld only | No fixed reader dependency in core logic |
| BR-GV-07 | The system shall trend ghost tag and bleed-through rates over time so store managers can identify deteriorating RF environments or tag quality issues | KPI time-series available via the reporting API |

### 2.3 Stakeholders

| Role | Interest |
|---|---|
| Store Manager | Accurate SOH; identify zones with chronic bleed or ghost-read problems |
| Store Associate | Clear scan guidance; review/override suspicious reads before submitting |
| Loss Prevention | Ghost tags from decommissioned or counterfeited items; unexpected zone appearances |
| Retail Operations Director | Inventory accuracy KPI improvement; fewer false replenishment tasks |
| ERP Team | Cleaner SOH pushes; fewer manual corrections after cycle counts |

---

## 3. Feature 1 — Ghost Tag Classification

### 3.1 Definition

A **ghost tag** is an EPC that appears in a scan session but does not correspond to an item that should be physically present in the scanned area. There are three distinct causes, each requiring different handling:

| Ghost Type | Cause | Example |
|---|---|---|
| **Unregistered EPC** | EPC not in the product master; tag was never commissioned in this system | Competitor item, counterfeit, display tag |
| **Decommissioned EPC** | EPC was valid but the item was sold, returned, or voided — the tag was not deactivated at exit | Sold item where EAS/RFID deactivation failed at POS |
| **RF Artefact** | EPC appears due to RF reflection or multipath — the tag does not physically exist near the reader | Metal shelving resonance, reader noise, corrupted read |

### 3.2 Business Impact of Undetected Ghost Tags

- **Inflated SOH** — system believes an item is present; replenishment is not triggered; shelf goes empty
- **False accuracy** — SOH count shows 100% but a ghost tag is masking a real missing item
- **Loss prevention failure** — a decommissioned EPC reappearing may indicate tag cloning or bypassed EAS
- **Wasted investigation time** — associates are sent to locate items that do not exist

### 3.3 Approach 1 — RSSI Threshold + Read-Count Confidence Score

#### How it works

Every EPC read carries `rssi` (signal strength) and a read count (how many times the EPC was seen during the session). Real, physically present tags produce a characteristic read profile: moderate-to-strong RSSI and multiple reads as the associate passes the item. Artefact ghost tags appear with very weak RSSI and only 1–2 reads.

**Scoring formula (per EPC, post-scan):**

```
readCountScore = clamp(readCount / threshold, 0.0, 1.0)
rssiScore      = clamp((rssi - rssiFloor) / rssiRange, 0.0, 1.0)
confidence     = (readCountScore × 0.6) + (rssiScore × 0.4)

confidence ≥ 0.7  → HIGH    (include in count)
confidence 0.3–0.7 → MEDIUM (flag for review)
confidence < 0.3  → LOW     (probable ghost / artefact)
```

Weights (0.6 / 0.4) favour read count over RSSI because RSSI is affected by tag orientation and nearby materials; read count is a more stable signal.

**Configurable per zone type:**

| Zone | Read count threshold | RSSI floor |
|---|---|---|
| Dense apparel rack | 4 reads | −70 dBm |
| Shoe wall (sparse) | 2 reads | −68 dBm |
| High-value watch items | 1 read | −85 dBm (never filter) |

#### Ghost type determination

After confidence scoring:
- HIGH confidence + EPC not in product master → `GHOST_UNREGISTERED`
- HIGH confidence + EPC in product master but status = sold/voided → `GHOST_DECOMMISSIONED`
- LOW confidence + EPC in product master → `GHOST_RF_ARTEFACT` (likely bleed or noise)

#### Tradeoffs

| Pro | Con |
|---|---|
| Uses data already in `EpcRead` — no new hardware | Threshold needs per-store calibration; dense metal racking attenuates real tags |
| Immediate on-device result | Read count requires keeping raw reads, not deduplicating on first seen |
| Configurable sensitivity per zone | RSSI is not reliable as a sole signal — must be combined with read count |

---

### 3.4 Approach 2 — Antenna Port Triangulation

#### How it works

A tag physically present in a zone is typically readable from multiple antenna ports at varying RSSI (different angles of the handheld as the associate moves). A ghost tag from an RF artefact or distant adjacent zone is typically visible on only **one antenna port** — the one that happened to point toward the bleed source momentarily.

**Classification rule:**

```
antennaPortsSeenCount = distinct antenna ports that read this EPC

antennaPortsSeenCount ≥ 2  → corroborating reads → raises confidence
antennaPortsSeenCount = 1  + low read count → suspicious → lowers confidence
```

This applies to handheld readers with multiple antenna ports (Zebra RFD90) and to future fixed multi-port reader deployments.

#### Tradeoffs

| Pro | Con |
|---|---|
| Strong signal for single-port bleed or noise artefacts | Single-port handheld (RFD40) provides no benefit — antenna count = 1 always |
| Complements Approach 1 without replacing it | Requires antenna port to be populated in `EpcRead` — currently optional (`antennaPort: Int?`) |
| Low compute cost | Associates may not change hand angle enough to activate multiple ports |

---

### 3.5 Approach 3 — Server-Side Expected-Set Cross-Check

#### How it works

The SOH session already carries `expectedEpcs` — the list of EPCs the system expects to find based on the last confirmed inventory state. After a scan session is uploaded, the backend classifies every scanned EPC against three sets:

```
Set A: expectedEpcs for this zone/session  (should be present)
Set B: product master (commissioned EPCs)
Set C: adjacent zone's recent scan results  (known neighbours)

Scanned EPC in A ∩ B         → CONFIRMED (expected and found)
Scanned EPC in B, not in A   → UNEXPECTED (found but not expected — investigate)
Scanned EPC in C only        → BLEED_FROM_NEIGHBOUR (virtual shielding territory)
Scanned EPC not in A, B, C   → GHOST_UNREGISTERED (never seen before)
Scanned EPC in B, status=sold → GHOST_DECOMMISSIONED
```

This runs entirely server-side after upload — no on-device logic change. The result is returned in the session result payload as a `classification` field per EPC.

#### Tradeoffs

| Pro | Con |
|---|---|
| Highest accuracy — uses full historical context | Post-hoc only — associate has already submitted the scan |
| No change to mobile scan logic | Requires up-to-date `expectedEpcs` per zone; stale expected set reduces accuracy |
| Catches all three ghost types cleanly | Requires adjacent zone scan history to be current |
| Enables ML training data collection over time | First few stores produce noisy results until baseline accumulates |

---

### 3.6 Ghost Tag Classification — Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  Zebra Handheld (Android)                                           │
│                                                                     │
│  RfidReader.reads → raw EpcRead stream                              │
│       ↓                                                             │
│  Accumulator: Map<epc, ReadAccumulator>                             │
│     { readCount, peakRssi, rssiHistory[], antennaPorts[] }          │
│       ↓  (on finishCount / complete)                                │
│  GhostClassifier.classify(accumulator, productCache)                │
│     → per-EPC: confidence score + ghost type                        │
│       ↓                                                             │
│  SpotCountResult / ScanResult                                       │
│     confirmed[]  |  needsReview[]  |  ghostTags[]                  │
│       ↓                                                             │
│  Associate reviews needsReview items → Confirm / Exclude            │
│       ↓                                                             │
│  RfidBatchRequest { reads: [{ epc, rssi, antennaPort, readCount }] }│
│  uploaded via ProductSyncWorker / SohUploadWorker                   │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTPS POST
┌────────────────────────────▼────────────────────────────────────────┐
│  Backend — RFID Processing Pipeline                                 │
│                                                                     │
│  TagIngestService                                                   │
│    → receives RfidBatchRequest                                      │
│    → persists raw reads to rfid_reads table                         │
│       ↓                                                             │
│  GhostTagClassificationService                                      │
│    → cross-checks against product_master (Set B)                   │
│    → cross-checks against session.expectedEpcs (Set A)             │
│    → cross-checks against adjacent zone scans (Set C)              │
│    → assigns: CONFIRMED / UNEXPECTED / GHOST_UNREGISTERED /        │
│               GHOST_DECOMMISSIONED / BLEED_FROM_NEIGHBOUR          │
│       ↓                                                             │
│  SohCalculatorService                                               │
│    → uses only CONFIRMED + associate-confirmed EPCs for SOH result  │
│       ↓                                                             │
│  GhostTagAlertService                                               │
│    → raises alerts for GHOST_UNREGISTERED (loss prevention)        │
│    → raises alerts for GHOST_DECOMMISSIONED (EAS failure)          │
│       ↓                                                             │
│  ReportingService                                                   │
│    → aggregates ghost_rate, bleed_rate into daily KPI rows         │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 3.7 Ghost Tag Dashboard Design

#### Android — Scan Result Screen

```
┌────────────────────────────────────────────────┐
│  Zone: Gondola A-3          Scan: 45 sec        │
│                                                 │
│  ██████████████████░░░░░░▒▒▒                    │
│  68 Confirmed    9 Review   3 Ghost             │
│                                                 │
│  Count submitted: 68                            │
│                                                 │
│  ▼ Ghost Tags (3)                               │
│  ────────────────────────────────               │
│  EPC: ...3A4F120  UNREGISTERED  [Report]        │
│  EPC: ...9B2C008  DECOMMISSIONED [Investigate]  │
│  EPC: ...FF10023  RF ARTEFACT   [Dismiss]       │
└────────────────────────────────────────────────┘
```

- **UNREGISTERED** — shows raw EPC; associate taps Report to raise a loss-prevention alert
- **DECOMMISSIONED** — shows product name (if resolvable); taps Investigate sends to manager queue
- **RF ARTEFACT** — low-confidence read; taps Dismiss removes from the session silently

#### Web Dashboard — Ghost Tag Panel

**New StatCard on main dashboard:**

```
┌──────────────────────┐
│  Ghost Tags (30d)    │
│        12            │
│  8 Unregistered      │
│  3 Decommissioned    │
│  1 Artefact          │
│  ▲ +3 vs last period │
└──────────────────────┘
```

Color: Red if ghost count is rising; green if declining or zero.

**Ghost Tag Log — table on Cycle Count detail page:**

| EPC (last 8) | Type | Zone | Session | Detected At | Status |
|---|---|---|---|---|---|
| ...3A4F120 | UNREGISTERED | Gondola A-3 | SOH-2026-06-09 | 09:42 | Open |
| ...9B2C008 | DECOMMISSIONED | Fitting Room | Spot Count | 11:15 | Investigating |

**Ghost Rate Trend chart (Recharts LineChart):**

- X-axis: date
- Y-axis: ghost tags per 1,000 EPCs scanned
- Two lines: Unregistered rate + Decommissioned rate
- A rising unregistered rate = new stock arriving untagged or counterfeit activity
- A rising decommissioned rate = EAS/RFID deactivation failures at POS checkout

---

### 3.8 Ghost Tag Classification — Benefits

| Benefit | Detail |
|---|---|
| Inventory accuracy | Removes inflated on-hand counts caused by phantom tags; SOH accuracy KPI improves |
| Loss prevention signal | Unregistered EPCs are the earliest indicator of counterfeit, tag cloning, or unprocessed returns |
| EAS failure detection | Decommissioned EPC still appearing = deactivation failure at checkout; identifies POS lanes needing maintenance |
| Associate confidence | Associates see exactly what was classified and why; override capability maintains trust in the system |
| Compliance | Ghost tag log provides audit trail for stock discrepancy investigations |

---

## 4. Feature 2 — Virtual Shielding

### 4.1 Definition

**Virtual shielding** is a software-defined zone boundary. It classifies EPC reads that physically originate outside the scanned zone — caused by RF bleed-through walls, gondola ends, fitting room curtains, and open stockroom doors — so they are excluded from the zone's inventory count without requiring physical RF shielding materials.

```
      Stockroom
         │
    ─────┼─────  ← door / wall (no physical shielding)
         │
      Sales Floor

Reader near the boundary reads tags from BOTH sides.
Virtual shielding identifies and excludes the stockroom tags
from the sales floor count — and vice versa.
```

### 4.2 Why Physical Shielding Is Not Always Sufficient

| Scenario | Physical shielding | Virtual shielding |
|---|---|---|
| Stockroom door | RF absorber curtain works well (one-time cost) | Software fallback when curtain is open or removed |
| Open-plan floor zones (gondola A vs. B) | Impractical — floor is open | Primary solution |
| Fitting rooms with soft curtains | Partially effective | Primary solution |
| Seasonal fixture rearrangement | Requires re-installation | Auto-adapts via calibration |

Physical and virtual shielding are complementary: use physical shielding at hard boundaries (stockroom door), virtual shielding for soft zone divisions throughout the sales floor.

### 4.3 Scan Context

StoreLense uses handheld-only scanning — there are no fixed readers. Every cycle count is performed by an associate walking through a zone with a Zebra handheld. This means:

- The reader is **moving** through the zone — a key advantage for shielding algorithms
- There is no continuous background read stream between counts
- All shielding logic must be derived from a **single scan session** per zone
- Signal data per EPC must be accumulated **across all reads** in the session, not just the first read

---

### 4.4 Technique 1 — Scan Protocol Discipline

**Type:** Operational / workflow
**Effort:** Zero engineering — training and app workflow design only
**Effectiveness:** Highest single-factor reduction in bleed-through

#### Description

The most impactful "shielding" is a defined associate scanning protocol enforced by the app workflow:

1. Associate selects zone in the app before starting (**Zone-In declaration**)
2. Walks end-to-end through the zone, holding the reader pointed **into** the zone, away from adjacent zone walls
3. Declares scan complete when they reach the far end (**Zone-Out confirmation**)

**Rules:**
- Never scan two adjacent zones back-to-back in the same session
- Always face the reader into the target zone, not toward the stockroom wall or gondola end
- Short scan durations (30–60 seconds) limit cumulative bleed exposure

**App enforcement:**
- Zone must be selected before scan can start (no unnamed scans)
- Scan ends with a deliberate "Finish Zone" action, not automatic timeout
- Warning shown if scan duration is unusually short (< 15 seconds) — possible incomplete coverage

---

### 4.5 Technique 2 — Read Count Density Filtering

**Type:** On-device algorithmic
**Effort:** Low
**Effectiveness:** High — eliminates majority of bleed-through in a single pass

#### Description

As the associate walks through the zone, a physically present tag is read **many times** — the reader passes within 0.5–2 m of the tag repeatedly. A bleed-through tag from an adjacent zone is always at distance; it is read **1–3 times** at most during the session.

**Classification threshold (configurable per zone type):**

| Zone Type | Minimum reads to confirm | Rationale |
|---|---|---|
| Dense apparel rack | 4 | Tags close together; each gets multiple passes |
| Sparse shoe wall | 2 | Fewer items; each gets 1–2 close passes |
| Fitting room | 3 | Small area; associate walks full perimeter |
| High-value watch items | 1 | Never filter — loss prevention overrides shielding |

**Accumulator change required:**
Currently `QuickSpotCountViewModel` deduplicates on first read (`seenEpcs.add()`), discarding subsequent reads. To enable count-based filtering, the accumulator must track raw reads per EPC and score at session end — not deduplicate at ingest time.

---

### 4.6 Technique 3 — RSSI Peak Gate

**Type:** On-device algorithmic
**Effort:** Low
**Effectiveness:** High when combined with Technique 2; unreliable alone

#### Description

A tag physically close to the associate at any point during the scan produces **at least one strong RSSI reading** — even if most reads are attenuated by fabric or shelving. A bleed-through tag that was always behind a wall **never** produces a strong peak read.

**Rule:**

```
Peak RSSI ≥ −65 dBm at any point during scan → in-zone candidate
Peak RSSI  < −65 dBm throughout entire scan  → bleed-through candidate
```

**Combined gate (Techniques 2 + 3):**

```
CONFIRMED:  readCount ≥ threshold  AND  peakRssi ≥ −65 dBm
SUSPECT:    readCount ≥ threshold  BUT  peakRssi  < −65 dBm  (attenuated real tag — review)
BLEED:      readCount  < threshold AND  peakRssi  < −65 dBm  (both signals weak — exclude)
```

The combined gate has significantly fewer false positives than either signal alone. A real tag hidden behind dense wool coats may have weak RSSI but will have a high read count; the combined gate still confirms it.

---

### 4.7 Technique 4 — RSSI Arc Detection

**Type:** On-device algorithmic
**Effort:** Medium (requires per-EPC RSSI time-series storage during scan)
**Effectiveness:** Highest algorithmic accuracy for handheld scanning

#### Description

As the associate walks past a physically present tag, the RSSI follows a characteristic **arc**:

```
weak → stronger → PEAK → stronger → weak
     (approaching)  (closest point)  (walking away)
```

A bleed-through tag from an adjacent zone shows a **flat weak line** — RSSI never rises significantly because the associate never gets close to it.

**Arc detection rule:**

```
rssiRange = max(rssiHistory) − min(rssiHistory)
rssiRange ≥ 15 dBm  → arc detected → in-zone (signal varied as associate passed)
rssiRange < 15 dBm  → flat line    → bleed-through candidate
```

Flat but strong RSSI (e.g., tag directly behind the wall with no distance variation) is caught by Technique 2 (low read count) even if arc detection misses it.

**Memory consideration:** Storing the full RSSI time-series for 200–500 EPCs with 20–30 reads each is approximately 100–200 KB in memory — well within handheld RAM budget.

---

### 4.8 Technique 5 — Boundary Exclusion Pass

**Type:** Scan protocol + on-device algorithmic
**Effort:** Medium (requires UI workflow change)
**Effectiveness:** High for stockroom-door / hard boundary zones specifically

#### Description

A deliberate two-pass scanning protocol designed for zones that share a boundary with a high-emission neighbour (stockroom, back of house):

**Pass 1 — Interior scan:**
Associate scans the interior of the zone facing inward. Normal scan protocol.

**Pass 2 — Boundary walk:**
After the interior scan, associate walks the shared boundary (e.g., along the stockroom wall) holding the reader **pointing away from the interior** — deliberately reading into the adjacent zone.

**Classification:**
Any EPC appearing in Pass 2 with RSSI comparable to its Pass 1 readings = it was readable from both sides of the boundary = bleed-through. Exclude from the zone count.

**When to apply:**
Zone types that warrant Boundary Exclusion Pass:
- Zones sharing a wall with the stockroom
- Zones adjacent to the fitting room entrance
- End-of-aisle fixtures adjacent to the staff area

This technique is not needed for open-plan gondola zones separated only by aisle space.

---

### 4.9 Technique 6 — Exclusion List from Adjacent Zone Scans

**Type:** Backend-assisted on-device filtering
**Effort:** Medium (requires backend API + zone adjacency map)
**Effectiveness:** High — catches repeating bleed patterns between known neighbours

#### Description

Before the associate starts scanning Zone A, the app fetches the EPC list from the most recent scan of all zones adjacent to Zone A (defined by the store zone adjacency map). During classification, any EPC that:
- Appears in an adjacent zone's recent scan list
- Has low read count in the current Zone A scan
- Has weak peak RSSI in the current Zone A scan

…is classified as `BLEED_FROM_NEIGHBOUR` and named with its source zone.

**API required:**
```
GET /api/v1/stores/{storeId}/zones/{zoneId}/neighbours/epcs
  ?maxAgeHours=4
Response: { zoneId, zoneName, epcs: [epc1, epc2, ...] }[]
```

**Zone adjacency map:**
A one-time store configuration: which zones share physical boundaries. Defined by the Store Manager in the web portal during store setup. Stored as an adjacency list per store.

**User-facing result:**
```
⚠ 4 tags possibly from Stockroom (matched last stockroom scan)
  → [View tags]
```

---

### 4.10 Technique 7 — Backend Cross-Zone Arbitration

**Type:** Server-side post-upload
**Effort:** Medium (backend reconciliation job)
**Effectiveness:** High — resolves multi-zone conflicts definitively; no mobile change needed

#### Description

When scan sessions from multiple zones on the same day are uploaded, the backend runs a cross-zone arbitration pass:

```
EPC X appears in:
  Zone A scan → readCount=42, avgRssi=−54 dBm
  Zone B scan → readCount=2,  avgRssi=−81 dBm

→ Assign EPC X to Zone A (stronger signal, higher count)
→ Flag Zone B read of EPC X as BLEED_FROM_ZONE_A
→ Remove EPC X from Zone B's confirmed count
→ Return zoneConflicts list in session result for Zone B
```

The arbitration logic:

```
For each EPC appearing in > 1 zone scan on the same day:
  winner  = zone with highest (readCount × 0.6 + normalisedRssi × 0.4) score
  losers  = all other zone scans containing this EPC
  action  = reassign EPC to winner; tag loser reads as BLEED_RESOLVED
```

This runs as a background job triggered after a configurable window (e.g., 30 minutes after a store's last zone scan upload of the day) to allow all zone scans to arrive before arbitration.

---

### 4.11 Technique 8 — Temporal Zone Consistency (Previous Cycle as Baseline)

**Type:** Server-side, post-upload
**Effort:** Low backend change (uses existing session history)
**Effectiveness:** Medium — catches repeating bleed patterns across multiple cycle count days

#### Description

The backend knows which EPCs were confirmed in each zone from the previous cycle count. If an EPC that was confirmed in Zone B (stockroom) in the last scan now appears in Zone A (sales floor) with low read count and weak RSSI, it is classified as a cross-read — not a legitimate zone transfer.

**Classification rule:**

```
EPC last confirmed in: Stockroom (confidence HIGH, 3 days ago)
EPC appears in today's:  Sales Floor scan (readCount=1, peakRssi=−83 dBm)

→ Classify as TEMPORAL_BLEED
→ Do NOT trigger a zone-transfer event (would be false)
→ Flag for associate review on next sales floor scan
```

**Genuine zone transfer signature** (how it differs from bleed):
- EPC disappears from Stockroom scan on the same day
- Appears in Sales Floor scan with high read count and strong RSSI
- Corresponds to a replenishment task completion event (optional corroboration)

---

### 4.12 Virtual Shielding — Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│  Pre-Scan (App Startup for Zone Scan)                               │
│                                                                     │
│  QuickSpotCountViewModel.setZone(zoneId)                            │
│    → fetch adjacentZoneEpcs from backend (Technique 6)              │
│    → cache locally as exclusionSet                                  │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│  During Scan (Handheld Walking Zone)                                │
│                                                                     │
│  RfidReader.reads → raw EpcRead stream                              │
│       ↓                                                             │
│  ReadAccumulator per EPC:                                           │
│    { readCount++, updatePeakRssi, appendRssiHistory, addAntPort }   │
│  (raw reads kept — dedup only at classification, not at ingest)     │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│  Post-Scan Classification (on finishCount / Pause+Review)           │
│                                                                     │
│  VirtualShieldClassifier.classify(accumulator, exclusionSet)        │
│                                                                     │
│  Layer 1 — Read Count Gate (Technique 2)                            │
│    readCount ≥ zoneThreshold? → pass | suspect                      │
│                                                                     │
│  Layer 2 — RSSI Peak Gate (Technique 3)                             │
│    peakRssi ≥ rssiFloor? → raise confidence | lower confidence      │
│                                                                     │
│  Layer 3 — RSSI Arc Detection (Technique 4)                         │
│    rssiRange ≥ 15 dBm? → arc detected → in-zone                     │
│                                                                     │
│  Layer 4 — Exclusion Set Check (Technique 6)                        │
│    epc in adjacentZoneEpcs? + low confidence → BLEED_FROM_NEIGHBOUR │
│                                                                     │
│  Output per EPC:                                                    │
│    CONFIRMED | SUSPECT | BLEED_FROM_NEIGHBOUR | BLEED_UNKNOWN       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│  Associate Review Screen                                            │
│                                                                     │
│  CONFIRMED    → displayed in main count total                       │
│  SUSPECT      → "Needs Review" list → Confirm / Exclude             │
│  BLEED_*      → "Shielded" section → Dismiss / Override             │
│                                                                     │
│  Associate actions logged to local audit trail                      │
└────────────────────────────┬────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────────┐
│  Upload (RfidBatchRequest)                                          │
│                                                                     │
│  Each read includes: epc, rssi, antennaPort, readCount              │
│  Session metadata includes: zoneId, shieldedCount, suspectCount     │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTPS POST
┌────────────────────────────▼────────────────────────────────────────┐
│  Backend — Post-Upload Processing                                   │
│                                                                     │
│  VirtualShieldArbitrationJob (Technique 7)                          │
│    → runs 30 min after last zone upload of the day                  │
│    → cross-zone comparison; reassigns contested EPCs                │
│    → updates session results with zoneConflicts list                │
│                                                                     │
│  TemporalConsistencyService (Technique 8)                           │
│    → compares current scan against previous cycle count             │
│    → flags EPCs that contradict zone history without a transfer     │
│                                                                     │
│  ReportingService                                                   │
│    → bleedThroughRatePct, ghostRatePct → daily KPI row              │
│    → zone-level bleed rate → zone_stats table                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 4.13 Virtual Shielding — Dashboard Design

#### Android — Scan Result Screen

```
┌────────────────────────────────────────────────┐
│  Zone: Gondola A-3          Scan: 45 sec        │
│                                                 │
│  ████████████████░░░░░░░▒▒▒                     │
│  68 Confirmed  12 Review  4 Shielded            │
│                                                 │
│  ⚠ 4 tags from Stockroom (matched last scan)   │
│                                                 │
│  ▼ Needs Review (12)                            │
│  ────────────────────────────────────────────── │
│  Nike Air Max 90 / Size 9   1 read · -81 dBm   │
│                         [Confirm]  [Exclude]    │
│  Levi's 501 / 32x32         2 reads · -76 dBm  │
│                         [Confirm]  [Exclude]    │
│                                                 │
│  ▼ Shielded (4)                                 │
│  ────────────────────────────────────────────── │
│  EPC ...3A4F  from Stockroom  [Override]        │
│  EPC ...9B2C  from Stockroom  [Override]        │
└────────────────────────────────────────────────┘
```

Color coding:
- Green bar segment = CONFIRMED
- Amber bar segment = NEEDS REVIEW
- Gray bar segment = SHIELDED

#### Web — Cycle Count Detail Page

**Extended stat grid** (adds to existing 4-column grid):

| Total Reads | Unique EPCs | Confirmed | Shielded | Review Resolved |
|---|---|---|---|---|
| 842 | 184 | 168 | 4 | 10 / 12 |

**Confidence breakdown stacked bar (per zone):**

```
Gondola A-3  ████████████████░░░░░░▒▒  168 | 12 | 4
Gondola B-1  ██████████████████░░░░░   174 | 8  | 2
Stockroom    █████████████████████░░   189 | 3  | 0
              ■ Confirmed  □ Review  ▒ Shielded
```

**Zone Conflict Table** (appears when `zoneConflicts` returned by backend):

| EPC (last 8) | Claimed by | Conflict zone | Read count | Resolution |
|---|---|---|---|---|
| ...3A4F120 | Gondola A-3 (42 reads) | Stockroom (1 read) | — | Assigned to A-3 |
| ...9B2C008 | Stockroom (3 reads) | Gondola B-1 (8 reads) | — | Needs review |

**RSSI Distribution Histogram** (collapsed by default, "RF Signal Analysis" toggle):

Shows per-session RSSI histogram with a bimodal distribution when shielding is working (strong cluster = in-zone, weak cluster = bleed). A continuous flat spread indicates poor protocol discipline.

#### Web — Main Dashboard

**New StatCard — Bleed-Through Rate:**

```
┌──────────────────────┐
│  Bleed-Through Rate  │
│       4.2%           │
│  ↓ 1.1% vs last week │
│  color: green < 5%   │
│         amber 5–8%   │
│         red   > 8%   │
└──────────────────────┘
```

**Bleed Rate by Zone — Bar Chart (Recharts):**

X-axis: Zone names | Y-axis: Bleed rate %
Zones near stockroom/fitting room are visually obvious as high bars.
Guides managers on where to invest in physical shielding or protocol training.

**Shielding Effectiveness Trend — second line on Inventory Accuracy chart:**

- Primary line: Inventory Accuracy %
- Secondary line: Bleed-through rate % (inverted / right axis)
- Demonstrates to the Retail Operations Director that improving shielding quality directly drives the accuracy KPI

---

### 4.14 Virtual Shielding — Benefits

| Benefit | Detail |
|---|---|
| Zone count accuracy | Removes bleed-through EPCs from zone totals; replenishment tasks triggered only on real shortages |
| Fewer false replenishment tasks | Items counted in the wrong zone no longer generate unnecessary pick tasks |
| Floor vs. backroom accuracy | Critical for multi-zone stores where replenishment decisions depend on knowing which zone holds the item |
| Associate confidence | Explicit review step means associates understand what was excluded and can override; builds trust in system output |
| Identifies chronic problem zones | Bleed-rate-by-zone chart shows exactly which zones need physical shielding investment |
| No hardware investment required | All techniques run on the existing Zebra handheld; no fixed readers, no RF curtains mandatory |
| Foundation for zone transition tracking | Temporal consistency data (Technique 8) is the data engine for the Zone Transition Tracking feature (Feature 10 in the product roadmap) |

---

## 5. Combined Data Model

New database fields and tables required for both features.

### 5.1 New Columns on Existing Tables

**`rfid_reads` (add):**

| Column | Type | Purpose |
|---|---|---|
| `read_count` | INTEGER | How many times this EPC was read in the session |
| `peak_rssi` | DECIMAL | Strongest RSSI reading for this EPC in the session |
| `rssi_variance` | DECIMAL | Standard deviation of RSSI readings (arc detection) |
| `antenna_ports` | TEXT | JSON array of distinct antenna port numbers seen |
| `shield_classification` | VARCHAR | CONFIRMED / SUSPECT / BLEED_FROM_NEIGHBOUR / BLEED_UNKNOWN / GHOST_UNREGISTERED / GHOST_DECOMMISSIONED / GHOST_RF_ARTEFACT |
| `confidence_score` | DECIMAL | 0.0–1.0 composite score |
| `associate_override` | BOOLEAN | True if associate manually confirmed or excluded |

**`soh_sessions` / `spot_count_sessions` (add):**

| Column | Type | Purpose |
|---|---|---|
| `confirmed_epc_count` | INTEGER | EPCs classified as high-confidence in-zone |
| `suspect_epc_count` | INTEGER | EPCs sent to associate review |
| `shielded_epc_count` | INTEGER | EPCs excluded as bleed-through |
| `ghost_unregistered_count` | INTEGER | EPCs with no product master match |
| `ghost_decommissioned_count` | INTEGER | EPCs with sold/voided status |
| `zone_id` | VARCHAR | Zone this session was scanned in (required for shielding) |

**`daily_kpi` (add):**

| Column | Type | Purpose |
|---|---|---|
| `bleed_through_rate_pct` | DECIMAL | Shielded / total reads × 100 |
| `ghost_rate_pct` | DECIMAL | Ghost EPCs / total reads × 100 |
| `avg_confidence_score` | DECIMAL | Mean confidence across all reads |

### 5.2 New Tables

**`zone_adjacency`**

| Column | Type |
|---|---|
| `store_id` | VARCHAR FK |
| `zone_id` | VARCHAR FK |
| `adjacent_zone_id` | VARCHAR FK |
| `boundary_type` | VARCHAR (wall / curtain / open_aisle / door) |
| `shielding_priority` | INTEGER (1=high bleed risk, 3=low) |

**`ghost_tag_alerts`**

| Column | Type |
|---|---|
| `id` | UUID PK |
| `store_id` | VARCHAR FK |
| `epc` | VARCHAR |
| `ghost_type` | VARCHAR (UNREGISTERED / DECOMMISSIONED / RF_ARTEFACT) |
| `zone_id` | VARCHAR |
| `session_id` | VARCHAR FK |
| `detected_at` | TIMESTAMP |
| `status` | VARCHAR (open / investigating / resolved / dismissed) |
| `resolved_by` | VARCHAR (user id) |
| `resolved_at` | TIMESTAMP |

**`zone_epc_history`** (used by Technique 6, 7, 8)

| Column | Type |
|---|---|
| `store_id` | VARCHAR FK |
| `epc` | VARCHAR |
| `zone_id` | VARCHAR |
| `confirmed_count` | INTEGER |
| `avg_rssi` | DECIMAL |
| `last_seen_at` | TIMESTAMP |
| `last_session_id` | VARCHAR |

**`zone_stats`** (per-zone per-session metrics)

| Column | Type |
|---|---|
| `session_id` | VARCHAR FK |
| `zone_id` | VARCHAR FK |
| `total_reads` | INTEGER |
| `confirmed_reads` | INTEGER |
| `shielded_reads` | INTEGER |
| `ghost_reads` | INTEGER |
| `bleed_rate_pct` | DECIMAL |
| `confidence_avg` | DECIMAL |

---

## 6. Integration Touchpoints with Existing System

### 6.1 Android App

| Component | Change |
|---|---|
| `EpcRead` | No struct change — `rssi`, `antennaPort`, `readAt` already present |
| `QuickSpotCountViewModel` | Accumulator replaces `seenEpcs` dedup set; classification at `finishCount` |
| `ScanViewModel` (SOH) | Same accumulator pattern; classification before `uploadBatch` |
| `SpotCountItem` | Add `readCount`, `confidence`, `classification`, `ghostType` fields |
| `RfidBatchRequest` / `RfidReadDto` | Add `readCount` field |
| New: `VirtualShieldClassifier` | Stateless utility — applies Layers 1–4 to an accumulator |
| New: `GhostTagClassifier` | Stateless utility — cross-checks against product cache + classification rules |
| New: Pre-scan API call | Fetch adjacent zone EPC exclusion list before scan starts |

### 6.2 Backend

| Service | Change |
|---|---|
| `TagIngestService` | Persist `read_count`, `peak_rssi` from incoming `RfidReadDto` |
| `GhostTagClassificationService` | New — runs Approach 3 (expected-set cross-check) post-upload |
| `VirtualShieldArbitrationJob` | New — runs cross-zone arbitration 30 min after last upload of the day |
| `TemporalConsistencyService` | New — compares current scan against `zone_epc_history` |
| `SohCalculatorService` | Use only CONFIRMED + associate-confirmed EPCs in SOH result calculation |
| `ReportingService` | Add `bleed_through_rate_pct`, `ghost_rate_pct` to daily KPI aggregation |
| New API: `GET /zones/{id}/neighbours/epcs` | Returns adjacent zone EPC exclusion list for pre-scan fetch |
| Zone setup UI (web portal) | Zone adjacency map configuration for Store Manager |

### 6.3 No Change Required

| Component | Reason |
|---|---|
| `RfidReader` interface | `rssi`, `antennaPort` already emitted per read |
| `EmDkRfidReader` | No hardware change |
| `InboundRepository` | Ghost classification not applied to inbound receiving |
| `ReplenishRepository` | Replenishment tasks consume confirmed SOH output; no change |
| Authentication / JWT | No new access control requirements |
| ERP integration | SOH push already uses session result; gains cleaner input |

---

## 7. Non-Functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-GV-01 | Classification must complete on-device within 2 seconds after scan finish for up to 500 unique EPCs | Measured on Zebra TC57 / TC72 |
| NFR-GV-02 | Pre-scan adjacent zone EPC fetch must complete within 1 second on store Wi-Fi | Cached on device for 4 hours to tolerate intermittent connectivity |
| NFR-GV-03 | Backend cross-zone arbitration job must complete within 5 minutes for a full-store day of scans (up to 50 zone sessions) | Batch job SLA |
| NFR-GV-04 | Ghost tag alert must appear on the web dashboard within 30 seconds of session upload | Consistent with existing SOH result latency |
| NFR-GV-05 | All associate override actions must be captured in the audit log with user, EPC, action, and timestamp | Compliance with BR-06 |
| NFR-GV-06 | Classification thresholds (read count floor, RSSI floor) must be configurable per store via the web portal without a code deployment | Store Manager self-service |
| NFR-GV-07 | The `zone_epc_history` table must be partitioned by store and have a retention policy of 90 days to prevent unbounded growth | Infrastructure constraint |

---

## 8. Phased Delivery Recommendation

### Phase 1 — Foundation (Low effort, immediate accuracy gain)

- Implement on-device accumulator (replace `seenEpcs` dedup with read-count tracking)
- Apply Techniques 2 + 3 (read count + RSSI peak gate)
- Apply Approach 1 ghost classification (confidence score)
- Show three-segment confidence bar on Android result screen
- Add `read_count` to `RfidBatchRequest`

*Outcome: Associates immediately see shielded and ghost tags per scan. Bleed-through rate KPI begins populating.*

### Phase 2 — Backend Intelligence (Medium effort)

- Implement `GhostTagClassificationService` (Approach 3, server-side expected-set cross-check)
- Implement `zone_adjacency` table and Store Manager configuration UI
- Implement adjacent zone EPC prefetch API (Technique 6)
- Populate `ghost_tag_alerts` table; surface ghost tag panel on web dashboard
- Add bleed-through rate and ghost rate to daily KPI

*Outcome: Ghost tag alerts visible to loss prevention. Zone conflicts resolved server-side. Dashboard KPIs live.*

### Phase 3 — Arbitration and Trend (Medium effort)

- Implement `VirtualShieldArbitrationJob` (Technique 7)
- Implement `TemporalConsistencyService` (Technique 8)
- Add RSSI arc detection (Technique 4) on-device
- Add bleed-rate-by-zone bar chart and shielding effectiveness trend line to dashboard
- Zone Conflict Table on cycle count detail page

*Outcome: Cross-zone conflicts resolved automatically. Store managers can identify chronic problem zones. Foundation for Zone Transition Tracking feature is operational.*

### Phase 4 — Protocol and Calibration (Operational)

- Implement Boundary Exclusion Pass workflow (Technique 5) in Android app for flagged high-risk zones
- Implement Zone Calibration Profile (Technique in reserve) for stores with persistent bleed despite Phases 1–3
- Associate training materials and in-app scan protocol guidance

*Outcome: Residual bleed in problem zones addressed. Calibration profiles provide highest accuracy for priority stores.*
