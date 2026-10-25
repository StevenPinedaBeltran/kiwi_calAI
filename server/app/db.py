import json
import sqlite3
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    title TEXT NOT NULL,
    dtstart TEXT NOT NULL,
    dtend TEXT NOT NULL,
    all_day INTEGER NOT NULL,
    start_ts INTEGER NOT NULL,
    end_ts INTEGER NOT NULL,
    location TEXT,
    notes TEXT,
    color TEXT
);
CREATE INDEX IF NOT EXISTS events_range ON events (start_ts, end_ts);
CREATE INDEX IF NOT EXISTS events_source ON events (source);

CREATE TABLE IF NOT EXISTS feeds (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    color TEXT NOT NULL,
    last_sync INTEGER,
    last_error TEXT
);

CREATE TABLE IF NOT EXISTS photos (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    added INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kv (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


class Database:
    """Thin wrapper around a single SQLite connection shared across threads."""

    def __init__(self, path: Path):
        self._conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        with self._lock:
            self._conn.execute("PRAGMA journal_mode=WAL")
            self._conn.executescript(SCHEMA)

    @contextmanager
    def tx(self) -> Iterator[sqlite3.Connection]:
        with self._lock:
            self._conn.execute("BEGIN")
            try:
                yield self._conn
            except BaseException:
                self._conn.execute("ROLLBACK")
                raise
            self._conn.execute("COMMIT")

    def query(self, sql: str, params: tuple = ()) -> list[dict[str, Any]]:
        with self._lock:
            return [dict(row) for row in self._conn.execute(sql, params)]

    def get(self, key: str, default: str | None = None) -> str | None:
        rows = self.query("SELECT value FROM kv WHERE key = ?", (key,))
        return rows[0]["value"] if rows else default

    def put(self, key: str, value: str) -> None:
        with self.tx() as c:
            c.execute(
                "INSERT INTO kv (key, value) VALUES (?, ?) "
                "ON CONFLICT (key) DO UPDATE SET value = excluded.value",
                (key, value),
            )

    def delete(self, key: str) -> None:
        with self.tx() as c:
            c.execute("DELETE FROM kv WHERE key = ?", (key,))

    def get_json(self, key: str) -> Any:
        raw = self.get(key)
        return json.loads(raw) if raw else None

    def put_json(self, key: str, value: Any) -> None:
        self.put(key, json.dumps(value))

    def revision(self) -> int:
        return int(self.get("revision", "0"))

    @staticmethod
    def bump(conn: sqlite3.Connection) -> None:
        conn.execute(
            "INSERT INTO kv (key, value) VALUES ('revision', '1') "
            "ON CONFLICT (key) DO UPDATE SET value = CAST(value AS INTEGER) + 1"
        )

    def bump_revision(self) -> None:
        with self.tx() as c:
            self.bump(c)
