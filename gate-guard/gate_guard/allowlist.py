"""Allowlist snapshot: the only thing the hot path is allowed to consult.

Built on a background loop, published by a single atomic reference swap, and
never mutated in place -- which is why the MQTT callback needs no locks and
never blocks on I/O.

Resolution chain, all of it existing backend endpoints:

    pending bills (time-bounded)
      -> GET /bills/{billRef}          -> items[] {ean, qty, isRfidEnabled}
        -> GET /epc-by-ean/{ean}       -> in-store EPCs for that product

Membership alone over-permits: epc-by-ean returns *every* in-store EPC of the
product, so a bill for one shirt would unlock the whole rack. Hence the
per-EAN quota alongside the set.
"""

from __future__ import annotations

import json
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from .api import ApiError, AuthError, StoreLenseApi
from .logs import ops


def _brief(exc: Exception, limit: int = 120) -> str:
    """Collapse an error to one line. nginx 503 bodies are full HTML pages and
    would otherwise dump twelve lines into the log per failed EAN."""
    return " ".join(str(exc).split())[:limit]


# --------------------------------------------------------------------------- #
# Snapshot
# --------------------------------------------------------------------------- #

@dataclass(frozen=True)
class Snapshot:
    built_at: float
    allowed_epcs: frozenset
    epc_to_ean: dict           # epc -> ean
    quota: dict                # ean -> remaining permitted exits
    bill_count: int
    ean_count: int
    ok: bool = True            # False if the build failed and this is stale
    error: str | None = None
    # EANs served from an expired cache entry because the live call failed.
    # Usable, but worth surfacing.
    degraded: int = 0
    # EANs we could not resolve at all. Any EPC of those products now looks
    # unknown, so the snapshot cannot be trusted to distinguish theft from a
    # gateway hiccup.
    missing: int = 0

    def age_s(self) -> float:
        return time.time() - self.built_at


EMPTY = Snapshot(
    built_at=0.0, allowed_epcs=frozenset(), epc_to_ean={}, quota={},
    bill_count=0, ean_count=0, ok=False, error="not yet built",
)


# --------------------------------------------------------------------------- #
# Exit ledger
# --------------------------------------------------------------------------- #

class ExitLedger:
    """EPCs that have already legitimately left, with a TTL.

    Solves two problems a plain quota counter cannot. First, quota decrements
    made against a live snapshot would be lost on the next rebuild, letting the
    same purchase pass twice. Second, a customer who steps back through the
    portal must not trigger an alarm for an item they paid for.

    So exits are recorded here, subtracted from quota at build time, and
    treated as silently allowed on the hot path.
    """

    def __init__(self, ttl_s: float, path: str | None = None):
        self._ttl = ttl_s
        self._path = path
        self._lock = threading.Lock()
        self._entries: dict[str, tuple[float, str]] = {}  # epc -> (ts, ean)
        self._dirty = False
        self._load()

    def _load(self) -> None:
        if not self._path or not os.path.exists(self._path):
            return
        try:
            with open(self._path, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            cutoff = time.time() - self._ttl
            self._entries = {
                epc: (float(ts), ean)
                for epc, (ts, ean) in data.items()
                if float(ts) >= cutoff
            }
            ops.info("Exit ledger restored: %d live entries", len(self._entries))
        except Exception as exc:
            ops.warning("Could not restore exit ledger from %s: %s", self._path, exc)

    def record(self, epc: str, ean: str | None) -> None:
        with self._lock:
            self._entries[epc] = (time.time(), ean or "")
            self._dirty = True

    def contains(self, epc: str) -> bool:
        with self._lock:
            entry = self._entries.get(epc)
            return entry is not None and time.time() - entry[0] < self._ttl

    def snapshot_counts(self) -> tuple[dict, set]:
        """(ean -> exits within TTL, set of live EPCs). Prunes as it goes."""
        cutoff = time.time() - self._ttl
        with self._lock:
            expired = [e for e, (ts, _) in self._entries.items() if ts < cutoff]
            for epc in expired:
                del self._entries[epc]
            if expired:
                self._dirty = True
            counts: dict[str, int] = {}
            epcs = set()
            for epc, (_ts, ean) in self._entries.items():
                epcs.add(epc)
                if ean:
                    counts[ean] = counts.get(ean, 0) + 1
            return counts, epcs

    def persist(self) -> None:
        if not self._path:
            return
        with self._lock:
            if not self._dirty:
                return
            data = {epc: [ts, ean] for epc, (ts, ean) in self._entries.items()}
            self._dirty = False
        tmp = self._path + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as fh:
                json.dump(data, fh)
            os.replace(tmp, self._path)
        except Exception as exc:
            ops.warning("Could not persist exit ledger: %s", exc)


# --------------------------------------------------------------------------- #
# TTL cache
# --------------------------------------------------------------------------- #

class _TtlCache:
    def __init__(self, ttl_s: float, max_entries: int = 5000):
        self._ttl = ttl_s
        self._max = max_entries
        self._lock = threading.Lock()
        self._data: dict = {}

    def get(self, key):
        with self._lock:
            entry = self._data.get(key)
            if entry is None:
                return None
            ts, value = entry
            if time.time() - ts > self._ttl:
                return None          # expired, but kept for get_stale()
            return value

    def get_stale(self, key):
        """Last known value regardless of age.

        Used as a fallback when the live call fails: a slightly old EPC list
        is far better than an empty one, which would false-alarm on every
        item of that product.
        """
        with self._lock:
            entry = self._data.get(key)
            return entry[1] if entry else None

    def put(self, key, value) -> None:
        with self._lock:
            if len(self._data) >= self._max:
                oldest = min(self._data, key=lambda k: self._data[k][0])
                del self._data[oldest]
            self._data[key] = (time.time(), value)


# --------------------------------------------------------------------------- #
# Builder
# --------------------------------------------------------------------------- #

class AllowlistBuilder:
    def __init__(self, api: StoreLenseApi, cfg, ledger: ExitLedger, event_log=None):
        self._api = api
        self._cfg = cfg
        self._tuning = cfg.tuning
        self._store_id = cfg.api.store_id
        self._ledger = ledger
        self._events = event_log
        self._bill_cache = _TtlCache(self._tuning.bill_cache_ttl_s)
        self._ean_cache = _TtlCache(self._tuning.ean_cache_ttl_s)
        self._pool = ThreadPoolExecutor(
            max_workers=max(1, self._tuning.sync_workers), thread_name_prefix="sync"
        )

    def close(self) -> None:
        self._pool.shutdown(wait=False)

    def _since_iso(self) -> str:
        since = datetime.now(timezone.utc) - timedelta(
            hours=self._tuning.bill_window_hours
        )
        return since.isoformat(timespec="seconds").replace("+00:00", "Z")

    def build(self) -> Snapshot:
        bills = self._api.list_pending_bills(self._store_id, self._since_iso())

        # bill items -> qty per EAN, RFID-tagged lines only. Non-RFID lines
        # never read at the portal; counting them would skew expected totals.
        qty_by_ean: dict[str, int] = {}
        refs = [b.get("billRef") for b in bills if b.get("billRef")]

        for bill in self._pool.map(self._fetch_bill, refs):
            if not bill:
                continue
            for item in bill.get("items") or []:
                if item.get("isRfidEnabled") is False:
                    continue
                ean = (item.get("ean") or "").strip()
                if not ean:
                    continue
                qty_by_ean[ean] = qty_by_ean.get(ean, 0) + int(item.get("qty") or 0)

        eans = sorted(qty_by_ean)
        epc_to_ean: dict[str, str] = {}
        degraded = missing = 0
        for ean, (epcs, status) in zip(eans, self._pool.map(self._fetch_epcs, eans)):
            if status == "degraded":
                degraded += 1
            elif status == "missing":
                missing += 1
            for epc in epcs:
                epc_to_ean[epc] = ean

        # Subtract exits already spent, and keep those EPCs allowed so a
        # customer stepping back through the portal stays silent.
        exit_counts, exited_epcs = self._ledger.snapshot_counts()
        quota = {
            ean: max(0, total - exit_counts.get(ean, 0))
            for ean, total in qty_by_ean.items()
        }
        allowed = frozenset(epc_to_ean) | exited_epcs

        if missing:
            ops.error("Snapshot incomplete: %d/%d EANs unresolved. Marking it "
                      "untrustworthy so the stale policy applies rather than "
                      "alarming on a half-built allowlist.", missing, len(eans))

        return Snapshot(
            built_at=time.time(),
            allowed_epcs=allowed,
            epc_to_ean=epc_to_ean,
            quota=quota,
            bill_count=len(refs),
            ean_count=len(eans),
            # A partial build is the dangerous case: the EPCs we failed to
            # fetch are indistinguishable from stolen goods.
            ok=(missing == 0),
            error=(f"{missing} EAN(s) unresolved" if missing else None),
            degraded=degraded,
            missing=missing,
        )

    def _fetch_bill(self, bill_ref: str) -> dict | None:
        cached = self._bill_cache.get(bill_ref)
        if cached is not None:
            return cached
        try:
            bill = self._api.lookup_bill(bill_ref, self._store_id)
        except ApiError as exc:
            ops.warning("Bill %s lookup failed: %s", bill_ref, _brief(exc))
            return None
        self._bill_cache.put(bill_ref, bill)
        return bill

    def _fetch_epcs(self, ean: str):
        """Returns (epcs, status) where status is ok | degraded | missing."""
        cached = self._ean_cache.get(ean)
        if cached is not None:
            return cached, "ok"
        try:
            data = self._api.epcs_by_ean(ean, self._store_id) or {}
        except ApiError as exc:
            stale = self._ean_cache.get_stale(ean)
            if stale is not None:
                ops.warning("epc-by-ean %s failed (%s); using last known %d EPCs",
                            ean, _brief(exc), len(stale))
                return stale, "degraded"
            ops.error("epc-by-ean %s failed (%s) and nothing cached -- items of "
                      "this product would false-alarm", ean, _brief(exc))
            return [], "missing"
        epcs = [e.strip().upper() for e in (data.get("epcs") or []) if e]
        self._ean_cache.put(ean, epcs)
        return epcs, "ok"


class AllowlistService:
    """Background loop owning the current snapshot."""

    def __init__(self, builder: AllowlistBuilder, interval_s: float,
                 ledger: ExitLedger, event_log=None):
        self._builder = builder
        self._interval = interval_s
        self._ledger = ledger
        self._events = event_log
        self._snapshot: Snapshot = EMPTY
        self._stop = threading.Event()
        self._ready = threading.Event()
        self._thread = threading.Thread(
            target=self._run, name="bill-sync", daemon=True
        )

    @property
    def snapshot(self) -> Snapshot:
        return self._snapshot          # atomic reference read; no lock needed

    def start(self) -> None:
        self._thread.start()

    def wait_ready(self, timeout: float) -> bool:
        return self._ready.wait(timeout)

    def _run(self) -> None:
        while not self._stop.is_set():
            started = time.time()
            try:
                snap = self._builder.build()
                self._snapshot = snap
                self._ready.set()
                ops.debug(
                    "Snapshot: %d bills, %d EANs, %d EPCs, %.0fms",
                    snap.bill_count, snap.ean_count, len(snap.allowed_epcs),
                    (time.time() - started) * 1000,
                )
                if self._events:
                    self._events.write(
                        "SNAPSHOT", bills=snap.bill_count, eans=snap.ean_count,
                        epcs=len(snap.allowed_epcs), ok=snap.ok,
                        degraded=snap.degraded, missing=snap.missing,
                        build_ms=round((time.time() - started) * 1000),
                    )
            except AuthError as exc:
                # Almost always a role problem: /bills needs ADMIN or STORE_MANAGER.
                ops.error("Bill sync unauthorised: %s", exc)
                self._mark_failed(str(exc))
            except ApiError as exc:
                ops.error("Bill sync failed: %s", exc)
                self._mark_failed(str(exc))
            except Exception as exc:  # never let the loop die
                ops.exception("Bill sync crashed: %s", exc)
                self._mark_failed(str(exc))

            self._ledger.persist()
            self._stop.wait(self._interval)

    def _mark_failed(self, error: str) -> None:
        """Keep serving the last good snapshot; the decider watches its age."""
        previous = self._snapshot
        self._snapshot = Snapshot(
            built_at=previous.built_at,
            allowed_epcs=previous.allowed_epcs,
            epc_to_ean=previous.epc_to_ean,
            quota=previous.quota,
            bill_count=previous.bill_count,
            ean_count=previous.ean_count,
            ok=False,
            error=error,
        )
        if self._events:
            self._events.write("SNAPSHOT_FAILED", error=error,
                               stale_age_s=round(previous.age_s(), 1))

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=5)
        self._ledger.persist()
        self._builder.close()
