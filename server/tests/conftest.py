import io
import json
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import Settings, load_zone
from app.main import create_app

TOKEN = "test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}


def jpeg_bytes(size=(400, 300)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, (40, 120, 80)).save(buf, "JPEG")
    return buf.getvalue()


ICS = b"""BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//test//EN
BEGIN:VEVENT
UID:weekly@test
DTSTART;TZID=America/Chicago:20261005T183000
DTEND;TZID=America/Chicago:20261005T193000
RRULE:FREQ=WEEKLY;COUNT=4
SUMMARY:Piano lesson
END:VEVENT
BEGIN:VEVENT
UID:trip@test
DTSTART;VALUE=DATE:20261030
DTEND;VALUE=DATE:20261102
SUMMARY:Lake trip
END:VEVENT
BEGIN:VEVENT
UID:cancelled@test
DTSTART:20261027T150000Z
DTEND:20261027T160000Z
STATUS:CANCELLED
SUMMARY:Gone
END:VEVENT
END:VCALENDAR
"""

ALBUM_HTML = """<html><script>
data=[["https://lh3.googleusercontent.com/pw/AAA111",1600,1200,null],
["https://lh3.googleusercontent.com/pw/BBB222",1200,1600,null],
["https://lh3.googleusercontent.com/pw/AAA111",1600,1200,null]]
</script></html>"""

SCOREBOARD = {
    "events": [
        {
            "id": "401",
            "date": "2026-10-25T18:00Z",
            "competitions": [
                {
                    "status": {"type": {"state": "in", "shortDetail": "Q3 5:12"}},
                    "competitors": [
                        {"homeAway": "home", "score": "21", "team": {"id": "3", "abbreviation": "CHI", "shortDisplayName": "Bears", "color": "0b162a"}},
                        {"homeAway": "away", "score": "17", "team": {"id": "9", "abbreviation": "GB", "shortDisplayName": "Packers", "color": "204e32"}},
                    ],
                }
            ],
        }
    ]
}

TEAMS = {"sports": [{"leagues": [{"teams": [
    {"team": {"id": "9", "displayName": "Green Bay Packers", "abbreviation": "GB", "color": "204e32"}},
    {"team": {"id": "3", "displayName": "Chicago Bears", "abbreviation": "CHI", "color": "0b162a"}},
]}]}]}


def fake_internet(request: httpx.Request) -> httpx.Response:
    url = str(request.url)
    if url.startswith("https://calendar.example/feed.ics"):
        return httpx.Response(200, content=ICS)
    if url.startswith("https://photos.app.goo.gl/album"):
        return httpx.Response(200, text=ALBUM_HTML)
    if url.startswith("https://lh3.googleusercontent.com/pw/"):
        return httpx.Response(200, content=jpeg_bytes((3000, 2000)))
    if "scoreboard" in url:
        return httpx.Response(200, json=SCOREBOARD)
    if url.endswith("/teams?limit=1000"):
        return httpx.Response(200, json=TEAMS)
    return httpx.Response(404)


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(data_dir=tmp_path, token=TOKEN, tz=load_zone("America/Chicago"), run_scheduler=False)


@pytest.fixture
def client(settings):
    app = create_app(settings, transport=httpx.MockTransport(fake_internet))
    with TestClient(app) as c:
        yield c
