"""Offline tests for the parts that must not be wrong: payload parsing, the
LLRP GPO encoding, and the decision table. No broker or backend required.

    python -m pytest tests/ -q
"""

from __future__ import annotations

import json
import struct
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gate_guard.allowlist import ExitLedger, Snapshot          # noqa: E402
from gate_guard.config import TagFieldsConfig                   # noqa: E402
from gate_guard.decider import (                                # noqa: E402
    ALLOWED, ALLOWED_REENTRY, DROPPED_DEBOUNCE, DROPPED_RSSI,
    QUOTA_EXCEEDED, SUPPRESSED_STALE, UNKNOWN_EPC, Decider,
)
from gate_guard.gpo import build_set_gpo_message                # noqa: E402
from gate_guard.tagparse import extract_reads, normalise_epc    # noqa: E402

FIELDS = TagFieldsConfig()


# --------------------------------------------------------------------------- #
# EPC normalisation
# --------------------------------------------------------------------------- #

def test_normalise_epc():
    assert normalise_epc("e280 1160 6000") == "E28011606000"
    assert normalise_epc("E2:80:11") == "E28011"
    assert normalise_epc("0xE28011") == "E28011"
    assert normalise_epc("") is None
    assert normalise_epc("not-hex-zz") is None
    assert normalise_epc(None) is None


# --------------------------------------------------------------------------- #
# Payload shapes
# --------------------------------------------------------------------------- #

def test_iotc_simple_shape():
    payload = json.dumps({
        "data": {"idHex": "E2003411B802011383257C3D", "peakRssi": -47, "antenna": 2},
        "timestamp": "2026-09-11T10:00:00.000Z",
        "type": "SIMPLE",
        "reader_name": "FX9600F0B1E4",
    })
    reads = extract_reads(payload, FIELDS)
    assert len(reads) == 1
    assert reads[0].epc == "E2003411B802011383257C3D"
    assert reads[0].rssi == -47
    assert reads[0].antenna == 2


def test_flat_shape():
    reads = extract_reads(json.dumps({"epc": "aabb", "rssi": -55}), FIELDS)
    assert reads[0].epc == "AABB"
    assert reads[0].rssi == -55


def test_batched_envelope():
    payload = json.dumps({
        "reader": "FX9600",
        "tags": [
            {"idHex": "AA01", "peakRssi": -40},
            {"idHex": "AA02", "peakRssi": -70},
        ],
    })
    reads = extract_reads(payload, FIELDS)
    assert [r.epc for r in reads] == ["AA01", "AA02"]


def test_json_array_and_ndjson():
    assert len(extract_reads('[{"epc":"AA"},{"epc":"BB"}]', FIELDS)) == 2
    assert len(extract_reads('{"epc":"AA"}\n{"epc":"BB"}', FIELDS)) == 2


def test_non_tag_messages_are_ignored():
    # Heartbeats, GPI events and garbage must yield nothing, not raise.
    assert extract_reads('{"type":"HEARTBEAT","status":"ok"}', FIELDS) == []
    assert extract_reads("not json at all", FIELDS) == []
    assert extract_reads(b"", FIELDS) == []


# --------------------------------------------------------------------------- #
# LLRP encoding
# --------------------------------------------------------------------------- #

def test_llrp_set_gpo_message():
    msg = build_set_gpo_message(port=1, state=True, msg_id=7)
    ver_type, length, msg_id = struct.unpack("!HII", msg[:10])
    assert ver_type >> 10 & 0x07 == 1          # LLRP version 1
    assert ver_type & 0x03FF == 3              # SET_READER_CONFIG
    assert length == len(msg)                  # length includes the header
    assert msg_id == 7
    assert msg[10] == 0x00                     # ResetToFactoryDefault = false

    param_type, param_len = struct.unpack("!HH", msg[11:15])
    assert param_type == 220                   # GPOWriteData
    assert param_len == 7
    port, data = struct.unpack("!HB", msg[15:18])
    assert port == 1
    assert data == 0x80                        # GPOData = true

    off = build_set_gpo_message(port=3, state=False, msg_id=8)
    port, data = struct.unpack("!HB", off[15:18])
    assert (port, data) == (3, 0x00)


# --------------------------------------------------------------------------- #
# Decision table
# --------------------------------------------------------------------------- #

class _FakeAlarm:
    def __init__(self):
        self.triggers = []

    def trigger(self, reason=""):
        self.triggers.append(reason)


class _FakeEvents:
    def __init__(self):
        self.records = []

    def write(self, event, **payload):
        self.records.append((event, payload))


class _FakeAllowlist:
    def __init__(self, snapshot):
        self.snapshot = snapshot


class _Cfg:
    class tuning:
        rssi_min = -60
        debounce_s = 5.0
        stale_snapshot_s = 300.0
        on_stale = "suppress"

    class logging:
        log_allowed = True
        log_drops = True


def _snapshot(epc_to_ean, quota, ok=True, age_s=0.0):
    return Snapshot(
        built_at=time.time() - age_s,
        allowed_epcs=frozenset(epc_to_ean),
        epc_to_ean=dict(epc_to_ean),
        quota=dict(quota),
        bill_count=1,
        ean_count=len(quota),
        ok=ok,
    )


def _decider(snapshot, ledger=None):
    alarm = _FakeAlarm()
    events = _FakeEvents()
    ledger = ledger or ExitLedger(3600)
    d = Decider(_FakeAllowlist(snapshot), ledger, alarm, _Cfg, events)
    return d, alarm, ledger


def _read(epc, rssi=-45, antenna=1):
    from gate_guard.tagparse import TagRead
    return TagRead(epc=epc, rssi=rssi, antenna=antenna, raw={})


def test_paid_item_is_silent():
    d, alarm, _ = _decider(_snapshot({"AA": "890"}, {"890": 1}))
    assert d.handle(_read("AA")) == ALLOWED
    assert alarm.triggers == []


def test_unbilled_epc_alarms():
    d, alarm, _ = _decider(_snapshot({"AA": "890"}, {"890": 1}))
    assert d.handle(_read("ZZ")) == UNKNOWN_EPC
    assert alarm.triggers == [UNKNOWN_EPC]


def test_quota_stops_the_whole_rack_walking_out():
    # One shirt paid for; epc-by-ean legitimately returns three in-store EPCs
    # of that product. Only the first may pass.
    snap = _snapshot({"AA": "890", "BB": "890", "CC": "890"}, {"890": 1})
    d, alarm, _ = _decider(snap)
    assert d.handle(_read("AA")) == ALLOWED
    assert d.handle(_read("BB")) == QUOTA_EXCEEDED
    assert alarm.triggers == [QUOTA_EXCEEDED]


def test_exit_is_remembered_across_a_rebuild():
    ledger = ExitLedger(3600)
    snap = _snapshot({"AA": "890"}, {"890": 1})
    d, _, _ = _decider(snap, ledger)
    assert d.handle(_read("AA")) == ALLOWED

    # Rebuild: quota resets, but the ledger has the exit on record.
    counts, exited = ledger.snapshot_counts()
    assert counts == {"890": 1}
    assert exited == {"AA"}
    rebuilt = Snapshot(
        built_at=time.time(), allowed_epcs=frozenset({"AA"}),
        epc_to_ean={"AA": "890"}, quota={"890": max(0, 1 - counts["890"])},
        bill_count=1, ean_count=1, ok=True,
    )
    d2, alarm2, _ = _decider(rebuilt, ledger)
    # Walking back in must not alarm and must not spend quota again.
    assert d2.handle(_read("AA")) == ALLOWED_REENTRY
    assert alarm2.triggers == []


def test_weak_read_is_dropped():
    d, alarm, _ = _decider(_snapshot({}, {}))
    assert d.handle(_read("ZZ", rssi=-85)) == DROPPED_RSSI
    assert alarm.triggers == []


def test_debounce_stops_continuous_alarm():
    d, alarm, _ = _decider(_snapshot({}, {}))
    assert d.handle(_read("ZZ")) == UNKNOWN_EPC
    assert d.handle(_read("ZZ")) == DROPPED_DEBOUNCE
    assert len(alarm.triggers) == 1


def test_stale_snapshot_suppresses_rather_than_false_alarms():
    stale = _snapshot({"AA": "890"}, {"890": 1}, ok=False, age_s=600)
    d, alarm, _ = _decider(stale)
    assert d.handle(_read("ZZ")) == SUPPRESSED_STALE
    assert alarm.triggers == []


def test_stale_snapshot_can_be_configured_to_keep_alarming():
    class AlarmCfg(_Cfg):
        class tuning(_Cfg.tuning):
            on_stale = "alarm"

    stale = _snapshot({"AA": "890"}, {"890": 1}, ok=False, age_s=600)
    d = Decider(_FakeAllowlist(stale), ExitLedger(3600), _FakeAlarm(),
                AlarmCfg, _FakeEvents())
    assert d.handle(_read("ZZ")) == UNKNOWN_EPC
