"""v4.2 sports logo contract (also runnable standalone without the full server)."""
from datetime import datetime, timedelta, timezone
from app.sports import parse_event, is_recent_score, _logo


def _event(logo, hours=2, state="post"):
    return {
        "id": "109", "date": (datetime.now(timezone.utc)-timedelta(hours=hours)).isoformat(),
        "status": {"type": {"state": state, "shortDetail": "Final"}},
        "competitions": [{"competitors": [
            {"homeAway": "home", "team": {"id": "1", "name": "Team A", "abbreviation": "AAA", "logo": logo}, "score": "3"},
            {"homeAway": "away", "team": {"id": "2", "name": "Team B", "abbreviation": "BBB", "logos": [{"href": "https://a.espncdn.com/i/teamlogos/nba/500/bbb.png"}]}, "score": "2"},
        ]}],
    }


def test_espn_cdn_logos_are_preserved():
    game = parse_event("nba",_event("https://a.espncdn.com/i/teamlogos/nba/500/aaa.png"))
    assert game["home"]["logo"].startswith("https://a.espncdn.com/")
    assert game["away"]["logo"].endswith("bbb.png")
    assert game["home"]["score"] == "3"
    assert is_recent_score(game, datetime.now(timezone.utc))


def test_untrusted_or_http_logo_discarded_and_expired_score_hidden():
    for logo in ["javascript:alert(1)", "http://a.espncdn.com/image.png", "https://espncdn.com.evil.net/a.png"]:
        game=parse_event("nba",_event(logo,hours=200))
        assert game["home"]["logo"] is None
        assert not is_recent_score(game,datetime.now(timezone.utc))
    assert _logo({"logos": [{"href":"https://a.espncdn.com/i.png"}]})


def test_upcoming_games_never_count_as_recent_scores():
    game=parse_event("nba",_event(None, state="pre"))
    assert not is_recent_score(game, datetime.now(timezone.utc))
