"""StoreLense REST client.

Every endpoint here already exists in the backend:

  POST /api/auth/login                        -> accessToken, refreshToken, expiresIn
  POST /api/auth/refresh                      -> rotates both tokens
  GET  /api/gate/checks/bills                 -> paged BillSummaryDto (no items)
  GET  /api/gate/checks/bills/{billRef}       -> BillLookupResponse (items w/ ean)
  GET  /api/inventory/epc-by-ean/{ean}        -> in-store EPCs for that EAN
  GET  /api/inventory/identify-epc/{epc}      -> product + statusInStore
  POST /api/gate/checks                       -> audit record

Note on roles: GET /bills is restricted to ADMIN and STORE_MANAGER. A
SECURITY_GUARD account will get 403 here. Use a dedicated STORE_MANAGER
service account.

Responses are wrapped in {success, code, message, data, timestamp}; this client
unwraps `data` and raises on `success: false`.
"""

from __future__ import annotations

import threading
import time
from typing import Any
from urllib.parse import quote

import requests

from .logs import ops


class ApiError(Exception):
    """Any non-success response or transport failure."""

    def __init__(self, message: str, status: int | None = None):
        super().__init__(message)
        self.status = status


class AuthError(ApiError):
    """Credentials or role problem -- retrying will not help."""


class StoreLenseApi:
    def __init__(self, cfg):
        self._cfg = cfg
        self._base = cfg.base_url.rstrip("/")
        self._session = requests.Session()
        self._session.verify = cfg.verify_tls
        self._lock = threading.Lock()
        self._access: str | None = None
        self._refresh_token: str | None = None
        self._expires_at: float = 0.0
        self.role: str | None = None
        self.token_store_id: str | None = None

    # -- auth --------------------------------------------------------------- #

    def login(self) -> None:
        data = self._raw_post(
            "/api/auth/login",
            {"username": self._cfg.username, "password": self._cfg.password},
            authed=False,
        )
        self._apply_tokens(data)
        self.role = data.get("role")
        self.token_store_id = data.get("storeId")
        ops.info(
            "Authenticated as %s (role=%s, storeId=%s), token valid %ss",
            data.get("username"), self.role, self.token_store_id, data.get("expiresIn"),
        )
        if self.role not in ("ADMIN", "STORE_MANAGER"):
            ops.warning(
                "Account role is %s. GET /api/gate/checks/bills requires ADMIN or "
                "STORE_MANAGER -- bill sync will fail with 403.", self.role,
            )

    def _apply_tokens(self, data: dict) -> None:
        with self._lock:
            self._access = data["accessToken"]
            self._refresh_token = data.get("refreshToken")
            expires_in = float(data.get("expiresIn") or 3600)
            self._expires_at = time.time() + expires_in

    def ensure_token(self) -> None:
        """Refresh proactively.

        Reactive refresh-on-401 would abort a half-built snapshot mid-cycle,
        so the margin matters more than it looks.
        """
        with self._lock:
            fresh = self._access and time.time() < self._expires_at - self._cfg.refresh_margin_s
            refresh_token = self._refresh_token
        if fresh:
            return
        if refresh_token:
            try:
                data = self._raw_post(
                    "/api/auth/refresh", {"refreshToken": refresh_token}, authed=False
                )
                self._apply_tokens(data)
                ops.debug("Access token refreshed")
                return
            except ApiError as exc:
                ops.warning("Token refresh failed (%s); falling back to full login", exc)
        self.login()

    # -- transport ---------------------------------------------------------- #

    def _headers(self) -> dict:
        with self._lock:
            token = self._access
        return {"Authorization": f"Bearer {token}"} if token else {}

    def _unwrap(self, resp: requests.Response) -> Any:
        if resp.status_code in (401, 403):
            raise AuthError(
                f"{resp.status_code} on {resp.request.method} {resp.request.url}: "
                f"{resp.text[:300]}",
                resp.status_code,
            )
        try:
            body = resp.json()
        except ValueError:
            raise ApiError(
                f"non-JSON response ({resp.status_code}) from {resp.url}: "
                f"{resp.text[:200]}",
                resp.status_code,
            ) from None
        if isinstance(body, dict) and body.get("success") is False:
            raise ApiError(
                f"{body.get('code')}: {body.get('message')}", resp.status_code
            )
        if not resp.ok:
            raise ApiError(f"HTTP {resp.status_code} from {resp.url}", resp.status_code)
        return body.get("data") if isinstance(body, dict) else body

    def _raw_post(self, path: str, payload: dict, authed: bool = True) -> Any:
        headers = {"Content-Type": "application/json"}
        if authed:
            headers.update(self._headers())
        try:
            resp = self._session.post(
                self._base + path, json=payload, headers=headers,
                timeout=self._cfg.timeout_s,
            )
        except requests.RequestException as exc:
            raise ApiError(f"POST {path} failed: {exc}") from exc
        return self._unwrap(resp)

    # nginx returns these when an upstream service is momentarily unavailable.
    # Measured ~40% on this deployment, so a single attempt is not enough to
    # build a complete allowlist.
    _RETRY_STATUS = (502, 503, 504)
    # At a measured ~40% failure rate, 3 attempts still leaves a ~40% chance
    # that at least one of 8 EANs fails outright. 5 brings that under 10%.
    _RETRIES = 5
    _BACKOFF_S = 0.2

    def _get(self, path: str, params: dict | None = None) -> Any:
        self.ensure_token()
        last: Exception | None = None
        for attempt in range(self._RETRIES):
            if attempt:
                time.sleep(self._BACKOFF_S * (2 ** (attempt - 1)))
            try:
                resp = self._session.get(
                    self._base + path, params=params, headers=self._headers(),
                    timeout=self._cfg.timeout_s,
                )
            except requests.RequestException as exc:
                last = ApiError(f"GET {path} failed: {exc}")
                continue
            if resp.status_code in self._RETRY_STATUS:
                last = ApiError(
                    f"HTTP {resp.status_code} (upstream unavailable) from {path}",
                    resp.status_code,
                )
                continue
            return self._unwrap(resp)
        assert last is not None
        raise last

    # -- endpoints ---------------------------------------------------------- #

    def list_pending_bills(self, store_id: str, since_iso: str,
                           page_size: int = 200) -> list[dict]:
        """All bills with gate_checked_at IS NULL created since `since_iso`.

        The time bound is not optional. pendingOnly keys off gate_checked_at,
        and nothing in an unattended-portal flow ever sets it, so without a
        window the list grows without limit and eventually allowlists the store.
        """
        out: list[dict] = []
        page = 0
        while True:
            data = self._get("/api/gate/checks/bills", {
                "storeId": store_id,
                "pendingOnly": "true",
                "from": since_iso,
                "page": page,
                "size": page_size,
            })
            items = (data or {}).get("content") or (data or {}).get("items") or []
            out.extend(items)
            total_pages = (data or {}).get("totalPages")
            page += 1
            if not items or total_pages is None or page >= total_pages:
                break
            if page > 50:  # hard stop; something is wrong upstream
                ops.warning("Bill pagination exceeded 50 pages; truncating")
                break
        return out

    def lookup_bill(self, bill_ref: str, store_id: str) -> dict:
        return self._get(
            f"/api/gate/checks/bills/{quote(bill_ref, safe='')}",
            {"storeId": store_id},
        )

    def epcs_by_ean(self, ean: str, store_id: str) -> dict:
        """Returns only EPCs with status='in_store' for that store."""
        return self._get(
            f"/api/inventory/epc-by-ean/{quote(ean, safe='')}",
            {"storeId": store_id},
        )

    def identify_epc(self, epc: str, store_id: str) -> dict | None:
        """404 means the EPC was never registered -- a foreign tag."""
        try:
            return self._get(
                f"/api/inventory/identify-epc/{quote(epc, safe='')}",
                {"storeId": store_id},
            )
        except ApiError as exc:
            if exc.status == 404:
                return None
            raise

    def record_gate_check(self, store_id: str, outcome: str,
                          expected: int, matched: int, extra: int,
                          epcs_matched: list[str], epcs_extra: list[str]) -> None:
        """billRef stays null on purpose.

        An unattended portal cannot know which bill a tag belongs to, and a
        non-null billRef makes the backend stamp gate_checked_at on that bill,
        which would drop it out of pendingOnly while the customer is still
        walking to the door.
        """
        self.ensure_token()
        self._raw_post("/api/gate/checks", {
            "storeId": store_id,
            "billRef": None,
            "expectedCount": expected,
            "matchedCount": matched,
            "extraCount": extra,
            "outcome": outcome,
            "epcsMatched": epcs_matched,
            "epcsExtra": epcs_extra,
        })
