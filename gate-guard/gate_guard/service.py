"""Wiring. Builds every component, starts the loops, shuts down cleanly."""

from __future__ import annotations

import os
import signal
import threading
import time

from . import logs
from .allowlist import AllowlistBuilder, AllowlistService, ExitLedger
from .api import StoreLenseApi
from .config import Config
from .decider import Decider
from .gpo import AlarmController, build_driver
from .logs import ops
from .mqtt_client import MqttTransport
from .reporter import Enricher, Heartbeat, Reporter
from .tagparse import extract_reads


class GateGuard:
    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.events = logs.setup(cfg.logging)

        self.api = StoreLenseApi(cfg.api)
        self.ledger = ExitLedger(
            cfg.tuning.exit_ledger_ttl_s,
            os.path.join(cfg.logging.dir, cfg.logging.exit_ledger_file),
        )

        self.mqtt = MqttTransport(cfg.mqtt, self._on_tag_message)

        # The mqtt GPO driver borrows the same connection the tags arrive on.
        self.alarm = AlarmController(
            build_driver(cfg, publish=self.mqtt.publish),
            cfg.tuning.alarm_hold_s,
            self.events,
        )

        builder = AllowlistBuilder(self.api, cfg, self.ledger, self.events)
        self.allowlist = AllowlistService(
            builder, cfg.tuning.sync_interval_s, self.ledger, self.events
        )

        self.enricher = Enricher(self.api, cfg.api.store_id, self.events)
        self.reporter = Reporter(
            self.api, cfg.api.store_id, cfg.tuning.report_interval_s,
            self.events, cfg.tuning.report_enabled,
        )
        self.decider = Decider(
            self.allowlist, self.ledger, self.alarm, cfg, self.events,
            enrich=self.enricher.submit, report=self.reporter.submit,
        )
        self.heartbeat = Heartbeat(self.decider, self.allowlist, self.events)

        self._stopping = threading.Event()

    # -- hot path ----------------------------------------------------------- #

    def _on_tag_message(self, _topic: str, payload: bytes) -> None:
        reads = extract_reads(payload, self.cfg.tag_fields)
        if not reads:
            return
        for read in reads:
            self.decider.handle(read)

    # -- lifecycle ---------------------------------------------------------- #

    def run(self) -> int:
        ops.info("gate-guard starting (store=%s, reader=%s, gpo=%s/port %s)",
                 self.cfg.api.store_id, self.cfg.gpo.reader_ip,
                 self.cfg.gpo.driver, self.cfg.gpo.port)

        self.api.login()

        self.mqtt.start()
        if not self.mqtt.wait_connected(15):
            # Not fatal: paho keeps retrying and the gate recovers on its own.
            ops.warning("MQTT not connected after 15s; continuing to retry")

        self.alarm.start()
        self.allowlist.start()
        self.enricher.start()
        self.reporter.start()
        self.heartbeat.start()

        if self.allowlist.wait_ready(30):
            snap = self.allowlist.snapshot
            ops.info("First snapshot ready: %d bills, %d EANs, %d EPCs",
                     snap.bill_count, snap.ean_count, len(snap.allowed_epcs))
        else:
            ops.error("No snapshot after 30s -- check API credentials and role")

        self._install_signals()
        ops.info("gate-guard running. Ctrl-C or SIGTERM to stop.")
        try:
            while not self._stopping.wait(1.0):
                pass
        except KeyboardInterrupt:
            pass
        self.shutdown()
        return 0

    def _install_signals(self) -> None:
        def handler(signum, _frame):
            ops.info("Signal %s received; shutting down", signum)
            self._stopping.set()

        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                signal.signal(sig, handler)
            except (ValueError, OSError):
                pass  # not on the main thread, or not supported on this platform

    def shutdown(self) -> None:
        ops.info("Stopping...")
        self.heartbeat.stop()
        self.reporter.stop()
        self.enricher.stop()
        self.allowlist.stop()
        # Alarm last: it must still be able to publish the final GPO-off.
        self.alarm.stop()
        self.mqtt.stop()
        time.sleep(0.2)
        ops.info("Stopped. Final counters: %s", self.decider.counters)
