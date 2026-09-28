# gate-guard

Standalone exit-portal monitor for the Zebra FX9600 at **10.1.2.16**.

Subscribes to the reader's tag stream over MQTT, resolves every EPC against the
EANs on the store's pending bills, and drives a GPO port high to sound the
buzzer when something leaves that nobody paid for.

It is independent of the Spring backend — it only consumes the public REST API.

---

## How it decides

```
pending bills (last N hours)                      MQTT tag stream
  GET /api/gate/checks/bills?pendingOnly=true            │
        │                                                │
        ▼  per bill                                      ▼
  GET /api/gate/checks/bills/{billRef}            RSSI filter, debounce
        │  items[] { ean, qty, isRfidEnabled }           │
        ▼  per RFID-enabled EAN                          ▼
  GET /api/inventory/epc-by-ean/{ean}  ───▶  ALLOWLIST ──▶ on the list,
        │  in-store EPCs                     + per-EAN     quota left?
        │                                      quota          │
        │                                                ┌────┴────┐
        │                                              yes│       │no
        │                                            silent│       ▼
        └── GET /api/inventory/identify-epc/{epc} ◀────────┴──  GPO HIGH
            (after the buzzer, for the log line)                 2s
```

Four loops, one shared state:

| Thread | Job |
|---|---|
| `bill-sync` | Rebuild the allowlist snapshot every ~4s, publish by atomic swap |
| MQTT callback | Decide, in memory, with no network call on the path |
| `enrich` | Resolve alarmed EPCs to product names *after* the buzzer fires |
| `report` | Batch alarms into `POST /api/gate/checks` every 30s |

The hot path never makes a network call. A portal fires dozens of reads per
second; one blocking HTTP request in the callback backs up the broker queue and
the buzzer sounds after the customer has left.

---

## Install

```bash
cd gate-guard
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp config.yaml.example config.yaml
```

Or with Docker:

```bash
mkdir -p config logs && cp config.yaml.example config/config.yaml
export GATE_GUARD_API_PASSWORD='...'
docker compose up -d --build
```

---

## Configure — in this order

Do these five steps in sequence. Each one has a command that proves it works
before you move on.

### 1. API account

`GET /api/gate/checks/bills` is restricted to **ADMIN** or **STORE_MANAGER**
([`GateCheckController.java:80`](../backend/inventory-service/src/main/java/com/storelense/inventory/controller/GateCheckController.java#L80)).
A `SECURITY_GUARD` account gets 403. Create a dedicated service user:

```yaml
api:
  base_url: "http://150.241.244.61:8080"
  username: "gate-guard-svc"
  password: "..."           # or env GG_API_PASSWORD
  store_id: "e26b776e-b06a-4633-98aa-5b20303f790c"
```

Prove it:

```bash
python -m gate_guard check
```

This logs in, prints the role it got, builds one real allowlist, and tells you
how many bills and EPCs it found.

### 2. MQTT topics — you supply these

```yaml
mqtt:
  host: "10.1.2.50"
  port: 1883
  tag_topic: "..."        # where the FX9600 publishes tag reads
  control_topic: "..."    # where it accepts GPO commands
```

### 3. Pin the tag payload shape

This is the step that most often silently breaks a gate. Zebra's IoT Connector
emits different JSON depending on the configured output format, so the parser
tries a list of dotted paths rather than assuming one. See what your reader
actually sends:

```bash
python -m gate_guard tap --seconds 30      # now wave a tag at the portal
```

It prints the raw payload and what it managed to extract. If it says
`parsed -> NOTHING`, copy the real path into `tag_fields.epc` and re-run:

```yaml
tag_fields:
  epc:  ["data.idHex"]      # delete the guesses, keep what works
  rssi: ["data.peakRssi"]
```

### 4. GPO driver

You already drive the relay from the reader's web UI, so the wiring is proven —
this is only about issuing the same command programmatically.

```yaml
gpo:
  driver: "mqtt"       # recommended: reuses the connection tags arrive on
  port: 1              # which of the four GPO ports feeds the relay
  reader_ip: "10.1.2.16"
  mqtt_on_payload:  '{{"command":"set_gpo","payload":{{"pin":{port},"state":true}}}}'
  mqtt_off_payload: '{{"command":"set_gpo","payload":{{"pin":{port},"state":false}}}}'
```

`{port}` and `{state}` are substituted; braces are doubled because the template
goes through Python's `str.format`. **Match the payload to whatever command
your reader's web UI issues** — capture it from the browser's network tab or
your broker, then verify by ear:

```bash
python -m gate_guard gpo-test --cycles 3
```

Three audible pulses means you are done. If not, the template is wrong — the
hardware is not in question at this point.

Two fallbacks if the MQTT control path does not exist on your firmware:

- `driver: llrp` — raw `SET_READER_CONFIG` with a `GPOWriteData` parameter over
  TCP/5084. Firmware-independent. **Caveat:** LLRP permits a single client
  connection, so if IoT Connector owns the radio this port may be unavailable.
- `driver: rest` — IoT Connector's local REST API. Paths vary by firmware
  version, so `rest_login_path` / `rest_gpo_path` are configurable. Check the
  API docs your reader serves at `https://10.1.2.16` and set them to match.

`driver: null` logs instead of buzzing — useful for bench-testing the matching
logic.

### 5. Tune the portal

```yaml
tuning:
  rssi_min: -60          # raise toward -50 if it picks up the sales floor
  debounce_s: 5          # stops a continuous alarm while someone stands there
  alarm_hold_s: 2
  bill_window_hours: 3
  on_stale: "suppress"
```

Run with `logging.log_drops: true` for an hour and look at the `DROPPED_RSSI`
lines in `events.jsonl` to pick a real threshold for your portal geometry.

---

## Run

```bash
python -m gate_guard run
```

Production, as a service:

```bash
sudo cp systemd/gate-guard.service /etc/systemd/system/
sudo mkdir -p /etc/gate-guard && sudo cp config.yaml /etc/gate-guard/
sudo systemctl enable --now gate-guard
journalctl -u gate-guard -f
```

---

## Logs

**`logs/events.jsonl`** — one JSON object per decision. The forensic record:

```json
{"ts":"2026-09-11T14:22:03.412Z","event":"UNKNOWN_EPC","epc":"E2003411...",
 "ean":null,"rssi":-52,"antenna":2,"alarmed":true,
 "allowlist_size":184,"snapshot_age_ms":2100,"snapshot_ok":true}
{"ts":"2026-09-11T14:22:03.870Z","event":"ALARM_DETAIL","epc":"E2003411...",
 "product":"Blue Shirt M","status_in_store":"sold","likely_false_alarm":true}
```

`ALLOWED` decisions are logged too. When someone reports "it beeped at a paying
customer," the allowed events around that timestamp plus `snapshot_age_ms` tell
you whether the allowlist was stale or the EPC was genuinely missing.

Event types: `ALLOWED`, `ALLOWED_REENTRY`, `QUOTA_EXCEEDED`, `UNKNOWN_EPC`,
`SUPPRESSED_STALE`, `DROPPED_RSSI`, `DROPPED_DEBOUNCE`, `GPO`, `GPO_ERROR`,
`SNAPSHOT`, `SNAPSHOT_FAILED`, `ALARM_DETAIL`, `REPORTED`, `HEARTBEAT`.

**`logs/gate-guard.log`** — operational: syncs, reconnects, token refreshes,
GPO failures. Separate file so a flood of tag events cannot bury a broker
disconnect.

---

## Design decisions worth knowing

**Quota, not just membership.** `epc-by-ean` returns *every* in-store EPC of a
product, so a bill for one shirt would otherwise unlock the whole rack. Each
EAN carries a remaining-exits counter alongside the set.

**The exit ledger.** Quota decrements live in the snapshot, which is rebuilt
every few seconds — so decrements alone would be forgotten and the same
purchase could pass twice. Exits are recorded in `logs/exit-ledger.json`
(TTL 3h), subtracted from quota at build time, and treated as silently allowed
so a customer stepping back through the portal does not set off the alarm.

**Time-bounded bills.** `pendingOnly=true` keys off `gate_checked_at IS NULL`
([`BillService.java:120`](../backend/inventory-service/src/main/java/com/storelense/inventory/service/BillService.java#L120)).
Nothing in an unattended-portal flow ever sets that column, so without
`bill_window_hours` the pending list grows forever and eventually allowlists
half the store.

**`billRef` is null on audit posts.** A portal cannot know which bill a tag
belongs to, and a non-null `billRef` makes the backend stamp `gate_checked_at`
on that bill ([`GateCheckService.java:62`](../backend/inventory-service/src/main/java/com/storelense/inventory/service/GateCheckService.java#L62)),
dropping it out of `pendingOnly` while the customer is still walking to the
door.

**Stale snapshots suppress by default.** If bill sync fails, the last good
snapshot keeps serving and its age is logged on every decision. Past
`stale_snapshot_s` the default is to go quiet and shout in the ops log, because
a gate that cries wolf gets unplugged by staff within a week and then you have
no gate at all. Set `on_stale: alarm` if your risk calculus differs.

**One alarm timer, never one per tag.** Overlapping on/off pairs are the
classic way to leave a GPO stuck high. Every alarm extends a single shared
deadline. The controller also sends GPO-low at startup (clearing a stuck state
from a previous crash) and on shutdown.

---

## The sold-status race — read this before go-live

`epc-by-ean` only returns EPCs with `status = 'in_store'`
([`InventoryService.java:265`](../backend/inventory-service/src/main/java/com/storelense/inventory/service/InventoryService.java#L265)).
If POS calls `/api/inventory/epc/sold` at checkout, those EPCs leave
`in_store`, vanish from the allowlist, and **the paying customer alarms.**

The guard against it is already in the code: alarmed EPCs are enriched
asynchronously and any whose `statusInStore` is not `in_store` are logged with
`likely_false_alarm: true`. Watch that field for the first week. If it fires
often, your POS is marking sold before the gate, and you should either move
that call after the portal or extend the decision to treat non-`in_store`
registered tags as silent.

---

## Verify

```bash
python -m pytest tests/ -q       # 15 tests, no broker or backend needed
```

Covers payload shapes (IOTC simple, flat, batched envelope, JSON array,
NDJSON, heartbeats), the LLRP `SET_READER_CONFIG` byte encoding, and the full
decision table including quota exhaustion, ledger persistence across rebuilds,
debounce, and stale-snapshot behaviour.

```bash
python -m gate_guard explain E2003411B802011383257C3D
```

Resolves one EPC against a freshly built allowlist and says whether it would
alarm and why. No buzzer, no side effects — the tool to reach for when a guard
says "it beeped at this item."
