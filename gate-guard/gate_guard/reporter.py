"""Off-hot-path work: EPC enrichment and batched audit posts.

Both are fire-and-forget from the decider's point of view. A bounded queue
means a backend outage degrades into dropped audit records rather than
unbounded memory growth or a stalled MQTT callback.
"""

from __future__ import annotations

import queue
import threading
import time

from .api import ApiError, StoreLenseApi
from .logs import ops


class Enricher:
    """Resolve an alarmed EPC to a product, after the buzzer has already fired.

    The `statusInStore` it returns is the tell for the sold-status race: a tag
    that comes back as anything other than in_store was almost certainly a
    legitimate purchase already marked sold by POS, and that shows up here as a
    reviewable false alarm rather than a mystery.
    """

    def __init__(self, api: StoreLenseApi, store_id: str, event_log,
                 max_queue: int = 500):
        self._api = api
        self._store_id = store_id
        self._events = event_log
        self._queue: queue.Queue = queue.Queue(maxsize=max_queue)
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="enrich", daemon=True)

    def start(self) -> None:
        self._thread.start()

    def submit(self, epc: str, outcome: str) -> None:
        try:
            self._queue.put_nowait((epc, outcome))
        except queue.Full:
            ops.warning("Enrichment queue full; dropping detail for %s", epc)

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                epc, outcome = self._queue.get(timeout=0.5)
            except queue.Empty:
                continue
            try:
                info = self._api.identify_epc(epc, self._store_id)
            except ApiError as exc:
                self._events.write("ALARM_DETAIL_FAILED", epc=epc,
                                   outcome=outcome, error=str(exc))
                continue
            if info is None:
                self._events.write("ALARM_DETAIL", epc=epc, outcome=outcome,
                                   known=False,
                                   note="EPC not in tag registry -- foreign tag")
                continue
            status = info.get("statusInStore")
            self._events.write(
                "ALARM_DETAIL", epc=epc, outcome=outcome, known=True,
                product=info.get("productName"), sku=info.get("sku"),
                eans=info.get("eans"), status_in_store=status,
                zone=info.get("zoneName"),
                likely_false_alarm=(status is not None and status != "in_store"),
            )

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=3)


class Reporter:
    """Batch alarmed EPCs into periodic POST /api/gate/checks records.

    billRef is left null deliberately -- see api.record_gate_check.
    """

    def __init__(self, api: StoreLenseApi, store_id: str, interval_s: float,
                 event_log, enabled: bool = True, max_batch: int = 200):
        self._api = api
        self._store_id = store_id
        self._interval = interval_s
        self._events = event_log
        self._enabled = enabled
        self._max_batch = max_batch
        self._lock = threading.Lock()
        self._pending: list[str] = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="report", daemon=True)

    def start(self) -> None:
        if self._enabled:
            self._thread.start()

    def submit(self, epc: str, _outcome: str) -> None:
        if not self._enabled:
            return
        with self._lock:
            if len(self._pending) < self._max_batch:
                self._pending.append(epc)

    def _drain(self) -> list[str]:
        with self._lock:
            batch, self._pending = self._pending, []
        return batch

    def _run(self) -> None:
        while not self._stop.wait(self._interval):
            self._flush()
        self._flush()

    def _flush(self) -> None:
        batch = self._drain()
        if not batch:
            return
        try:
            self._api.record_gate_check(
                store_id=self._store_id,
                outcome="FLAGGED",
                expected=0,
                matched=0,
                extra=len(batch),
                epcs_matched=[],
                epcs_extra=batch,
            )
            self._events.write("REPORTED", count=len(batch))
        except ApiError as exc:
            ops.warning("Gate-check report failed (%d EPCs dropped): %s",
                        len(batch), exc)
            self._events.write("REPORT_FAILED", count=len(batch), error=str(exc))

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=5)


class Heartbeat:
    """Periodic one-line health summary. The thing you grep when asked
    'was the gate even running last Tuesday?'"""

    def __init__(self, decider, allowlist, event_log, interval_s: float = 60.0):
        self._decider = decider
        self._allowlist = allowlist
        self._events = event_log
        self._interval = interval_s
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="heartbeat", daemon=True)
        self._started = time.time()

    def start(self) -> None:
        self._thread.start()

    def _run(self) -> None:
        while not self._stop.wait(self._interval):
            snap = self._allowlist.snapshot
            self._events.write(
                "HEARTBEAT",
                uptime_s=round(time.time() - self._started),
                snapshot_ok=snap.ok,
                snapshot_age_s=round(snap.age_s(), 1),
                bills=snap.bill_count,
                allowlist_size=len(snap.allowed_epcs),
                **{k.lower(): v for k, v in self._decider.counters.items()},
            )

    def stop(self) -> None:
        self._stop.set()
