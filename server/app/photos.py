import asyncio
import hashlib
import io
import logging
import re
import time
from pathlib import Path
from typing import Any

import httpx
from PIL import Image, ImageOps

from .config import Settings
from .db import Database

log = logging.getLogger("kiwi.photos")

# Public shared-album pages embed each photo as ["https://lh3.googleusercontent.com/pw/...",width,height
# This is not an official API and may break if Google changes the page.
ALBUM_ITEM = re.compile(r'\["(https://lh3\.googleusercontent\.com/pw/[A-Za-z0-9_\-]+)",(\d+),(\d+)')
ALBUM_HOSTS = ("https://photos.app.goo.gl/", "https://photos.google.com/share/", "https://goo.gl/photos/")


def photo_id(source: str, key: str) -> str:
    return hashlib.sha1(f"{source}|{key}".encode()).hexdigest()[:20]


def save_image(data: bytes, dest: Path, max_edge: int) -> tuple[int, int]:
    with Image.open(io.BytesIO(data)) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
    image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
    tmp = dest.with_suffix(".tmp")
    image.save(tmp, "JPEG", quality=85, progressive=True)
    tmp.replace(dest)
    return image.size


class PhotoLibrary:
    def __init__(self, db: Database, settings: Settings, client: httpx.AsyncClient):
        self.db = db
        self.settings = settings
        self.client = client
        self.dir = settings.data_dir / "photos"
        self.dir.mkdir(parents=True, exist_ok=True)
        self._album_lock = asyncio.Lock()

    def path(self, pid: str) -> Path:
        return self.dir / f"{pid}.jpg"

    def all(self) -> list[dict[str, Any]]:
        return self.db.query("SELECT id, source, width, height FROM photos ORDER BY added")

    async def add(self, pid: str, source: str, data: bytes) -> None:
        width, height = await asyncio.to_thread(save_image, data, self.path(pid), self.settings.photo_max_edge)
        with self.db.tx() as c:
            c.execute(
                "INSERT OR REPLACE INTO photos (id, source, width, height, added) VALUES (?, ?, ?, ?, ?)",
                (pid, source, width, height, int(time.time())),
            )

    def remove(self, ids: set[str]) -> None:
        if not ids:
            return
        with self.db.tx() as c:
            c.executemany("DELETE FROM photos WHERE id = ?", [(i,) for i in ids])
            self.db.bump(c)
        for pid in ids:
            self.path(pid).unlink(missing_ok=True)

    def remove_source(self, source: str) -> int:
        ids = {r["id"] for r in self.db.query("SELECT id FROM photos WHERE source = ?", (source,))}
        self.remove(ids)
        return len(ids)

    def album_status(self) -> dict[str, Any]:
        count = self.db.query("SELECT COUNT(*) AS n FROM photos WHERE source = 'album'")[0]["n"]
        last = self.db.get("album_last_sync")
        return {
            "url": self.db.get("album_url"),
            "last_sync": int(last) if last else None,
            "error": self.db.get("album_error"),
            "count": count,
            "refreshing": self._album_lock.locked(),
        }

    def set_album(self, url: str) -> None:
        url = url.strip()
        if not url.startswith(ALBUM_HOSTS):
            raise ValueError("Use the share link from Google Photos (it starts with https://photos.app.goo.gl/)")
        self.db.put("album_url", url)
        self.db.delete("album_error")

    def clear_album(self) -> None:
        self.db.delete("album_url")
        self.db.delete("album_error")
        self.db.delete("album_last_sync")
        self.remove_source("album")

    async def fetch_album_items(self, url: str) -> list[str]:
        response = await self.client.get(url)
        response.raise_for_status()
        seen: dict[str, None] = {}
        for match in ALBUM_ITEM.finditer(response.text):
            seen.setdefault(match.group(1), None)
        return list(seen)

    async def refresh_album(self) -> None:
        url = self.db.get("album_url")
        if not url:
            return
        async with self._album_lock:
            try:
                items = await self.fetch_album_items(url)
                if not items:
                    raise ValueError("No photos found. Check that link sharing is turned on for the album.")
                wanted = {photo_id("album", u): u for u in items[: self.settings.max_photos]}
                existing = {r["id"] for r in self.db.query("SELECT id FROM photos WHERE source = 'album'")}
                edge = self.settings.photo_max_edge
                added = failed = 0
                for pid, item_url in wanted.items():
                    if pid in existing:
                        continue
                    try:
                        response = await self.client.get(f"{item_url}=w{edge}-h{edge}")
                        response.raise_for_status()
                        await self.add(pid, "album", response.content)
                        added += 1
                    except Exception as exc:  # noqa: BLE001 - skip a broken photo, keep the rest
                        failed += 1
                        log.warning("Album photo failed: %s", exc)
                stale = existing - wanted.keys()
                self.remove(stale)
                if added and not stale:
                    self.db.bump_revision()
                self.db.put("album_last_sync", str(int(time.time())))
                self.db.delete("album_error")
                log.info("Album: %d new, %d removed, %d failed, %d total", added, len(stale), failed, len(wanted))
            except Exception as exc:  # noqa: BLE001
                log.warning("Album refresh failed: %s", exc)
                self.db.put("album_error", str(exc)[:300])
