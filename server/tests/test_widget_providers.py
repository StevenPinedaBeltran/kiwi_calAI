"""Future maintainer regression tests for public weather and sports endpoints."""
import asyncio
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import httpx
import pytest

from app.sports import Sports, parse_event, pick_game
from app.weather import Weather, normalize


def _game(game_id, stamp, state):
    return {
        "id": game_id,
        "date": stamp.isoformat(),
        "status": {"type": {"state": state, "shortDetail": state}},
        "competitions": [{"competitors": [
            {"homeAway": "home", "team": {"id": "3", "abbreviation": "CHI"}},
            {"homeAway": "away", "team": {"id": "9", "abbreviation": "GB"}},
        ]}],
    }


def test_schedule_fallback_picks_upcoming_game_and_deduplicates():
    asyncio.run(_check_schedule_fallback())


async def _check_schedule_fallback():
    zone = ZoneInfo("America/Chicago")
    now = datetime.now(zone)
    past = _game("past", now - timedelta(days=3), "post")
    upcoming = _game("next", now + timedelta(days=3), "pre")
    calls = []

    def fake(request):
        calls.append(request.url.path)
        if request.url.path.endswith("/scoreboard"):
            return httpx.Response(200, json={"events": [past]})
        if request.url.path.endswith("/schedule"):
            return httpx.Response(200, json={"events": [past, upcoming]})
        return httpx.Response(404)

    async with httpx.AsyncClient(transport=httpx.MockTransport(fake)) as client:
        provider = Sports(client, zone)
        games = await provider.scores([("nfl", "3"), ("nfl", "9")])
        assert len(games) == 1 and games[0]["id"] == "next"
        assert pick_game([parse_event("nfl", past), parse_event("nfl", upcoming)], now.date(), zone)["id"] == "next"
        await provider.scores([("nfl", "3")])
        assert sum(p.endswith("/scoreboard") for p in calls) == 1


def test_scores_provider_outage_is_not_empty_success():
    asyncio.run(_check_provider_outage())


async def _check_provider_outage():
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda _: httpx.Response(503))) as client:
        provider = Sports(client, ZoneInfo("America/Chicago"))
        with pytest.raises(httpx.HTTPError):
            await provider.scores([("nfl", "3")])


def test_forecast_dst_future_days_and_absent_air_quality():
    def hourly_range():
        first = datetime(2026, 10, 31, 8)
        return [(first + timedelta(hours=i)).isoformat(timespec="minutes") for i in range(48)]
    report = {
        "timezone": "America/Chicago", "utc_offset_seconds": -18000,
        "current": {"time": "2026-10-31T08:00", "weather_code": 2, "is_day": 1},
        "hourly": {"time": hourly_range(), "temperature_2m": [12] * 48,
                   "weather_code": [2] * 48, "is_day": [1] * 48,
                   "precipitation_probability": [10] * 48},
        "daily": {"time": ["2026-10-31", "2026-11-01"], "weather_code": [2, 61],
                  "temperature_2m_max": [12, 11], "temperature_2m_min": [8, 5],
                  "sunrise": ["2026-10-31T07:00", "2026-11-01T07:00"],
                  "sunset": ["2026-10-31T18:00", "2026-11-01T17:00"],
                  "uv_index_max": [2, 3], "precipitation_probability_max": [25, 65],
                  "precipitation_sum": [1, 2], "wind_speed_10m_max": [5, 6]},
    }
    data = normalize(report, None, "metric")
    assert data["daily"][0]["sunset"].endswith("-05:00")
    assert data["daily"][1]["sunset"].endswith("-06:00")
    assert data["air"] is None
    assert len(data["hourly"]) == 24
