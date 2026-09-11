"""Live local dashboard.

A single-page status view served by gate-guard itself on
http://<host>:<port>/ -- no extra dependency, no separate process. The page
polls /api/status once a second and renders it; the server does no work
between polls beyond assembling a small JSON object from in-memory state
already tracked elsewhere (snapshot, decider counters, MQTT/GPO state, the
event-log ring buffer). It never touches the hot path.

Intended for a phone or laptop on the same LAN while standing at the portal
during setup and testing, and as an always-on "is this thing alive" page
afterwards. It is not authenticated -- bind it to localhost or the store LAN
only, never expose it to the internet.
"""

from __future__ import annotations

import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .logs import ops

_PAGE = """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>gate-guard</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 0;
         background: #0b0f14; color: #e6edf3; }
  header { padding: 16px 20px; border-bottom: 1px solid #1f2937;
           display: flex; align-items: center; gap: 12px; }
  header h1 { font-size: 18px; margin: 0; font-weight: 600; }
  .dot { width: 10px; height: 10px; border-radius: 50%; background: #6b7280;
         flex-shrink: 0; }
  .dot.ok { background: #22c55e; }
  .dot.bad { background: #ef4444; }
  .dot.warn { background: #eab308; }
  main { padding: 20px; max-width: 1000px; margin: 0 auto; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
          gap: 12px; margin-bottom: 24px; }
  .card { background: #111827; border: 1px solid #1f2937; border-radius: 10px;
          padding: 14px 16px; }
  .card .label { font-size: 12px; color: #9ca3af; text-transform: uppercase;
                 letter-spacing: .04em; margin-bottom: 6px; }
  .card .value { font-size: 24px; font-weight: 600; }
  .card .sub { font-size: 12px; color: #6b7280; margin-top: 4px; }
  .alarm-banner { display: none; background: #ef4444; color: #fff;
                  padding: 14px 18px; border-radius: 10px; margin-bottom: 20px;
                  font-weight: 600; font-size: 16px; animation: pulse 1s infinite; }
  .alarm-banner.show { display: block; }
  @keyframes pulse { 0%,100% { opacity: 1 } 50% { opacity: .6 } }
  h2 { font-size: 14px; color: #9ca3af; text-transform: uppercase;
       letter-spacing: .04em; margin: 24px 0 10px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th { text-align: left; color: #6b7280; font-weight: 500; padding: 6px 10px;
       border-bottom: 1px solid #1f2937; }
  td { padding: 6px 10px; border-bottom: 1px solid #161b22;
       font-family: ui-monospace, Consolas, monospace; }
  tr.ev-ALLOWED td.ev, tr.ev-ALLOWED_REENTRY td.ev { color: #22c55e; }
  tr.ev-UNKNOWN_EPC td.ev, tr.ev-QUOTA_EXCEEDED td.ev { color: #ef4444; font-weight: 600; }
  tr.ev-SUPPRESSED_STALE td.ev { color: #eab308; }
  .stale { color: #eab308; }
  .down { color: #ef4444; }
  footer { text-align: center; color: #4b5563; font-size: 12px; padding: 20px; }
</style></head>
<body>
<header>
  <div class="dot" id="dot-overall"></div>
  <h1>gate-guard</h1>
  <span id="uptime" style="color:#6b7280;font-size:13px;margin-left:auto"></span>
</header>
<main>
  <div class="alarm-banner" id="alarm-banner">ALARM ACTIVE &mdash; unbilled EPC detected at the exit</div>
  <div class="grid">
    <div class="card"><div class="label">MQTT</div>
      <div class="value" id="v-mqtt">-</div>
      <div class="sub" id="s-mqtt">&nbsp;</div></div>
    <div class="card"><div class="label">Allowlist</div>
      <div class="value" id="v-allow">-</div>
      <div class="sub" id="s-allow">&nbsp;</div></div>
    <div class="card"><div class="label">Snapshot age</div>
      <div class="value" id="v-age">-</div>
      <div class="sub" id="s-age">&nbsp;</div></div>
    <div class="card"><div class="label">Alarms today</div>
      <div class="value" id="v-alarms">-</div>
      <div class="sub" id="s-alarms">&nbsp;</div></div>
  </div>
  <h2>Recent activity</h2>
  <table>
    <thead><tr><th>Time</th><th>Event</th><th>EPC</th><th>Product / EAN</th><th>Note</th></tr></thead>
    <tbody id="events"></tbody>
  </table>
</main>
<footer>polling every 1s &middot; gate-guard</footer>
<script>
function fmtAge(s) {
  if (s == null) return '-';
  if (s < 60) return s.toFixed(0) + 's';
  return (s / 60).toFixed(1) + 'm';
}
async function poll() {
  let r;
  try { r = await fetch('/api/status'); } catch (e) { setDown(); return; }
  if (!r.ok) { setDown(); return; }
  const d = await r.json();

  const mqttOk = d.mqtt_connected;
  document.getElementById('v-mqtt').textContent = mqttOk ? 'connected' : 'down';
  document.getElementById('v-mqtt').className = 'value ' + (mqttOk ? '' : 'down');
  document.getElementById('s-mqtt').textContent = d.mqtt_host || '';

  document.getElementById('v-allow').textContent = d.allowlist_size ?? '-';
  document.getElementById('s-allow').textContent = d.bills + ' pending bill(s)';

  const age = d.snapshot_age_s;
  const ageEl = document.getElementById('v-age');
  ageEl.textContent = fmtAge(age);
  ageEl.className = 'value ' + (d.snapshot_ok ? '' : 'stale');
  document.getElementById('s-age').textContent = d.snapshot_ok ? 'healthy' :
    (d.snapshot_error || 'stale');

  document.getElementById('v-alarms').textContent = d.alarm_count ?? 0;
  document.getElementById('s-alarms').textContent = d.allowed_count + ' allowed exits';

  document.getElementById('alarm-banner').classList.toggle('show', !!d.alarm_active);

  const overall = mqttOk && d.snapshot_ok ? 'ok' : (mqttOk || d.snapshot_ok ? 'warn' : 'bad');
  document.getElementById('dot-overall').className = 'dot ' + overall;

  const up = d.uptime_s || 0;
  document.getElementById('uptime').textContent =
    'up ' + Math.floor(up/3600) + 'h ' + Math.floor((up%3600)/60) + 'm';

  const tbody = document.getElementById('events');
  tbody.innerHTML = '';
  (d.recent_events || []).slice().reverse().forEach(ev => {
    const tr = document.createElement('tr');
    tr.className = 'ev-' + ev.event;
    const t = (ev.ts || '').split('T')[1]?.replace('Z','') || '';
    const note = ev.product ? ev.product :
                 (ev.reason ? ev.reason : (ev.error || ''));
    tr.innerHTML = '<td>' + t + '</td><td class="ev">' + ev.event + '</td>' +
      '<td>' + (ev.epc ? ev.epc.slice(0,16) : '') + '</td>' +
      '<td>' + (ev.ean || '') + '</td><td>' + (note || '') + '</td>';
    tbody.appendChild(tr);
  });
}
function setDown() {
  document.getElementById('dot-overall').className = 'dot bad';
  document.getElementById('v-mqtt').textContent = 'unreachable';
}
poll();
setInterval(poll, 1000);
</script>
</body></html>
"""


class DashboardServer:
    def __init__(self, cfg, guard):
        """`guard` is the GateGuard instance -- read its live components."""
        self._cfg = cfg.dashboard
        self._guard = guard
        self._started = time.time()
        self._httpd: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None

    def _status(self) -> dict:
        guard = self._guard
        snap = guard.allowlist.snapshot
        counters = guard.decider.counters
        alarm_count = sum(counters.get(k, 0) for k in ("UNKNOWN_EPC", "QUOTA_EXCEEDED"))
        allowed_count = sum(counters.get(k, 0) for k in ("ALLOWED", "ALLOWED_REENTRY"))
        return {
            "uptime_s": round(time.time() - self._started),
            "mqtt_connected": guard.mqtt.is_connected(),
            "mqtt_host": f"{guard.cfg.mqtt.host}:{guard.cfg.mqtt.port}",
            "snapshot_ok": snap.ok,
            "snapshot_age_s": round(snap.age_s(), 1),
            "snapshot_error": snap.error,
            "allowlist_size": len(snap.allowed_epcs),
            "bills": snap.bill_count,
            "alarm_active": guard.alarm.is_active(),
            "alarm_count": alarm_count,
            "allowed_count": allowed_count,
            "gpo_driver": guard.cfg.gpo.driver,
            "counters": dict(counters),
            "recent_events": guard.events.recent(100),
        }

    def start(self) -> None:
        status_fn = self._status
        cfg = self._cfg

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass  # noisy at INFO; ops.log already covers connections

            def do_GET(self):
                if self.path.startswith("/api/status"):
                    try:
                        body = json.dumps(status_fn(), default=str).encode("utf-8")
                    except Exception as exc:  # dashboard must never crash the app
                        self.send_response(500)
                        self.end_headers()
                        self.wfile.write(str(exc).encode("utf-8"))
                        return
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                elif self.path in ("/", "/index.html"):
                    body = _PAGE.encode("utf-8")
                    self.send_response(200)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                else:
                    self.send_response(404)
                    self.end_headers()

        try:
            self._httpd = ThreadingHTTPServer((cfg.host, cfg.port), Handler)
        except OSError as exc:
            ops.error("Dashboard could not bind %s:%s (%s) -- disabled",
                      cfg.host, cfg.port, exc)
            return

        self._thread = threading.Thread(
            target=self._httpd.serve_forever, name="dashboard", daemon=True
        )
        self._thread.start()
        ops.info("Dashboard listening on http://%s:%s/", cfg.host, cfg.port)

    def stop(self) -> None:
        if self._httpd:
            self._httpd.shutdown()
            self._httpd.server_close()
