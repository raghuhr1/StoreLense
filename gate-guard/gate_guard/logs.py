"""Two log streams, deliberately separate.

`events.jsonl`   one JSON object per decision. This is the forensic record --
                 when someone reports a false alarm you replay this file around
                 the timestamp and the cause is usually obvious from
                 snapshot_age_ms and the surrounding ALLOWED lines.

`gate-guard.log` human-readable operations: syncs, reconnects, GPO commands,
                 token refreshes. Kept apart so a flood of tag events cannot
                 bury a broker disconnect.
"""

from __future__ import annotations

import collections
import json
import logging
import logging.handlers
import os
import sys
import threading
import time
from typing import Any

ops = logging.getLogger("gate_guard")


class EventLog:
    """Append-only JSONL sink with size-based rotation.

    Also keeps the last `ring_size` records in memory so the dashboard can
    show recent activity without re-reading (and re-parsing) the file on
    every poll.
    """

    def __init__(self, path: str, rotate_mb: int, backup_count: int,
                 ring_size: int = 500):
        self._lock = threading.Lock()
        self._ring: collections.deque = collections.deque(maxlen=ring_size)
        self._logger = logging.getLogger("gate_guard.events")
        self._logger.setLevel(logging.INFO)
        self._logger.propagate = False
        handler = logging.handlers.RotatingFileHandler(
            path,
            maxBytes=rotate_mb * 1024 * 1024,
            backupCount=backup_count,
            encoding="utf-8",
        )
        handler.setFormatter(logging.Formatter("%(message)s"))
        self._logger.addHandler(handler)

    def write(self, event: str, **payload: Any) -> None:
        record = {
            "ts": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime())
                  + f".{int(time.time() * 1000) % 1000:03d}Z",
            "event": event,
        }
        record.update(payload)
        line = json.dumps(record, separators=(",", ":"), default=str)
        with self._lock:
            self._ring.append(record)
            self._logger.info(line)

    def recent(self, limit: int = 200) -> list[dict]:
        with self._lock:
            items = list(self._ring)
        return items[-limit:]


def setup(cfg) -> EventLog:
    os.makedirs(cfg.dir, exist_ok=True)

    ops.setLevel(getattr(logging, cfg.level.upper(), logging.INFO))
    ops.handlers.clear()

    fmt = logging.Formatter(
        "%(asctime)s %(levelname)-7s [%(threadName)s] %(name)s: %(message)s"
    )

    file_handler = logging.handlers.RotatingFileHandler(
        os.path.join(cfg.dir, cfg.ops_file),
        maxBytes=cfg.rotate_mb * 1024 * 1024,
        backupCount=cfg.backup_count,
        encoding="utf-8",
    )
    file_handler.setFormatter(fmt)
    ops.addHandler(file_handler)

    # systemd/docker capture stdout; keep it so `journalctl -u gate-guard` works.
    stream = logging.StreamHandler(sys.stdout)
    stream.setFormatter(fmt)
    ops.addHandler(stream)

    return EventLog(
        os.path.join(cfg.dir, cfg.events_file), cfg.rotate_mb, cfg.backup_count
    )
