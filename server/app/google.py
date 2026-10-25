"""Google Photos Picker API, authorized with the OAuth device flow.

The device flow ("TVs and Limited Input devices" client type) needs no redirect URL,
so it works for a server that is only reachable on the local network.
"""

import asyncio
import logging
import time
from typing import Any

import httpx

from .config import Settings
from .db import Database
from .photos import PhotoLibrary, photo_id

log = logging.getLogger("kiwi.google")

DEVICE_URL = "https://oauth2.googleapis.com/device/code"
TOKEN_URL = "https://oauth2.googleapis.com/token"
PICKER_API = "https://photospicker.googleapis.com/v1"
SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
TOKEN_KEY = "google_token"


class NotConnected(Exception):
    pass


def _seconds(duration: str | None, default: float) -> float:
    try:
        return float(str(duration).rstrip("s"))
    except (TypeError, ValueError):
        return default


class GooglePhotos:
    def __init__(self, db: Database, settings: Settings, client: httpx.AsyncClient, library: PhotoLibrary):
        self.db = db
        self.settings = settings
        self.client = client
        self.library = library
        self.auth: dict[str, Any] | None = None
        self.auth_error: str | None = None
        self.pick: dict[str, Any] = {"state": "idle"}
        self._tasks: set[asyncio.Task] = set()

    @property
    def configured(self) -> bool:
        return bool(self.settings.google_client_id and self.settings.google_client_secret)

    def status(self) -> dict[str, Any]:
        picked = self.db.query("SELECT COUNT(*) AS n FROM photos WHERE source = 'picker'")[0]["n"]
        return {
            "configured": self.configured,
            "connected": self.db.get(TOKEN_KEY) is not None,
            "auth": self.auth,
            "auth_error": self.auth_error,
            "pick": self.pick,
            "picked_count": picked,
        }

    def _spawn(self, coro) -> None:
        task = asyncio.create_task(coro)
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    async def connect(self) -> dict[str, Any]:
        if not self.configured:
            raise NotConnected("Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET on the server first")
        response = await self.client.post(
            DEVICE_URL, data={"client_id": self.settings.google_client_id, "scope": SCOPE}
        )
        body = response.json()
        if response.status_code != 200:
            raise NotConnected(body.get("error_description") or body.get("error") or "Google rejected the request")
        self.auth_error = None
        self.auth = {
            "user_code": body["user_code"],
            "verification_url": body.get("verification_url") or body.get("verification_uri"),
            "expires_at": int(time.time()) + int(body.get("expires_in", 1800)),
        }
        self._spawn(self._wait_for_device(body["device_code"], int(body.get("interval", 5)), int(body.get("expires_in", 1800))))
        return self.status()

    async def _wait_for_device(self, device_code: str, interval: int, expires_in: int) -> None:
        deadline = time.monotonic() + expires_in
        try:
            while time.monotonic() < deadline:
                await asyncio.sleep(interval)
                response = await self.client.post(
                    TOKEN_URL,
                    data={
                        "client_id": self.settings.google_client_id,
                        "client_secret": self.settings.google_client_secret,
                        "device_code": device_code,
                        "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
                    },
                )
                body = response.json()
                if response.status_code == 200:
                    self._store_token(body)
                    log.info("Google account connected")
                    return
                error = body.get("error")
                if error == "authorization_pending":
                    continue
                if error == "slow_down":
                    interval += 5
                    continue
                self.auth_error = body.get("error_description") or error or "Authorization failed"
                return
            self.auth_error = "The code expired. Start again."
        except Exception as exc:  # noqa: BLE001
            log.warning("Device authorization failed: %s", exc)
            self.auth_error = str(exc)[:300]
        finally:
            self.auth = None

    def _store_token(self, body: dict[str, Any]) -> None:
        current = self.db.get_json(TOKEN_KEY) or {}
        self.db.put_json(
            TOKEN_KEY,
            {
                "access_token": body["access_token"],
                "refresh_token": body.get("refresh_token") or current.get("refresh_token"),
                "expires_at": int(time.time()) + int(body.get("expires_in", 3600)),
            },
        )

    async def _access_token(self) -> str:
        token = self.db.get_json(TOKEN_KEY)
        if not token:
            raise NotConnected("Connect a Google account first")
        if token["expires_at"] > time.time() + 60:
            return token["access_token"]
        response = await self.client.post(
            TOKEN_URL,
            data={
                "client_id": self.settings.google_client_id,
                "client_secret": self.settings.google_client_secret,
                "refresh_token": token.get("refresh_token") or "",
                "grant_type": "refresh_token",
            },
        )
        if response.status_code != 200:
            self.db.delete(TOKEN_KEY)
            raise NotConnected("Google access was revoked. Connect the account again.")
        self._store_token(response.json())
        return self.db.get_json(TOKEN_KEY)["access_token"]

    def disconnect(self) -> None:
        self.db.delete(TOKEN_KEY)
        self.pick = {"state": "idle"}

    async def start_pick(self) -> dict[str, Any]:
        token = await self._access_token()
        response = await self.client.post(
            f"{PICKER_API}/sessions", headers={"Authorization": f"Bearer {token}"}, json={}
        )
        response.raise_for_status()
        session = response.json()
        polling = session.get("pollingConfig", {})
        self.pick = {"state": "waiting", "picker_uri": session["pickerUri"], "done": 0, "total": 0, "error": None}
        self._spawn(
            self._wait_for_pick(
                session["id"],
                _seconds(polling.get("pollInterval"), 5.0),
                _seconds(polling.get("timeoutIn"), 1800.0),
            )
        )
        return self.status()

    async def _wait_for_pick(self, session_id: str, interval: float, timeout: float) -> None:
        deadline = time.monotonic() + timeout
        try:
            while time.monotonic() < deadline:
                await asyncio.sleep(max(interval, 2.0))
                token = await self._access_token()
                response = await self.client.get(
                    f"{PICKER_API}/sessions/{session_id}", headers={"Authorization": f"Bearer {token}"}
                )
                response.raise_for_status()
                if response.json().get("mediaItemsSet"):
                    await self._download_session(session_id)
                    return
            self.pick = {"state": "expired", "error": "Picking timed out. Start again."}
        except Exception as exc:  # noqa: BLE001
            log.warning("Picker session failed: %s", exc)
            self.pick = {"state": "error", "error": str(exc)[:300]}
        finally:
            try:
                token = await self._access_token()
                await self.client.delete(
                    f"{PICKER_API}/sessions/{session_id}", headers={"Authorization": f"Bearer {token}"}
                )
            except Exception:  # noqa: BLE001 - cleanup is best effort
                pass

    async def _download_session(self, session_id: str) -> None:
        token = await self._access_token()
        headers = {"Authorization": f"Bearer {token}"}
        items: list[dict[str, Any]] = []
        page_token = None
        while True:
            params = {"sessionId": session_id, "pageSize": 100}
            if page_token:
                params["pageToken"] = page_token
            response = await self.client.get(f"{PICKER_API}/mediaItems", headers=headers, params=params)
            response.raise_for_status()
            body = response.json()
            items += [i for i in body.get("mediaItems", []) if i.get("type") == "PHOTO"]
            page_token = body.get("nextPageToken")
            if not page_token:
                break

        self.pick = {"state": "downloading", "done": 0, "total": len(items), "error": None}
        edge = self.settings.photo_max_edge
        for item in items:
            base_url = item["mediaFile"]["baseUrl"]
            try:
                response = await self.client.get(f"{base_url}=w{edge}-h{edge}", headers=headers)
                response.raise_for_status()
                await self.library.add(photo_id("picker", item["id"]), "picker", response.content)
            except Exception as exc:  # noqa: BLE001
                log.warning("Picked photo failed: %s", exc)
            self.pick["done"] += 1
        if items:
            self.db.bump_revision()
        self.pick = {"state": "done", "done": len(items), "total": len(items), "error": None}
