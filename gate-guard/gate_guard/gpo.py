"""GPO drivers for the FX9600 buzzer output.

Four backends, chosen by `gpo.driver`, because which one works depends on the
reader's firmware branch and on what else is holding the radio:

  mqtt   Publish a control command on the reader's IoT Connector control topic.
         Recommended when tag data already flows over MQTT -- one transport,
         nothing else to open, and it coexists with IOTC owning the radio.

  llrp   Raw LLRP SET_READER_CONFIG carrying a GPOWriteData parameter, over
         TCP/5084. Firmware-independent, but LLRP allows a single client
         connection: if IoT Connector is actively managing the reader, this
         port may be unavailable. Use as a fallback.

  rest   IoT Connector's local REST API. Paths differ between firmware
         versions, so they are configurable rather than hardcoded.

  null   Log only. For bench-testing the matching logic without a buzzer.

Run `gate-guard gpo-test` to prove your chosen driver actually clicks the relay
before you trust the daemon with it.
"""

from __future__ import annotations

import socket
import struct
import threading
import time

import requests

from .logs import ops

# --------------------------------------------------------------------------- #
# LLRP wire constants (LLRP 1.0.1)
# --------------------------------------------------------------------------- #
_LLRP_VERSION = 1
_MSG_SET_READER_CONFIG = 3
_MSG_SET_READER_CONFIG_RESPONSE = 13
_MSG_KEEPALIVE = 62
_MSG_KEEPALIVE_ACK = 72
_PARAM_GPO_WRITE_DATA = 220
_LLRP_HEADER_LEN = 10


class GpoDriver:
    """Set a single GPO port high or low. Implementations must be thread-safe."""

    name = "base"

    def set(self, state: bool) -> None:
        raise NotImplementedError

    def close(self) -> None:
        pass


# --------------------------------------------------------------------------- #
# MQTT
# --------------------------------------------------------------------------- #

class MqttGpoDriver(GpoDriver):
    name = "mqtt"

    def __init__(self, cfg_gpo, cfg_mqtt, publish):
        self._cfg = cfg_gpo
        self._topic = cfg_mqtt.control_topic
        self._qos = cfg_mqtt.qos
        self._publish = publish

    def _payload(self, state: bool) -> str:
        template = self._cfg.mqtt_on_payload if state else self._cfg.mqtt_off_payload
        return template.format(port=self._cfg.port, state="true" if state else "false")

    def set(self, state: bool) -> None:
        payload = self._payload(state)
        self._publish(self._topic, payload, qos=self._qos)
        ops.debug("GPO(mqtt) -> %s: %s", self._topic, payload)


# --------------------------------------------------------------------------- #
# REST (IoT Connector local API)
# --------------------------------------------------------------------------- #

class RestGpoDriver(GpoDriver):
    name = "rest"

    def __init__(self, cfg_gpo):
        self._cfg = cfg_gpo
        self._lock = threading.Lock()
        self._session = requests.Session()
        self._session.verify = cfg_gpo.rest_verify_tls
        if not cfg_gpo.rest_verify_tls:
            try:
                import urllib3
                urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
            except Exception:  # pragma: no cover - cosmetic only
                pass
        self._token: str | None = None

    def _base(self) -> str:
        return self._cfg.rest_base_url.rstrip("/")

    def _login(self) -> None:
        url = self._base() + self._cfg.rest_login_path
        resp = self._session.get(
            url, auth=(self._cfg.rest_username, self._cfg.rest_password), timeout=10
        )
        resp.raise_for_status()
        token = None
        try:
            body = resp.json()
            for key in ("token", "access_token", "accessToken", "message"):
                if isinstance(body, dict) and body.get(key):
                    token = body[key]
                    break
        except ValueError:
            token = resp.text.strip() or None
        self._token = token
        ops.info("GPO(rest) logged in to %s", self._base())

    def set(self, state: bool) -> None:
        """Set the port, re-authenticating on any failure.

        The FX9600 does NOT return 401 for a bad token -- it returns 500 with
        {"code":-1,"message":"jwt token signature verification failed"}. And it
        appears to keep only one active REST session, so anyone logging into
        the reader's web UI silently invalidates ours. Keying recovery off 401
        alone would leave the buzzer permanently dead after that happens, so
        any failure drops the token and retries with a fresh login.
        """
        url = self._base() + self._cfg.rest_gpo_path
        payload = self._cfg.rest_gpo_payload.format(
            port=self._cfg.port, state="true" if state else "false"
        )
        with self._lock:
            last: Exception | None = None
            for attempt in (1, 2):
                try:
                    if self._token is None:
                        self._login()
                    headers = {"Content-Type": "application/json"}
                    if self._token:
                        headers["Authorization"] = f"Bearer {self._token}"
                    resp = self._session.request(
                        self._cfg.rest_gpo_method.upper(), url,
                        data=payload, headers=headers, timeout=10,
                    )
                    resp.raise_for_status()
                    # A 200 can still carry an application-level failure.
                    if '"code": -1' in resp.text or '"code":-1' in resp.text:
                        raise OSError(f"reader rejected command: {resp.text[:120]}")
                    ops.debug("GPO(rest) port %s -> %s", self._cfg.port, state)
                    return
                except Exception as exc:
                    last = exc
                    self._token = None          # force a fresh login next round
                    if attempt == 1:
                        ops.warning("GPO(rest) failed (%s); re-authenticating",
                                    str(exc)[:120])
            raise last if last else OSError("GPO set failed")


# --------------------------------------------------------------------------- #
# LLRP
# --------------------------------------------------------------------------- #

def _llrp_message(msg_type: int, body: bytes, msg_id: int) -> bytes:
    ver_type = (_LLRP_VERSION << 10) | msg_type
    return struct.pack("!HII", ver_type, _LLRP_HEADER_LEN + len(body), msg_id) + body


def _gpo_write_data(port: int, state: bool) -> bytes:
    # TLV parameter: [6b reserved | 10b type][16b length incl. header]
    # body = GPOPortNumber (u16) + GPOData (boolean in the MSB of one byte)
    body = struct.pack("!HB", port, 0x80 if state else 0x00)
    return struct.pack("!HH", _PARAM_GPO_WRITE_DATA, 4 + len(body)) + body


def build_set_gpo_message(port: int, state: bool, msg_id: int = 1) -> bytes:
    """SET_READER_CONFIG with ResetToFactoryDefault=0 and one GPOWriteData."""
    body = b"\x00" + _gpo_write_data(port, state)
    return _llrp_message(_MSG_SET_READER_CONFIG, body, msg_id)


class LlrpGpoDriver(GpoDriver):
    name = "llrp"

    def __init__(self, cfg_gpo):
        self._cfg = cfg_gpo
        self._lock = threading.Lock()
        self._sock: socket.socket | None = None
        self._msg_id = 0

    def _connect(self) -> socket.socket:
        sock = socket.create_connection(
            (self._cfg.reader_ip, self._cfg.llrp_port), timeout=self._cfg.llrp_timeout_s
        )
        sock.settimeout(self._cfg.llrp_timeout_s)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        # The reader announces itself with a READER_EVENT_NOTIFICATION. Drain it
        # if it arrives; its absence is not fatal.
        try:
            self._read_message(sock)
        except (socket.timeout, OSError):
            pass
        ops.info("GPO(llrp) connected to %s:%s",
                 self._cfg.reader_ip, self._cfg.llrp_port)
        return sock

    @staticmethod
    def _read_message(sock: socket.socket) -> tuple[int, bytes]:
        header = _recv_exact(sock, _LLRP_HEADER_LEN)
        ver_type, length, _msg_id = struct.unpack("!HII", header)
        msg_type = ver_type & 0x03FF
        if length < _LLRP_HEADER_LEN or length > 1_000_000:
            raise OSError(f"implausible LLRP message length {length}")
        body = _recv_exact(sock, length - _LLRP_HEADER_LEN)
        return msg_type, body

    def set(self, state: bool) -> None:
        with self._lock:
            for attempt in (1, 2):
                try:
                    if self._sock is None:
                        self._sock = self._connect()
                    self._msg_id += 1
                    self._sock.sendall(
                        build_set_gpo_message(self._cfg.port, state, self._msg_id)
                    )
                    deadline = time.time() + self._cfg.llrp_timeout_s
                    while time.time() < deadline:
                        msg_type, _body = self._read_message(self._sock)
                        if msg_type == _MSG_SET_READER_CONFIG_RESPONSE:
                            ops.debug("GPO(llrp) port %s -> %s",
                                      self._cfg.port, state)
                            return
                        if msg_type == _MSG_KEEPALIVE:
                            self._msg_id += 1
                            self._sock.sendall(
                                _llrp_message(_MSG_KEEPALIVE_ACK, b"", self._msg_id)
                            )
                        # anything else (event notifications) is noise here
                    raise OSError("timed out waiting for SET_READER_CONFIG_RESPONSE")
                except (OSError, socket.timeout) as exc:
                    self._drop()
                    if attempt == 2:
                        raise
                    ops.warning("GPO(llrp) error (%s); reconnecting", exc)

    def _drop(self) -> None:
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass
            self._sock = None

    def close(self) -> None:
        with self._lock:
            self._drop()


def _recv_exact(sock: socket.socket, count: int) -> bytes:
    chunks = []
    remaining = count
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            raise OSError("LLRP connection closed by reader")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


# --------------------------------------------------------------------------- #
# Null
# --------------------------------------------------------------------------- #

class NullGpoDriver(GpoDriver):
    name = "null"

    def set(self, state: bool) -> None:
        ops.info("GPO(null) would set port -> %s", state)


def build_driver(cfg, publish=None) -> GpoDriver:
    driver = cfg.gpo.driver
    if driver == "mqtt":
        if publish is None:
            raise ValueError("mqtt GPO driver needs an MQTT publish callable")
        return MqttGpoDriver(cfg.gpo, cfg.mqtt, publish)
    if driver == "llrp":
        return LlrpGpoDriver(cfg.gpo)
    if driver == "rest":
        return RestGpoDriver(cfg.gpo)
    return NullGpoDriver()


# --------------------------------------------------------------------------- #
# Alarm controller
# --------------------------------------------------------------------------- #

class AlarmController:
    """Owns the buzzer state. One timer, never one-per-tag.

    Overlapping on/off pairs from concurrent alarms are the classic way to end
    up with a GPO stuck high and a buzzer nobody can silence, so every alarm
    extends a single shared deadline instead of queuing its own off command.
    """

    def __init__(self, driver: GpoDriver, hold_s: float, event_log=None):
        self._driver = driver
        self._hold_s = hold_s
        self._events = event_log
        self._lock = threading.Lock()
        self._deadline = 0.0
        self._on = False
        self._stop = threading.Event()
        self._thread = threading.Thread(
            target=self._run, name="alarm", daemon=True
        )

    def start(self) -> None:
        # Clear a GPO left high by a previous crash before doing anything else.
        self.force_off("startup")
        self._thread.start()

    def trigger(self, reason: str = "") -> None:
        with self._lock:
            self._deadline = time.time() + self._hold_s
            already_on = self._on
            if not already_on:
                self._on = True
        if not already_on:
            self._apply(True, reason or "alarm")

    def _apply(self, state: bool, reason: str) -> None:
        try:
            self._driver.set(state)
            if self._events:
                self._events.write("GPO", state=state, reason=reason,
                                   driver=self._driver.name)
        except Exception as exc:
            ops.error("GPO set(%s) failed via %s: %s",
                      state, self._driver.name, exc)
            if self._events:
                self._events.write("GPO_ERROR", state=state, reason=reason,
                                   driver=self._driver.name, error=str(exc))

    def _run(self) -> None:
        while not self._stop.wait(0.05):
            with self._lock:
                expired = self._on and time.time() >= self._deadline
                if expired:
                    self._on = False
            if expired:
                self._apply(False, "hold-expired")

    def force_off(self, reason: str) -> None:
        with self._lock:
            self._on = False
            self._deadline = 0.0
        self._apply(False, reason)

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=2)
        # Belt and braces: never leave the site with a screaming buzzer.
        self.force_off("shutdown")
        self._driver.close()
