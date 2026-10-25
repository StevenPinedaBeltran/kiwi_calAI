import hashlib
from datetime import date, datetime, time, timedelta, tzinfo
from typing import Any

from .db import Database
from .models import EventIn

LOCAL = "local"


def _aware(value: datetime, tz: tzinfo) -> datetime:
    return value.replace(tzinfo=tz) if value.tzinfo is None else value


def _day_ts(day: date, tz: tzinfo) -> int:
    return int(datetime.combine(day, time(), tz).timestamp())


def normalize(event: EventIn, tz: tzinfo) -> dict[str, Any]:
    """Validate start/end and compute the columns stored for an event."""
    if event.all_day:
        start = date.fromisoformat(event.start[:10])
        end = date.fromisoformat(event.end[:10]) if event.end else start + timedelta(days=1)
        if end <= start:
            raise ValueError("An all-day event must end after the day it starts")
        return {
            "dtstart": start.isoformat(),
            "dtend": end.isoformat(),
            "all_day": 1,
            "start_ts": _day_ts(start, tz),
            "end_ts": _day_ts(end, tz),
        }
    start_dt = _aware(datetime.fromisoformat(event.start), tz)
    end_dt = _aware(datetime.fromisoformat(event.end), tz) if event.end else start_dt + timedelta(hours=1)
    if end_dt < start_dt:
        raise ValueError("The event ends before it starts")
    return {
        "dtstart": start_dt.isoformat(timespec="seconds"),
        "dtend": end_dt.isoformat(timespec="seconds"),
        "all_day": 0,
        "start_ts": int(start_dt.timestamp()),
        "end_ts": int(end_dt.timestamp()),
    }


def upsert_local(db: Database, event_id: str, event: EventIn, tz: tzinfo) -> dict[str, Any]:
    columns = normalize(event, tz)
    with db.tx() as c:
        row = c.execute("SELECT source FROM events WHERE id = ?", (event_id,)).fetchone()
        if row and row["source"] != LOCAL:
            raise PermissionError("Events from subscribed calendars can't be edited here")
        c.execute(
            "INSERT OR REPLACE INTO events "
            "(id, source, title, dtstart, dtend, all_day, start_ts, end_ts, location, notes, color) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                event_id,
                LOCAL,
                event.title.strip(),
                columns["dtstart"],
                columns["dtend"],
                columns["all_day"],
                columns["start_ts"],
                columns["end_ts"],
                (event.location or "").strip() or None,
                (event.notes or "").strip() or None,
                event.color,
            ),
        )
        db.bump(c)
    return get_event(db, event_id)


def delete_local(db: Database, event_id: str) -> bool:
    with db.tx() as c:
        row = c.execute("SELECT source FROM events WHERE id = ?", (event_id,)).fetchone()
        if not row:
            return False
        if row["source"] != LOCAL:
            raise PermissionError("Events from subscribed calendars can't be deleted here")
        c.execute("DELETE FROM events WHERE id = ?", (event_id,))
        db.bump(c)
    return True


_SELECT = (
    "SELECT e.*, f.color AS feed_color, f.name AS feed_name "
    "FROM events e LEFT JOIN feeds f ON f.id = e.source "
)


def _to_api(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": row["id"],
        "title": row["title"],
        "start": row["dtstart"],
        "end": row["dtend"],
        "all_day": bool(row["all_day"]),
        "location": row["location"],
        "notes": row["notes"],
        "color": row["color"] or row["feed_color"],
        "source": row["source"],
        "calendar": row["feed_name"],
    }


def get_event(db: Database, event_id: str) -> dict[str, Any]:
    rows = db.query(_SELECT + "WHERE e.id = ?", (event_id,))
    return _to_api(rows[0])


def events_between(db: Database, start_ts: int, end_ts: int) -> list[dict[str, Any]]:
    rows = db.query(
        _SELECT + "WHERE e.start_ts < ? AND e.end_ts >= ? ORDER BY e.start_ts, e.title",
        (end_ts, start_ts),
    )
    return [_to_api(r) for r in rows]


def replace_source(db: Database, source: str, rows: list[dict[str, Any]]) -> bool:
    """Swap every event from one feed for a fresh set. Returns True if anything changed."""

    def digest(items: list[dict[str, Any]]) -> str:
        keyed = sorted(
            (r["id"], r["title"], r["dtstart"], r["dtend"], r["location"] or "", r["notes"] or "")
            for r in items
        )
        return hashlib.sha1(repr(keyed).encode()).hexdigest()

    existing = db.query(
        "SELECT id, title, dtstart, dtend, location, notes FROM events WHERE source = ?", (source,)
    )
    if digest(existing) == digest(rows):
        return False
    with db.tx() as c:
        c.execute("DELETE FROM events WHERE source = ?", (source,))
        c.executemany(
            "INSERT OR REPLACE INTO events "
            "(id, source, title, dtstart, dtend, all_day, start_ts, end_ts, location, notes, color) "
            "VALUES (:id, :source, :title, :dtstart, :dtend, :all_day, :start_ts, :end_ts, "
            ":location, :notes, NULL)",
            [{**r, "source": source} for r in rows],
        )
        db.bump(c)
    return True
