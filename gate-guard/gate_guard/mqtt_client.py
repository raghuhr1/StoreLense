"""MQTT transport.

Wraps paho so the rest of the package does not care whether the installed
version is 1.x or 2.x -- the callback signatures changed between them and
sites pin whatever their base image ships.

The same connection carries tag reads in and, when `gpo.driver: mqtt`, the GPO
command back out.
"""

from __future__ import annotations

import ssl
import threading
from typing import Callable

import paho.mqtt.client as mqtt

from .logs import ops

_PAHO2 = hasattr(mqtt, "CallbackAPIVersion")


class MqttTransport:
    def __init__(self, cfg, on_tag_message: Callable[[str, bytes], None]):
        self._cfg = cfg
        self._on_tag_message = on_tag_message
        self._connected = threading.Event()
        self._client = self._build_client()

    def _build_client(self) -> mqtt.Client:
        if _PAHO2:
            client = mqtt.Client(
                mqtt.CallbackAPIVersion.VERSION1,
                client_id=self._cfg.client_id,
                clean_session=self._cfg.clean_session,
            )
        else:
            client = mqtt.Client(
                client_id=self._cfg.client_id,
                clean_session=self._cfg.clean_session,
            )

        if self._cfg.username:
            client.username_pw_set(self._cfg.username, self._cfg.password or None)

        if self._cfg.tls:
            client.tls_set(
                ca_certs=self._cfg.ca_cert, cert_reqs=ssl.CERT_REQUIRED
                if not self._cfg.tls_insecure else ssl.CERT_NONE,
            )
            if self._cfg.tls_insecure:
                client.tls_insecure_set(True)

        client.reconnect_delay_set(min_delay=1, max_delay=30)
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        return client

    # -- callbacks ---------------------------------------------------------- #

    def _on_connect(self, client, _userdata, _flags, rc, *_args) -> None:
        if rc != 0:
            ops.error("MQTT connect refused (rc=%s): %s",
                      rc, mqtt.connack_string(rc) if hasattr(mqtt, "connack_string") else "")
            return
        self._connected.set()
        # Subscribe on every connect, not just the first -- a broker that lost
        # our session would otherwise leave us connected and deaf.
        client.subscribe(self._cfg.tag_topic, qos=self._cfg.qos)
        ops.info("MQTT connected to %s:%s, subscribed to '%s' (qos=%s)",
                 self._cfg.host, self._cfg.port, self._cfg.tag_topic, self._cfg.qos)

    def _on_disconnect(self, _client, _userdata, rc, *_args) -> None:
        self._connected.clear()
        if rc != 0:
            ops.warning("MQTT disconnected unexpectedly (rc=%s); auto-reconnecting", rc)
        else:
            ops.info("MQTT disconnected")

    def _on_message(self, _client, _userdata, message) -> None:
        try:
            self._on_tag_message(message.topic, message.payload)
        except Exception as exc:
            # A malformed payload must never kill the callback thread.
            ops.exception("Tag message handler failed: %s", exc)

    # -- lifecycle ---------------------------------------------------------- #

    def start(self) -> None:
        ops.info("MQTT connecting to %s:%s as '%s'",
                 self._cfg.host, self._cfg.port, self._cfg.client_id)
        self._client.connect_async(self._cfg.host, self._cfg.port,
                                   keepalive=self._cfg.keepalive_s)
        self._client.loop_start()

    def wait_connected(self, timeout: float) -> bool:
        return self._connected.wait(timeout)

    def is_connected(self) -> bool:
        return self._connected.is_set()

    def publish(self, topic: str, payload: str, qos: int = 1) -> None:
        info = self._client.publish(topic, payload, qos=qos)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            raise OSError(f"MQTT publish to '{topic}' failed (rc={info.rc})")

    def stop(self) -> None:
        try:
            self._client.loop_stop()
            self._client.disconnect()
        except Exception:
            pass
