"""Command line.

  gate-guard run         start the daemon
  gate-guard check       validate config, prove API auth + role, print a snapshot
  gate-guard tap         print raw MQTT payloads so you can pin tag_fields paths
  gate-guard gpo-test    click the relay on/off so you can hear it before trusting it
  gate-guard sync-once   build one allowlist snapshot and dump it
  gate-guard explain     resolve one EPC against the live allowlist, no alarm
"""

from __future__ import annotations

import argparse
import json
import sys
import time

from . import logs
from .allowlist import AllowlistBuilder, ExitLedger
from .api import ApiError, StoreLenseApi
from .config import ConfigError, load_config, redacted
from .gpo import build_driver
from .logs import ops
from .mqtt_client import MqttTransport
from .service import GateGuard
from .tagparse import extract_reads, normalise_epc

DEFAULT_CONFIG = "config.yaml"


def _load(path: str):
    try:
        return load_config(path)
    except ConfigError as exc:
        print(f"Config error: {exc}", file=sys.stderr)
        raise SystemExit(2)


# --------------------------------------------------------------------------- #

def cmd_run(args) -> int:
    cfg = _load(args.config)
    return GateGuard(cfg).run()


def cmd_check(args) -> int:
    cfg = _load(args.config)
    logs.setup(cfg.logging)
    print("Config OK\n")
    print(json.dumps(redacted(cfg), indent=2, default=str))

    api = StoreLenseApi(cfg.api)
    print("\n-- API --")
    try:
        api.login()
    except ApiError as exc:
        print(f"  FAIL  login: {exc}")
        return 1
    print(f"  OK    login (role={api.role}, token storeId={api.token_store_id})")

    if api.role not in ("ADMIN", "STORE_MANAGER"):
        print("  WARN  GET /api/gate/checks/bills needs ADMIN or STORE_MANAGER; "
              f"this account is {api.role}. Bill sync will 403.")
    if api.token_store_id and api.role != "ADMIN" \
            and api.token_store_id != cfg.api.store_id:
        print(f"  WARN  config store_id {cfg.api.store_id} differs from the "
              f"token's store {api.token_store_id}; a non-admin is pinned to "
              "its own store server-side.")

    ledger = ExitLedger(cfg.tuning.exit_ledger_ttl_s)
    builder = AllowlistBuilder(api, cfg, ledger)
    started = time.time()
    try:
        snap = builder.build()
    except ApiError as exc:
        print(f"  FAIL  allowlist build: {exc}")
        return 1
    finally:
        builder.close()
    print(f"  OK    allowlist: {snap.bill_count} pending bills, "
          f"{snap.ean_count} EANs, {len(snap.allowed_epcs)} EPCs "
          f"in {(time.time() - started) * 1000:.0f}ms")

    print("\n-- MQTT --")
    seen = {"count": 0}

    def on_msg(_topic, payload):
        seen["count"] += len(extract_reads(payload, cfg.tag_fields))

    transport = MqttTransport(cfg.mqtt, on_msg)
    transport.start()
    if transport.wait_connected(10):
        print(f"  OK    connected, subscribed to '{cfg.mqtt.tag_topic}'")
        print("        listening 5s for tag reads...")
        time.sleep(5)
        if seen["count"]:
            print(f"  OK    parsed {seen['count']} tag read(s)")
        else:
            print("  WARN  no parseable reads. Wave a tag, then run "
                  "'gate-guard tap' to see the raw payload shape.")
    else:
        print("  FAIL  could not connect")
        transport.stop()
        return 1
    transport.stop()

    print("\n-- GPO --")
    print(f"  driver={cfg.gpo.driver} port={cfg.gpo.port} "
          f"reader={cfg.gpo.reader_ip}")
    print("  run 'gate-guard gpo-test' to actually click the relay")
    return 0


def cmd_tap(args) -> int:
    """Print what the reader is really sending. Run this before tuning
    tag_fields -- guessing at the payload shape is the single most common way
    to end up with a gate that never alarms."""
    cfg = _load(args.config)
    logs.setup(cfg.logging)

    def on_msg(topic, payload):
        text = payload.decode("utf-8", errors="replace")
        print(f"\n--- {topic} ---")
        try:
            print(json.dumps(json.loads(text), indent=2)[:4000])
        except ValueError:
            print(text[:4000])
        reads = extract_reads(payload, cfg.tag_fields)
        if reads:
            print(f"  parsed -> {[(r.epc, r.rssi, r.antenna) for r in reads]}")
        else:
            print("  parsed -> NOTHING. Add the right dotted path to "
                  "tag_fields.epc in config.yaml")

    transport = MqttTransport(cfg.mqtt, on_msg)
    transport.start()
    if not transport.wait_connected(10):
        print("Could not connect to broker", file=sys.stderr)
        return 1
    print(f"Tapping '{cfg.mqtt.tag_topic}' for {args.seconds}s. Wave a tag.")
    try:
        time.sleep(args.seconds)
    except KeyboardInterrupt:
        pass
    transport.stop()
    return 0


def cmd_gpo_test(args) -> int:
    cfg = _load(args.config)
    logs.setup(cfg.logging)

    transport = None
    publish = None
    if cfg.gpo.driver == "mqtt":
        transport = MqttTransport(cfg.mqtt, lambda *_: None)
        transport.start()
        if not transport.wait_connected(10):
            print("Could not connect to broker for GPO publish", file=sys.stderr)
            return 1
        publish = transport.publish

    driver = build_driver(cfg, publish=publish)
    print(f"Driver: {driver.name}, reader {cfg.gpo.reader_ip}, "
          f"GPO port {cfg.gpo.port}")
    rc = 0
    try:
        for i in range(args.cycles):
            print(f"  cycle {i + 1}/{args.cycles}: HIGH ({args.hold}s)")
            driver.set(True)
            time.sleep(args.hold)
            print("                 LOW")
            driver.set(False)
            time.sleep(args.gap)
    except Exception as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        if cfg.gpo.driver == "llrp":
            print("Note: LLRP permits one client connection. If IoT Connector "
                  "owns the reader, port 5084 may be unavailable -- use "
                  "gpo.driver: mqtt instead.", file=sys.stderr)
        rc = 1
    finally:
        try:
            driver.set(False)   # never leave it high
        except Exception:
            pass
        driver.close()
        if transport:
            transport.stop()
    if rc == 0:
        print("Done. Did the buzzer sound? If not, the payload template or "
              "path is wrong, not the wiring.")
    return rc


def cmd_sync_once(args) -> int:
    cfg = _load(args.config)
    logs.setup(cfg.logging)
    api = StoreLenseApi(cfg.api)
    api.login()
    ledger = ExitLedger(cfg.tuning.exit_ledger_ttl_s)
    builder = AllowlistBuilder(api, cfg, ledger)
    try:
        snap = builder.build()
    finally:
        builder.close()
    print(json.dumps({
        "bills": snap.bill_count,
        "eans": snap.ean_count,
        "allowed_epcs": len(snap.allowed_epcs),
        "quota": snap.quota,
        "sample_epcs": sorted(snap.allowed_epcs)[:20],
    }, indent=2))
    return 0


def cmd_explain(args) -> int:
    """Would this EPC alarm right now, and why? No buzzer, no side effects."""
    cfg = _load(args.config)
    logs.setup(cfg.logging)
    epc = normalise_epc(args.epc)
    if not epc:
        print(f"'{args.epc}' is not valid hex", file=sys.stderr)
        return 2

    api = StoreLenseApi(cfg.api)
    api.login()
    ledger = ExitLedger(cfg.tuning.exit_ledger_ttl_s)
    builder = AllowlistBuilder(api, cfg, ledger)
    try:
        snap = builder.build()
    finally:
        builder.close()

    ean = snap.epc_to_ean.get(epc)
    on_list = epc in snap.allowed_epcs
    remaining = snap.quota.get(ean, 0) if ean else 0
    if on_list and remaining > 0:
        verdict = "ALLOWED (on a pending bill, quota remaining)"
    elif on_list:
        verdict = "QUOTA_EXCEEDED (product is on a bill, but all units accounted for)"
    else:
        verdict = "UNKNOWN_EPC -> WOULD ALARM"

    detail = api.identify_epc(epc, cfg.api.store_id)
    print(json.dumps({
        "epc": epc,
        "verdict": verdict,
        "ean": ean,
        "quota_remaining": remaining,
        "allowlist_size": len(snap.allowed_epcs),
        "pending_bills": snap.bill_count,
        "registry": detail or "not registered (foreign tag)",
    }, indent=2, default=str))
    return 0


# --------------------------------------------------------------------------- #

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="gate-guard",
        description="StoreLense FX9600 exit-gate EPC monitor",
    )
    parser.add_argument("-c", "--config", default=DEFAULT_CONFIG,
                        help=f"config file (default: {DEFAULT_CONFIG})")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("run", help="start the daemon").set_defaults(func=cmd_run)
    sub.add_parser("check", help="validate config, API, MQTT").set_defaults(func=cmd_check)
    sub.add_parser("sync-once", help="build one snapshot and print it").set_defaults(func=cmd_sync_once)

    tap = sub.add_parser("tap", help="print raw MQTT tag payloads")
    tap.add_argument("--seconds", type=int, default=30)
    tap.set_defaults(func=cmd_tap)

    gpo = sub.add_parser("gpo-test", help="pulse the GPO port")
    gpo.add_argument("--cycles", type=int, default=3)
    gpo.add_argument("--hold", type=float, default=1.0)
    gpo.add_argument("--gap", type=float, default=1.0)
    gpo.set_defaults(func=cmd_gpo_test)

    explain = sub.add_parser("explain", help="why would this EPC alarm?")
    explain.add_argument("epc")
    explain.set_defaults(func=cmd_explain)

    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except KeyboardInterrupt:
        return 130
    except ApiError as exc:
        ops.error("API error: %s", exc)
        print(f"API error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
