from .conftest import AIR_DOWN, AUTH


def test_weather_is_normalized(client):
    data = client.get("/api/weather?lat=42.07&lon=-87.94&units=imperial", headers=AUTH).json()
    current = data["current"]
    assert current["condition"] == "Partly cloudy"
    assert current["time"] == "2026-10-25T14:15-05:00"
    assert current["visibility"] == 10.0  # 52,800 ft
    assert current["pressure"] == 30.01  # inHg
    assert data["labels"]["temp"] == "°F"

    assert len(data["hourly"]) == 24
    assert data["hourly"][0]["time"] == "2026-10-25T14:00-05:00"

    tomorrow = data["daily"][1]
    assert tomorrow["condition"] == "Light rain"
    assert tomorrow["sunset"] == "2026-10-26T17:55-05:00"
    assert data["air"]["us_aqi"] == 42


def test_weather_survives_air_quality_outage(client):
    AIR_DOWN["enabled"] = True
    try:
        data = client.get("/api/weather?lat=10&lon=10&units=metric", headers=AUTH).json()
    finally:
        AIR_DOWN["enabled"] = False
    assert data["air"] is None
    assert data["labels"]["pressure"] == "hPa"
    assert data["current"]["pressure"] == 1016


def test_weather_validates_input(client):
    assert client.get("/api/weather?lat=100&lon=0", headers=AUTH).status_code == 422
    assert client.get("/api/weather?lat=1&lon=1&units=kelvin", headers=AUTH).status_code == 422
    assert client.get("/api/weather?lat=1&lon=1").status_code == 401


def test_place_search(client):
    places = client.get("/api/weather/search?q=mount prospect", headers=AUTH).json()
    assert places == [{"name": "Mount Prospect", "region": "Illinois, United States", "latitude": 42.0664, "longitude": -87.9373}]
