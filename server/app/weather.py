"""Weather from Open-Meteo: current conditions, 24 hours, 7 days, and air quality.

Open-Meteo needs no API key and is free for non-commercial use. Responses are cached
briefly so several tablets in one house share a single request.
"""

import asyncio
import time
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from typing import Any

import httpx

FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"

CURRENT_FIELDS = (
    "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,"
    "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,visibility,dew_point_2m"
)
HOURLY_FIELDS = "temperature_2m,weather_code,is_day,precipitation_probability"
DAILY_FIELDS = (
    "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,"
    "precipitation_probability_max,precipitation_sum,wind_speed_10m_max"
)

UNITS = {
    "imperial": {
        "params": {"temperature_unit": "fahrenheit", "wind_speed_unit": "mph", "precipitation_unit": "inch"},
        "labels": {"temp": "°F", "speed": "mph", "precip": "in", "distance": "mi", "pressure": "inHg"},
    },
    "metric": {
        "params": {"temperature_unit": "celsius", "wind_speed_unit": "kmh", "precipitation_unit": "mm"},
        "labels": {"temp": "°C", "speed": "km/h", "precip": "mm", "distance": "km", "pressure": "hPa"},
    },
}

# WMO weather interpretation codes, as used by Open-Meteo.
CONDITIONS = {
    0: "Clear", 1: "Mostly clear", 2: "Partly cloudy", 3: "Cloudy",
    45: "Fog", 48: "Freezing fog",
    51: "Light drizzle", 53: "Drizzle", 55: "Heavy drizzle", 56: "Freezing drizzle", 57: "Freezing drizzle",
    61: "Light rain", 63: "Rain", 65: "Heavy rain", 66: "Freezing rain", 67: "Freezing rain",
    71: "Light snow", 73: "Snow", 75: "Heavy snow", 77: "Snow grains",
    80: "Light showers", 81: "Showers", 82: "Heavy showers", 85: "Snow showers", 86: "Heavy snow showers",
    95: "Thunderstorms", 96: "Thunderstorms with hail", 99: "Thunderstorms with hail",
}

CACHE_SECONDS = 600


def condition(code: int | None) -> str:
    return CONDITIONS.get(code if code is not None else -1, "Unknown")


def _local(stamp: str, offset: timezone) -> datetime:
    return datetime.fromisoformat(stamp).replace(tzinfo=offset)


def _iso(stamp: str, offset: timezone) -> str:
    return _local(stamp, offset).isoformat(timespec="minutes")


def _code(value: Any) -> int | None:
    return None if value is None else int(value)


def _round(value: Any, digits: int = 1) -> float | None:
    return None if value is None else round(float(value), digits)


def _visibility(value: Any, unit: str | None, system: str) -> float | None:
    if value is None:
        return None
    meters = float(value) * (0.3048 if unit == "ft" else 1.0)
    return round(meters / 1609.344, 1) if system == "imperial" else round(meters / 1000, 1)


def _pressure(hpa: Any, system: str) -> float | None:
    if hpa is None:
        return None
    return round(float(hpa) * 0.0295300, 2) if system == "imperial" else round(float(hpa))


def normalize(forecast: dict[str, Any], air: dict[str, Any] | None, system: str) -> dict[str, Any]:
    offset = timezone(timedelta(seconds=int(forecast.get("utc_offset_seconds", 0))))
    # The API reports a current UTC offset, but sunrise/forecast hours may cross
    # daylight-saving time. Use the actual timezone when the provider supplies one.
    try:
        if forecast.get("timezone"):
            offset = ZoneInfo(forecast["timezone"])
    except ZoneInfoNotFoundError:
        pass
    cur = forecast["current"]
    units = forecast.get("current_units", {})

    hourly_raw = forecast.get("hourly", {})
    now_hour = _local(cur["time"], offset).replace(minute=0)
    hourly = []
    for i, stamp in enumerate(hourly_raw.get("time", [])):
        when = _local(stamp, offset)
        if when < now_hour:
            continue
        hourly.append({
            "time": when.isoformat(timespec="minutes"),
            "temp": _round(hourly_raw["temperature_2m"][i]),
            "code": _code(hourly_raw["weather_code"][i]),
            "is_day": bool(hourly_raw["is_day"][i]),
            "precip_prob": hourly_raw["precipitation_probability"][i],
        })
        if len(hourly) == 24:
            break

    daily_raw = forecast.get("daily", {})
    daily = []
    for i, day in enumerate(daily_raw.get("time", [])):
        daily.append({
            "date": day,
            "code": _code(daily_raw["weather_code"][i]),
            "condition": condition(daily_raw["weather_code"][i]),
            "high": _round(daily_raw["temperature_2m_max"][i]),
            "low": _round(daily_raw["temperature_2m_min"][i]),
            "precip_prob": daily_raw["precipitation_probability_max"][i],
            "precip_sum": _round(daily_raw["precipitation_sum"][i], 2),
            "sunrise": _iso(daily_raw["sunrise"][i], offset),
            "sunset": _iso(daily_raw["sunset"][i], offset),
            "uv_max": _round(daily_raw["uv_index_max"][i]),
            "wind_max": _round(daily_raw["wind_speed_10m_max"][i]),
        })

    air_now = (air or {}).get("current") or {}
    return {
        "units": system,
        "labels": UNITS[system]["labels"],
        "updated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "current": {
            "time": _iso(cur["time"], offset),
            "temp": _round(cur.get("temperature_2m")),
            "feels_like": _round(cur.get("apparent_temperature")),
            "humidity": cur.get("relative_humidity_2m"),
            "dew_point": _round(cur.get("dew_point_2m")),
            "precip": _round(cur.get("precipitation"), 2),
            "code": _code(cur.get("weather_code")),
            "condition": condition(cur.get("weather_code")),
            "is_day": bool(cur.get("is_day", 1)),
            "cloud_cover": cur.get("cloud_cover"),
            "pressure": _pressure(cur.get("pressure_msl"), system),
            "wind_speed": _round(cur.get("wind_speed_10m")),
            "wind_gusts": _round(cur.get("wind_gusts_10m")),
            "wind_dir": cur.get("wind_direction_10m"),
            "uv": _round(cur.get("uv_index")),
            "visibility": _visibility(cur.get("visibility"), units.get("visibility"), system),
        },
        "hourly": hourly,
        "daily": daily,
        "air": {
            "us_aqi": air_now.get("us_aqi"),
            "european_aqi": air_now.get("european_aqi"),
            "pm2_5": _round(air_now.get("pm2_5")),
            "pm10": _round(air_now.get("pm10")),
        } if air_now else None,
    }


class Weather:
    def __init__(self, client: httpx.AsyncClient):
        self.client = client
        self._cache: dict[tuple, tuple[float, dict[str, Any]]] = {}

    async def forecast(self, lat: float, lon: float, system: str) -> dict[str, Any]:
        key = (round(lat, 2), round(lon, 2), system)
        hit = self._cache.get(key)
        if hit and hit[0] > time.monotonic():
            return hit[1]

        common = {"latitude": key[0], "longitude": key[1], "timezone": "auto"}
        forecast_request = self.client.get(
            FORECAST_URL,
            params={
                **common,
                **UNITS[system]["params"],
                "current": CURRENT_FIELDS,
                "hourly": HOURLY_FIELDS,
                "daily": DAILY_FIELDS,
                "forecast_days": 7,
            },
        )
        air_request = self.client.get(AIR_URL, params={**common, "current": "us_aqi,european_aqi,pm2_5,pm10"})
        forecast_response, air_response = await asyncio.gather(forecast_request, air_request, return_exceptions=True)
        if isinstance(forecast_response, Exception):
            raise forecast_response
        forecast_response.raise_for_status()

        air = None
        if isinstance(air_response, httpx.Response) and air_response.status_code == 200:
            air = air_response.json()

        data = normalize(forecast_response.json(), air, system)
        data["latitude"], data["longitude"] = key[0], key[1]
        self._cache[key] = (time.monotonic() + CACHE_SECONDS, data)
        return data

    async def search(self, query: str) -> list[dict[str, Any]]:
        response = await self.client.get(
            GEOCODE_URL, params={"name": query, "count": 8, "language": "en", "format": "json"}
        )
        response.raise_for_status()
        places = []
        for item in response.json().get("results") or []:
            region = ", ".join(part for part in (item.get("admin1"), item.get("country")) if part)
            places.append({
                "name": item.get("name", ""),
                "region": region,
                "latitude": item["latitude"],
                "longitude": item["longitude"],
            })
        return places
