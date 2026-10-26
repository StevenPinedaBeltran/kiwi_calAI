"""Scores from ESPN's public site API.

The endpoints are undocumented but widely used. Everything here is cached briefly
so several tablets polling the server don't hammer ESPN.
"""

import time
from datetime import date, datetime, timedelta, tzinfo
from typing import Any

import httpx

BASE = "https://site.api.espn.com/apis/site/v2/sports"

# id: (display name, ESPN path, extra scoreboard params, uses weekly scoreboard)
LEAGUES: dict[str, tuple[str, str, dict[str, str], bool]] = {
    "nfl": ("NFL", "football/nfl", {}, True),
    "ncaaf": ("College football", "football/college-football", {"groups": "80"}, True),
    "nba": ("NBA", "basketball/nba", {}, False),
    "wnba": ("WNBA", "basketball/wnba", {}, False),
    "ncaam": ("Men's college basketball", "basketball/mens-college-basketball", {"groups": "50"}, False),
    "ncaaw": ("Women's college basketball", "basketball/womens-college-basketball", {"groups": "50"}, False),
    "mlb": ("MLB", "baseball/mlb", {}, False),
    "nhl": ("NHL", "hockey/nhl", {}, False),
    "mls": ("MLS", "soccer/usa.1", {}, False),
    "epl": ("Premier League", "soccer/eng.1", {}, False),
    "laliga": ("La Liga", "soccer/esp.1", {}, False),
    "ucl": ("Champions League", "soccer/uefa.champions", {}, False),
}


def _color(value: str | None) -> str | None:
    if value and len(value) == 6:
        return "#" + value.lower()
    return None


def _score(value: Any) -> str | None:
    if isinstance(value, dict):
        value = value.get("displayValue") or value.get("value")
    return None if value is None else str(value)


def parse_event(league: str, event: dict[str, Any]) -> dict[str, Any]:
    competition = event["competitions"][0]
    status = (competition.get("status") or event.get("status") or {}).get("type", {})
    sides: dict[str, dict[str, Any]] = {}
    for competitor in competition.get("competitors", []):
        team = competitor.get("team", {})
        sides[competitor.get("homeAway", "home")] = {
            "id": str(team.get("id", "")),
            "abbr": team.get("abbreviation", ""),
            "name": team.get("shortDisplayName") or team.get("displayName", ""),
            "color": _color(team.get("color")),
            "score": _score(competitor.get("score")),
        }
    return {
        "id": str(event["id"]),
        "league": league,
        "start": datetime.fromisoformat(event["date"]).isoformat(timespec="seconds"),
        "state": status.get("state", "pre"),
        "detail": status.get("shortDetail") or status.get("detail", ""),
        "home": sides.get("home"),
        "away": sides.get("away"),
    }


def pick_game(games: list[dict[str, Any]], today: date, tz: tzinfo) -> dict[str, Any] | None:
    """Live, then today, then the next seven days, then the most recent final."""

    def rank(game: dict[str, Any]) -> tuple[int, float]:
        start = datetime.fromisoformat(game["start"])
        if game["state"] == "in":
            return (0, 0)
        if start.astimezone(tz).date() == today:
            return (1, start.timestamp())
        if game["state"] == "pre" and 0 <= (start.astimezone(tz).date() - today).days <= 7:
            return (2, start.timestamp())
        if game["state"] == "post":
            return (3, -start.timestamp())
        return (4, start.timestamp())

    return min(games, key=rank) if games else None


class Sports:
    def __init__(self, client: httpx.AsyncClient, tz: tzinfo):
        self.client = client
        self.tz = tz
        self._cache: dict[str, tuple[float, Any]] = {}

    async def _get(self, url: str, params: dict[str, str], ttl: float) -> Any:
        key = url + "?" + "&".join(f"{k}={v}" for k, v in sorted(params.items()))
        hit = self._cache.get(key)
        if hit and hit[0] > time.monotonic():
            return hit[1]
        response = await self.client.get(url, params=params)
        response.raise_for_status()
        data = response.json()
        self._cache[key] = (time.monotonic() + ttl, data)
        return data

    @staticmethod
    def leagues() -> list[dict[str, str]]:
        return [{"id": key, "name": value[0]} for key, value in LEAGUES.items()]

    async def teams(self, league: str) -> list[dict[str, Any]]:
        _, path, _, _ = LEAGUES[league]
        data = await self._get(f"{BASE}/{path}/teams", {"limit": "1000"}, ttl=86400)
        leagues = (data.get("sports") or [{}])[0].get("leagues") or [{}]
        teams = []
        for entry in leagues[0].get("teams", []):
            team = entry.get("team", {})
            teams.append(
                {
                    "id": str(team.get("id", "")),
                    "name": team.get("displayName", ""),
                    "abbr": team.get("abbreviation", ""),
                    "color": _color(team.get("color")),
                }
            )
        return sorted(teams, key=lambda t: t["name"])

    async def scoreboard(self, league: str) -> list[dict[str, Any]]:
        _, path, extra, weekly = LEAGUES[league]
        params = {"limit": "300", **extra}
        if not weekly:
            today = datetime.now(self.tz).date()
            # Today's-only ranges left the family display empty most off-days.
            params["dates"] = f"{today - timedelta(days=3):%Y%m%d}-{today + timedelta(days=14):%Y%m%d}"
        data = await self._get(f"{BASE}/{path}/scoreboard", params, ttl=60)
        games = []
        for event in data.get("events", []):
            try:
                games.append(parse_event(league, event))
            except (KeyError, IndexError, ValueError):
                continue
        return games

    async def team_schedule(self, league: str, team_id: str) -> list[dict[str, Any]]:
        """Fallback for bye weeks, days between games, and leagues with short scoreboards.

        ESPN's site API is unofficial; failures must not turn into fabricated games.
        """
        path = LEAGUES[league][1]
        data = await self._get(
            f"{BASE}/{path}/teams/{team_id}/schedule", {"limit": "500"}, ttl=600
        )
        games = []
        for event in data.get("events") or []:
            try:
                games.append(parse_event(league, event))
            except (KeyError, IndexError, TypeError, ValueError):
                continue
        return games

    async def scores(self, teams: list[tuple[str, str]]) -> list[dict[str, Any]]:
        today = datetime.now(self.tz).date()
        boards: dict[str, list[dict[str, Any]]] = {}
        result: dict[str, dict[str, Any]] = {}
        valid = [(league, team_id) for league, team_id in teams if league in LEAGUES and team_id.isdigit()]
        if not valid:
            return []
        failures = 0
        for league, team_id in valid:
            if league not in boards:
                try:
                    boards[league] = await self.scoreboard(league)
                except (httpx.HTTPError, ValueError, KeyError):
                    boards[league] = []
                    failures += 1
            mine = [
                g for g in boards[league]
                if team_id in {(g["home"] or {}).get("id"), (g["away"] or {}).get("id")}
            ]
            # A weekly scoreboard can omit teams on a bye; a finished game can
            # remain the 'latest' for days. Check the team schedule for the next fixture.
            if not any(g["state"] == "in" or
                       (g["state"] == "pre" and datetime.fromisoformat(g["start"]).astimezone(self.tz).date() >= today)
                       for g in mine):
                try:
                    mine.extend(await self.team_schedule(league, team_id))
                except (httpx.HTTPError, ValueError, KeyError):
                    pass  # The scoreboard is still usable when schedule lookup fails.
            game = pick_game(mine, today, self.tz)
            if game:
                result.setdefault(f"{league}:{game['id']}", game)
        if failures and not result:
            raise httpx.HTTPError("Sports provider is unavailable")
        return sorted(result.values(), key=lambda g: (g["state"] != "in", g["start"]))
