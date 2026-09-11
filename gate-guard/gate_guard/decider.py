"""The hot path.

Runs on the MQTT callback thread and must never make a network call. A portal
fires dozens of reads per second; one blocking HTTP request in here backs up
the broker queue and the buzzer sounds after the customer has left.

So: everything slow is either precomputed (the snapshot) or deferred (the
identify-EPC enrichment and the audit post). The alarm fires on set membership
alone.
"""

from __future__ import annotations

import threading
import time

from .allowlist import AllowlistService, ExitLedger
from .logs import ops
from .tagparse import TagRead

# Decision outcomes, written verbatim into events.jsonl.
ALLOWED = "ALLOWED"
ALLOWED_REENTRY = "ALLOWED_REENTRY"
QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
UNKNOWN_EPC = "UNKNOWN_EPC"
SUPPRESSED_STALE = "SUPPRESSED_STALE"
DROPPED_RSSI = "DROPPED_RSSI"
DROPPED_DEBOUNCE = "DROPPED_DEBOUNCE"

ALARM_OUTCOMES = (QUOTA_EXCEEDED, UNKNOWN_EPC)


class _Debounce:
    """Suppress repeat reads of the same EPC inside a window.

    Without this the portal re-reads a stationary tag continuously and the
    buzzer never stops while someone is standing in it.
    """

    def __init__(self, window_s: float, max_entries: int = 20000):
        self._window = window_s
        self._max = max_entries
        self._seen: dict[str, float] = {}
        self._lock = threading.Lock()
        self._last_sweep = time.time()

    def seen_recently(self, epc: str) -> bool:
        now = time.time()
        with self._lock:
            last = self._seen.get(epc)
            if last is not None and now - last < self._window:
                return True
            self._seen[epc] = now
            if now - self._last_sweep > max(self._window, 30.0):
                cutoff = now - self._window
                for key in [k for k, v in self._seen.items() if v < cutoff]:
                    del self._seen[key]
                self._last_sweep = now
            if len(self._seen) > self._max:
                self._seen.clear()
        return False


class Decider:
    def __init__(self, allowlist: AllowlistService, ledger: ExitLedger,
                 alarm, cfg, event_log, enrich=None, report=None):
        self._allowlist = allowlist
        self._ledger = ledger
        self._alarm = alarm
        self._tuning = cfg.tuning
        self._log_cfg = cfg.logging
        self._events = event_log
        self._enrich = enrich      # async identify-epc callback
        self._report = report      # async audit callback
        self._debounce = _Debounce(cfg.tuning.debounce_s)
        self.counters = {
            ALLOWED: 0, ALLOWED_REENTRY: 0, QUOTA_EXCEEDED: 0, UNKNOWN_EPC: 0,
            SUPPRESSED_STALE: 0, DROPPED_RSSI: 0, DROPPED_DEBOUNCE: 0,
        }

    def handle(self, read: TagRead) -> str:
        # 1. Stray reads from the sales floor, not exits.
        if read.rssi is not None and read.rssi < self._tuning.rssi_min:
            return self._finish(DROPPED_RSSI, read, None, None, quiet=True)

        # 2. The same tag, still in the portal.
        if self._debounce.seen_recently(read.epc):
            return self._finish(DROPPED_DEBOUNCE, read, None, None, quiet=True)

        snap = self._allowlist.snapshot
        age = snap.age_s()

        # 3. Already walked out on a legitimate purchase -- stay silent and do
        #    not spend quota again.
        if self._ledger.contains(read.epc):
            return self._finish(ALLOWED_REENTRY, read,
                                snap.epc_to_ean.get(read.epc), snap)

        # 4. A snapshot we cannot trust. Alarming on a stale allowlist means
        #    beeping at paying customers, and a gate that cries wolf gets
        #    unplugged by staff within a week -- so the default is to go quiet
        #    and shout in the ops log instead.
        stale = (not snap.ok) or age > self._tuning.stale_snapshot_s
        if stale and self._tuning.on_stale == "suppress":
            ops.warning(
                "Snapshot stale (%.0fs, ok=%s) -- suppressing decision for %s",
                age, snap.ok, read.epc,
            )
            return self._finish(SUPPRESSED_STALE, read, None, snap)

        ean = snap.epc_to_ean.get(read.epc)

        # 5. On a bill, and the bill still has headroom for this product.
        if read.epc in snap.allowed_epcs and ean:
            remaining = snap.quota.get(ean, 0)
            if remaining > 0:
                snap.quota[ean] = remaining - 1
                self._ledger.record(read.epc, ean)
                return self._finish(ALLOWED, read, ean, snap)
            # Right product, but more units are leaving than were paid for.
            return self._alarm_now(QUOTA_EXCEEDED, read, ean, snap)

        # 6. Not on any pending bill.
        return self._alarm_now(UNKNOWN_EPC, read, ean, snap)

    def _alarm_now(self, outcome: str, read: TagRead, ean, snap) -> str:
        # Fire first, explain later. identify-epc is for the log line and the
        # dashboard, never for the decision -- a slow API must not delay the
        # buzzer.
        self._alarm.trigger(outcome)
        if self._enrich:
            self._enrich(read.epc, outcome)
        if self._report:
            self._report(read.epc, outcome)
        return self._finish(outcome, read, ean, snap, alarmed=True)

    def _finish(self, outcome: str, read: TagRead, ean, snap,
                quiet: bool = False, alarmed: bool = False) -> str:
        self.counters[outcome] = self.counters.get(outcome, 0) + 1

        if quiet and not self._log_cfg.log_drops:
            return outcome
        if outcome in (ALLOWED, ALLOWED_REENTRY) and not self._log_cfg.log_allowed:
            return outcome

        self._events.write(
            outcome,
            epc=read.epc,
            ean=ean,
            rssi=read.rssi,
            antenna=read.antenna,
            alarmed=alarmed,
            allowlist_size=len(snap.allowed_epcs) if snap else None,
            snapshot_age_ms=round(snap.age_s() * 1000) if snap else None,
            snapshot_ok=snap.ok if snap else None,
        )
        return outcome
