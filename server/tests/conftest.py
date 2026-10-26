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


def _hours(start: str, count: int) -> list[str]:
    from datetime import datetime, timedelta
    first = datetime.fromisoformat(start)
    return [(first + timedelta(hours=i)).strftime("%Y-%m-%dT%H:%M") for i in range(count)]


FORECAST = {
    "utc_offset_seconds": -18000,
    "current_units": {"visibility": "ft"},
    "current": {
        "time": "2026-10-25T14:15", "temperature_2m": 61.4, "relative_humidity_2m": 48,
        "apparent_temperature": 59.9, "is_day": 1, "precipitation": 0.0, "weather_code": 2,
        "cloud_cover": 40, "pressure_msl": 1016.2, "wind_speed_10m": 9.3, "wind_direction_10m": 225,
        "wind_gusts_10m": 17.0, "uv_index": 3.1, "visibility": 52800, "dew_point_2m": 41.0,
    },
    "hourly": {
        "time": _hours("2026-10-25T00:00", 48),
        "temperature_2m": [50 + i % 12 for i in range(48)],
        "weather_code": [2] * 48,
        "is_day": [1 if 7 <= i % 24 < 18 else 0 for i in range(48)],
        "precipitation_probability": [i % 100 for i in range(48)],
    },
    "daily": {
        "time": ["2026-10-25", "2026-10-26"],
        "weather_code": [2, 61],
        "temperature_2m_max": [63.0, 58.2],
        "temperature_2m_min": [47.1, 45.9],
        "sunrise": ["2026-10-25T07:16", "2026-10-26T07:17"],
        "sunset": ["2026-10-25T17:56", "2026-10-26T17:55"],
        "uv_index_max": [3.4, 2.0],
        "precipitation_probability_max": [10, 80],
        "precipitation_sum": [0.0, 0.42],
        "wind_speed_10m_max": [12.1, 18.4],
    },
}

AIR = {"current": {"us_aqi": 42, "european_aqi": 21, "pm2_5": 8.24, "pm10": 12.0}}

GEOCODE = {"results": [
    {"name": "Mount Prospect", "latitude": 42.0664, "longitude": -87.9373, "country": "United States", "admin1": "Illinois"},
]}

AIR_DOWN = {"enabled": False}


def fake_internet(request: httpx.Request) -> httpx.Response:
    url = str(request.url)
    if url.startswith("https://api.open-meteo.com/v1/forecast"):
        return httpx.Response(200, json=FORECAST)
    if url.startswith("https://air-quality-api.open-meteo.com/"):
        if AIR_DOWN["enabled"]:
            return httpx.Response(503)
        return httpx.Response(200, json=AIR)
    if url.startswith("https://geocoding-api.open-meteo.com/"):
        return httpx.Response(200, json=GEOCODE)
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
