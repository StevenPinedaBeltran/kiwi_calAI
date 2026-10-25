import time
from datetime import date, datetime
from zoneinfo import ZoneInfo

from app.feeds import expand
from app.scheduler import seconds_until
from app.sports import pick_game

from .conftest import AUTH, ICS


def test_health_is_public(client):
    assert client.get("/api/health").json() == {"ok": True}


def test_api_requires_token(client):
    assert client.get("/api/sync").status_code == 401
    assert client.get("/api/sync", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_web_page_is_served(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "Add event" in response.text


def test_event_lifecycle_bumps_revision(client):
    before = client.get("/api/revision", headers=AUTH).json()["revision"]
    created = client.post(
        "/api/events",
        headers=AUTH,
        json={"title": "Dentist", "start": "2026-10-26T15:00:00-05:00", "end": "2026-10-26T16:00:00-05:00"},
    )
    assert created.status_code == 201
    event = created.json()
    assert event["source"] == "local"
    assert client.get("/api/revision", headers=AUTH).json()["revision"] == before + 1

    updated = client.put(
        f"/api/events/{event['id']}", headers=AUTH,
        json={"title": "Dentist (moved)", "start": "2026-10-27", "all_day": True},
    ).json()
    assert updated["all_day"] is True
    assert updated["end"] == "2026-10-28"

    assert client.delete(f"/api/events/{event['id']}", headers=AUTH).status_code == 204
    assert client.delete(f"/api/events/{event['id']}", headers=AUTH).status_code == 404


def test_client_chosen_ids_make_offline_edits_idempotent(client):
    body = {"title": "Bake sale", "start": "2026-11-02T09:00:00-06:00"}
    first = client.put("/api/events/abc-123", headers=AUTH, json=body).json()
    second = client.put("/api/events/abc-123", headers=AUTH, json=body).json()
    assert first == second
    assert first["end"] == "2026-11-02T10:00:00-06:00"


def test_rejects_backwards_event(client):
    response = client.post(
        "/api/events", headers=AUTH,
        json={"title": "Bad", "start": "2026-10-26T15:00:00-05:00", "end": "2026-10-26T14:00:00-05:00"},
    )
    assert response.status_code == 422


def test_naive_times_use_server_zone(client):
    event = client.post("/api/events", headers=AUTH, json={"title": "Lunch", "start": "2026-07-01T12:00"}).json()
    assert event["start"] == "2026-07-01T12:00:00-05:00"


def test_expand_handles_recurrence_all_day_and_cancelled():
    tz = ZoneInfo("America/Chicago")
    rows = expand(ICS, "feed-x", tz, today=date(2026, 10, 25))
    titles = sorted(r["title"] for r in rows)
    assert titles.count("Piano lesson") == 4
    assert "Gone" not in titles
    trip = next(r for r in rows if r["title"] == "Lake trip")
    assert (trip["dtstart"], trip["dtend"], trip["all_day"]) == ("2026-10-30", "2026-11-02", 1)
    piano = sorted(r["dtstart"] for r in rows if r["title"] == "Piano lesson")
    assert piano[0] == "2026-10-05T18:30:00-05:00"


def test_feed_events_show_up_in_sync_and_are_read_only(client):
    feed = client.post(
        "/api/feeds", headers=AUTH,
        json={"name": "School", "url": "webcal://calendar.example/feed.ics", "color": "#3b6ea8"},
    ).json()
    assert feed["last_error"] is None

    sync = client.get("/api/sync", headers=AUTH).json()
    from_feed = [e for e in sync["events"] if e["source"] == feed["id"]]
    assert from_feed and all(e["color"] == "#3b6ea8" for e in from_feed)
    assert all(e["calendar"] == "School" for e in from_feed)

    target = from_feed[0]["id"]
    assert client.delete(f"/api/events/{target}", headers=AUTH).status_code == 403

    revision = sync["revision"]
    client.post("/api/feeds/refresh", headers=AUTH)
    assert client.get("/api/revision", headers=AUTH).json()["revision"] == revision

    assert client.delete(f"/api/feeds/{feed['id']}", headers=AUTH).status_code == 204
    sync = client.get("/api/sync", headers=AUTH).json()
    assert not [e for e in sync["events"] if e["source"] == feed["id"]]


def test_album_rejects_non_google_links(client):
    response = client.put("/api/photos/album", headers=AUTH, json={"url": "https://example.com/album"})
    assert response.status_code == 422


def test_album_download_resizes_and_serves_photos(client, settings):
    response = client.put("/api/photos/album", headers=AUTH, json={"url": "https://photos.app.goo.gl/album"})
    assert response.status_code == 200
    for _ in range(50):
        photos = client.get("/api/photos", headers=AUTH).json()
        if not photos["album"]["refreshing"] and photos["photos"]:
            break
        time.sleep(0.05)
    assert photos["album"]["error"] is None
    assert len(photos["photos"]) == 2
    first = photos["photos"][0]
    assert max(first["width"], first["height"]) == settings.photo_max_edge

    image = client.get(f"/api/photos/{first['id']}", headers=AUTH)
    assert image.status_code == 200
    assert image.headers["content-type"] == "image/jpeg"
    assert client.get("/api/photos/../../etc", headers=AUTH).status_code == 404

    client.delete("/api/photos/album", headers=AUTH)
    assert client.get("/api/photos", headers=AUTH).json()["photos"] == []


def test_google_needs_configuration(client):
    status = client.get("/api/google", headers=AUTH).json()
    assert status["configured"] is False
    assert client.post("/api/google/connect", headers=AUTH).status_code == 409


def test_teams_and_scores(client):
    teams = client.get("/api/sports/teams?league=nfl", headers=AUTH).json()
    assert [t["abbr"] for t in teams] == ["CHI", "GB"]
    assert teams[0]["color"] == "#0b162a"
    assert client.get("/api/sports/teams?league=cricket", headers=AUTH).status_code == 404

    games = client.get("/api/scores?teams=nfl:3,nfl:9", headers=AUTH).json()
    assert len(games) == 1
    game = games[0]
    assert game["state"] == "in"
    assert game["home"]["score"] == "21"
    assert game["start"] == "2026-10-25T18:00:00+00:00"


def test_pick_game_prefers_live_then_today_then_latest_final():
    tz = ZoneInfo("America/Chicago")
    today = date(2026, 10, 25)

    def game(state, start):
        return {"state": state, "start": start}

    final = game("post", "2026-10-24T01:00:00+00:00")
    upcoming = game("pre", "2026-10-26T23:00:00+00:00")
    tonight = game("pre", "2026-10-26T00:30:00+00:00")
    live = game("in", "2026-10-25T18:00:00+00:00")
    assert pick_game([final, upcoming, tonight, live], today, tz) is live
    assert pick_game([final, upcoming, tonight], today, tz) is tonight
    assert pick_game([final, upcoming], today, tz) is final
    assert pick_game([upcoming], today, tz) is upcoming


def test_seconds_until_rolls_to_tomorrow():
    tz = ZoneInfo("America/Chicago")
    now = datetime(2026, 10, 25, 4, 0, tzinfo=tz)
    assert seconds_until("03:00", tz, now) == 23 * 3600
    assert seconds_until("05:30", tz, now) == 5400
