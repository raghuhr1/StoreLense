"""Configuration loading and validation.

Every tunable lives in one YAML file. Nothing that varies between sites is
hardcoded anywhere else in the package. Environment variables of the form
GG_<SECTION>_<KEY> override the file, which is how secrets get into a container
without baking them into an image.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field, fields, is_dataclass
from typing import Any

import yaml


class ConfigError(Exception):
    """Raised for a malformed or incomplete config; always fatal at startup."""


# --------------------------------------------------------------------------- #
# Sections
# --------------------------------------------------------------------------- #

@dataclass
class ApiConfig:
    base_url: str = "http://150.241.244.61:8080"
    username: str = ""
    password: str = ""
    store_id: str = ""
    verify_tls: bool = True
    timeout_s: float = 10.0
    # Refresh this many seconds before the token actually expires.
    refresh_margin_s: int = 60


@dataclass
class MqttConfig:
    host: str = "127.0.0.1"
    port: int = 1883
    client_id: str = "storelense-gate-guard"
    username: str | None = None
    password: str | None = None
    tls: bool = False
    tls_insecure: bool = False
    ca_cert: str | None = None
    keepalive_s: int = 30
    # Durable session so a broker-side disconnect does not silently drop reads.
    clean_session: bool = False
    qos: int = 1
    tag_topic: str = ""          # e.g. "fx9600/10.1.2.16/tags"  -- you provide
    control_topic: str = ""      # e.g. "fx9600/10.1.2.16/control"


@dataclass
class TagFieldsConfig:
    """Dotted paths tried in order when pulling a field out of a tag event.

    Zebra's IoT Connector emits several shapes depending on the configured
    output format, and every site seems to tweak it. Rather than guess, try the
    common ones and let the operator pin the exact path once `gate-guard tap`
    has shown them the real payload.
    """
    epc: list[str] = field(default_factory=lambda: [
        "data.idHex", "idHex", "data.epc", "epc", "tag.epc",
        "EPC", "tagID", "tag_id", "id_hex",
    ])
    rssi: list[str] = field(default_factory=lambda: [
        "data.peakRssi", "peakRssi", "data.rssi", "rssi", "RSSI", "peak_rssi",
    ])
    antenna: list[str] = field(default_factory=lambda: [
        "data.antenna", "antenna", "antennaPort", "data.antennaPort", "ant",
    ])
    # If the payload is an envelope holding a list of reads, name it here.
    batch: list[str] = field(default_factory=lambda: [
        "data.tags", "tags", "tagList", "reads",
    ])


@dataclass
class GpoConfig:
    # mqtt | llrp | rest | null
    driver: str = "mqtt"
    port: int = 1
    reader_ip: str = "10.1.2.16"

    # -- mqtt driver -------------------------------------------------------- #
    # {port} and {state} are substituted; {state} is the literal true/false.
    mqtt_on_payload: str = '{{"command":"set_gpo","command_id":"gg-on","payload":{{"pin":{port},"state":true}}}}'
    mqtt_off_payload: str = '{{"command":"set_gpo","command_id":"gg-off","payload":{{"pin":{port},"state":false}}}}'

    # -- rest driver (IoT Connector local REST) ----------------------------- #
    rest_base_url: str = "https://10.1.2.16"
    rest_username: str = "admin"
    rest_password: str = ""
    rest_verify_tls: bool = False
    rest_login_path: str = "/cloud/localRestLogin"
    rest_gpo_path: str = "/cloud/setGPO"
    rest_gpo_method: str = "PUT"
    rest_gpo_payload: str = '{{"pin":{port},"state":{state}}}'

    # -- llrp driver -------------------------------------------------------- #
    llrp_port: int = 5084
    llrp_timeout_s: float = 5.0


@dataclass
class TuningConfig:
    # Only consider bills created inside this window as live exits. Without it
    # the pending list grows forever, because nothing clears gate_checked_at.
    bill_window_hours: float = 3.0
    sync_interval_s: float = 4.0
    # Below this the read is treated as a stray from the sales floor, not an exit.
    rssi_min: int = -60
    # Ignore repeat reads of the same EPC inside this window.
    debounce_s: float = 5.0
    alarm_hold_s: float = 2.0
    # Beyond this age the snapshot is not trustworthy.
    stale_snapshot_s: float = 300.0
    # suppress -> go quiet on a stale snapshot. alarm -> keep alarming.
    on_stale: str = "suppress"
    # Remember legitimate exits this long, so a re-read does not double-charge
    # quota or alarm on the way back in.
    exit_ledger_ttl_s: float = 10800.0
    # Caches keyed by billRef / EAN, to keep the sync cycle cheap.
    bill_cache_ttl_s: float = 600.0
    ean_cache_ttl_s: float = 20.0
    sync_workers: int = 8
    # Batched audit posts back to /api/gate/checks.
    report_interval_s: float = 30.0
    report_enabled: bool = True


@dataclass
class LoggingConfig:
    dir: str = "./logs"
    # Structured decision log, one JSON object per line.
    events_file: str = "events.jsonl"
    # Human-readable operational log.
    ops_file: str = "gate-guard.log"
    level: str = "INFO"
    # Log every allowed read too, not just alarms. Noisy but invaluable when a
    # paying customer gets beeped at and you need the surrounding context.
    log_allowed: bool = True
    # Debounce/RSSI drops are thousands of lines an hour. Off unless debugging.
    log_drops: bool = False
    rotate_mb: int = 64
    backup_count: int = 14
    exit_ledger_file: str = "exit-ledger.json"


@dataclass
class Config:
    api: ApiConfig = field(default_factory=ApiConfig)
    mqtt: MqttConfig = field(default_factory=MqttConfig)
    tag_fields: TagFieldsConfig = field(default_factory=TagFieldsConfig)
    gpo: GpoConfig = field(default_factory=GpoConfig)
    tuning: TuningConfig = field(default_factory=TuningConfig)
    logging: LoggingConfig = field(default_factory=LoggingConfig)


# --------------------------------------------------------------------------- #
# Loading
# --------------------------------------------------------------------------- #

_SECTIONS = {f.name: f.type for f in fields(Config)}


def _build_section(cls: Any, raw: dict, section: str) -> Any:
    known = {f.name: f for f in fields(cls)}
    unknown = set(raw) - set(known)
    if unknown:
        raise ConfigError(
            f"{section}: unknown key(s) {sorted(unknown)}. "
            f"Valid keys: {sorted(known)}"
        )
    return cls(**raw)


def _env_overrides(data: dict) -> dict:
    """GG_API_PASSWORD=... overrides api.password, and so on."""
    for name, value in os.environ.items():
        if not name.startswith("GG_"):
            continue
        rest = name[3:].lower()
        for section in _SECTIONS:
            prefix = section + "_"
            if rest.startswith(prefix):
                key = rest[len(prefix):]
                data.setdefault(section, {})[key] = _coerce(value)
                break
    return data


def _coerce(value: str) -> Any:
    low = value.strip().lower()
    if low in ("true", "yes", "on"):
        return True
    if low in ("false", "no", "off"):
        return False
    if low in ("null", "none", ""):
        return None
    try:
        return int(value)
    except ValueError:
        pass
    try:
        return float(value)
    except ValueError:
        pass
    return value


def load_config(path: str) -> Config:
    try:
        with open(path, "r", encoding="utf-8") as fh:
            raw = yaml.safe_load(fh) or {}
    except FileNotFoundError as exc:
        raise ConfigError(f"config file not found: {path}") from exc
    except yaml.YAMLError as exc:
        raise ConfigError(f"config file is not valid YAML: {exc}") from exc

    if not isinstance(raw, dict):
        raise ConfigError("config root must be a mapping")

    unknown = set(raw) - set(_SECTIONS)
    if unknown:
        raise ConfigError(f"unknown config section(s) {sorted(unknown)}")

    raw = _env_overrides(raw)

    kwargs = {}
    for name, cls in ((f.name, f.default_factory()) for f in fields(Config)):  # type: ignore[misc]
        section_raw = raw.get(name) or {}
        if not isinstance(section_raw, dict):
            raise ConfigError(f"section '{name}' must be a mapping")
        kwargs[name] = _build_section(type(cls), section_raw, name)

    cfg = Config(**kwargs)
    _validate(cfg)
    return cfg


def _validate(cfg: Config) -> None:
    problems: list[str] = []

    if not cfg.api.base_url:
        problems.append("api.base_url is required")
    if not cfg.api.username or not cfg.api.password:
        problems.append("api.username and api.password are required")
    if not cfg.api.store_id:
        problems.append("api.store_id is required")

    if not cfg.mqtt.host:
        problems.append("mqtt.host is required")
    if not cfg.mqtt.tag_topic:
        problems.append("mqtt.tag_topic is required")

    if cfg.gpo.driver not in ("mqtt", "llrp", "rest", "null"):
        problems.append(f"gpo.driver must be mqtt|llrp|rest|null, got '{cfg.gpo.driver}'")
    if cfg.gpo.driver == "mqtt" and not cfg.mqtt.control_topic:
        problems.append("mqtt.control_topic is required when gpo.driver is 'mqtt'")
    if not 1 <= cfg.gpo.port <= 4:
        problems.append("gpo.port must be 1-4 (the FX9600 has four GPO ports)")

    if cfg.tuning.on_stale not in ("suppress", "alarm"):
        problems.append("tuning.on_stale must be 'suppress' or 'alarm'")
    if cfg.tuning.alarm_hold_s <= 0:
        problems.append("tuning.alarm_hold_s must be > 0")
    if cfg.tuning.bill_window_hours <= 0:
        problems.append("tuning.bill_window_hours must be > 0")
    if cfg.tuning.rssi_min > 0:
        problems.append("tuning.rssi_min should be negative (dBm), e.g. -60")
    if not cfg.tag_fields.epc:
        problems.append("tag_fields.epc must list at least one path")

    if problems:
        raise ConfigError("invalid configuration:\n  - " + "\n  - ".join(problems))


def redacted(cfg: Config) -> dict:
    """Config as a dict with secrets masked, for the startup banner."""
    secret_keys = {"password", "rest_password", "refresh_token"}

    def walk(obj: Any) -> Any:
        if is_dataclass(obj):
            out = {}
            for f in fields(obj):
                value = getattr(obj, f.name)
                out[f.name] = "***" if (f.name in secret_keys and value) else walk(value)
            return out
        return obj

    return walk(cfg)
