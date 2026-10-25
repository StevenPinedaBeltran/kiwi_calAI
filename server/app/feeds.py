import asyncio
import hashlib
import logging
import secrets
import time
from datetime import date, datetime, timedelta, tzinfo
from typing import Any

import httpx
import icalendar
import recurring_ical_events

from . import events
from .db import Database
from .models import FeedIn

log = logging.getLogger("kiwi.feeds")

PAST_DAYS = 31
FUTURE_DAYS = 400


def _http_url(url: str) -> str:
    url = url.strip()
    if url.startswith("webcal://"):
        return "https://" + url[len("webcal://") :]
    return url


def expand(data: bytes, feed_id: str, tz: tzinfo, today: date | None = None) -> list[dict[str, Any]]:
    """Turn an iCalendar file into concrete event rows, with recurrences expanded."""
    calendar = icalendar.Calendar.from_ical(data)
    today = today or datetime.now(tz).date()
    window = (today - timedelta(days=PAST_DAYS), today + timedelta(days=FUTURE_DAYS))
    rows: dict[str, dict[str, Any]] = {}

    for component in recurring_ical_events.of(calendar).between(*window):
        if str(component.get("STATUS", "")).upper() == "CANCELLED":
            continue
        start = component["DTSTART"].dt
        all_day = isinstance(start, date) and not isinstance(start, datetime)

        if component.get("DTEND") is not None:
            end = component["DTEND"].dt
        elif component.get("DURATION") is not None:
            end = start + component["DURATION"].dt
        else:
            end = start + timedelta(days=1) if all_day else start

        if all_day:
            if isinstance(end, datetime):
                end = end.date()
            if end <= start:
                end = start + timedelta(days=1)
            dtstart, dtend = start.isoformat(), end.isoformat()
            start_ts = int(datetime.combine(start, datetime.min.time(), tz).timestamp())
            end_ts = int(datetime.combine(end, datetime.min.time(), tz).timestamp())
        else:
            if start.tzinfo is None:
                start = start.replace(tzinfo=tz)
            if not isinstance(end, datetime):
                end = datetime.combine(end, datetime.min.time(), tz)
            if end.tzinfo is None:
                end = end.replace(tzinfo=tz)
            dtstart = start.isoformat(timespec="seconds")
            dtend = end.isoformat(timespec="seconds")
            start_ts, end_ts = int(start.timestamp()), int(end.timestamp())

        uid = str(component.get("UID", "")) or f"{dtstart}-{component.get('SUMMARY', '')}"
        event_id = hashlib.sha1(f"{feed_id}|{uid}|{dtstart}".encode()).hexdigest()[:24]
        rows[event_id] = {
            "id": event_id,
            "title": str(component.get("SUMMARY", "")).strip() or "(No title)",
            "dtstart": dtstart,
            "dtend": dtend,
            "all_day": int(all_day),
            "start_ts": start_ts,
            "end_ts": end_ts,
            "location": str(component.get("LOCATION", "")).strip() or None,
            "notes": str(component.get("DESCRIPTION", "")).strip()[:4000] or None,
        }
    return list(rows.values())


class Feeds:
    def __init__(self, db: Database, client: httpx.AsyncClient, tz: tzinfo):
        self.db = db
        self.client = client
        self.tz = tz

    def all(self) -> list[dict[str, Any]]:
        return self.db.query("SELECT * FROM feeds ORDER BY name")

    def add(self, feed: FeedIn) -> dict[str, Any]:
        feed_id = "feed-" + secrets.token_hex(4)
        with self.db.tx() as c:
            c.execute(
                "INSERT INTO feeds (id, name, url, color) VALUES (?, ?, ?, ?)",
                (feed_id, feed.name.strip(), feed.url.strip(), feed.color),
            )
        return self.db.query("SELECT * FROM feeds WHERE id = ?", (feed_id,))[0]

    def remove(self, feed_id: str) -> bool:
        with self.db.tx() as c:
            deleted = c.execute("DELETE FROM feeds WHERE id = ?", (feed_id,)).rowcount
            c.execute("DELETE FROM events WHERE source = ?", (feed_id,))
            if deleted:
                self.db.bump(c)
        return bool(deleted)

    async def refresh(self, feed: dict[str, Any]) -> None:
        try:
            response = await self.client.get(_http_url(feed["url"]))
            response.raise_for_status()
            rows = await asyncio.to_thread(expand, response.content, feed["id"], self.tz)
        except Exception as exc:  # noqa: BLE001 - any failure is reported on the feed itself
            log.warning("Feed %s failed: %s", feed["name"], exc)
            with self.db.tx() as c:
                c.execute("UPDATE feeds SET last_error = ? WHERE id = ?", (str(exc)[:300], feed["id"]))
            return
        changed = events.replace_source(self.db, feed["id"], rows)
        with self.db.tx() as c:
            c.execute(
                "UPDATE feeds SET last_sync = ?, last_error = NULL WHERE id = ?",
                (int(time.time()), feed["id"]),
            )
        log.info("Feed %s: %d events%s", feed["name"], len(rows), "" if changed else " (unchanged)")

    async def refresh_all(self) -> None:
        for feed in self.all():
            await self.refresh(feed)
