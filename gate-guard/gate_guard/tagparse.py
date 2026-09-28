"""Tolerant parser for FX9600 / IoT Connector tag events.

Zebra emits several payload shapes depending on the configured output format,
and sites tweak them further. Rather than hardcode one, try a list of dotted
paths per field and let the operator pin the exact one in config after
`gate-guard tap` has shown them a real message.

Handles a single object, a JSON array of objects, an envelope containing a list
of reads, and newline-delimited JSON.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Iterable


@dataclass(frozen=True)
class TagRead:
    epc: str
    rssi: int | None
    antenna: int | None
    raw: dict


def normalise_epc(value: Any) -> str | None:
    """Upper-case hex, no separators. Matching is worthless without this."""
    if value is None:
        return None
    text = str(value).strip().upper()
    for junk in (" ", ":", "-", "_"):
        text = text.replace(junk, "")
    if text.startswith("0X"):
        text = text[2:]
    if not text or any(c not in "0123456789ABCDEF" for c in text):
        return None
    return text


def dig(obj: Any, path: str) -> Any:
    """Resolve a dotted path, tolerating missing keys and list indices."""
    current = obj
    for part in path.split("."):
        if isinstance(current, dict):
            if part not in current:
                return None
            current = current[part]
        elif isinstance(current, list):
            try:
                current = current[int(part)]
            except (ValueError, IndexError):
                return None
        else:
            return None
    return current


def _first(obj: Any, paths: Iterable[str]) -> Any:
    for path in paths:
        value = dig(obj, path)
        if value is not None:
            return value
    return None


def _as_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return None


def decode_payload(payload: bytes | str) -> list[dict]:
    """Bytes off the wire -> a list of candidate event dicts."""
    if isinstance(payload, bytes):
        try:
            text = payload.decode("utf-8", errors="replace")
        except Exception:
            return []
    else:
        text = payload
    text = text.strip()
    if not text:
        return []

    try:
        parsed = json.loads(text)
    except ValueError:
        # newline-delimited JSON
        out = []
        for line in text.splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except ValueError:
                continue
            if isinstance(item, dict):
                out.append(item)
        return out

    if isinstance(parsed, dict):
        return [parsed]
    if isinstance(parsed, list):
        return [p for p in parsed if isinstance(p, dict)]
    return []


def extract_reads(payload: bytes | str, fields) -> list[TagRead]:
    """Every tag read in one MQTT message.

    Messages with no resolvable EPC -- heartbeats, status envelopes, GPI
    events -- yield nothing rather than raising.
    """
    reads: list[TagRead] = []
    for event in decode_payload(payload):
        batch = _first(event, fields.batch)
        candidates = batch if isinstance(batch, list) else [event]
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            epc = normalise_epc(_first(candidate, fields.epc))
            if epc is None and candidate is not event:
                # some formats keep the EPC on the envelope, metrics per read
                epc = normalise_epc(_first(event, fields.epc))
            if epc is None:
                continue
            rssi = _as_int(_first(candidate, fields.rssi))
            if rssi is None:
                rssi = _as_int(_first(event, fields.rssi))
            antenna = _as_int(_first(candidate, fields.antenna))
            if antenna is None:
                antenna = _as_int(_first(event, fields.antenna))
            reads.append(TagRead(epc=epc, rssi=rssi, antenna=antenna, raw=candidate))
    return reads
