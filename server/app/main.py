import asyncio
import hmac
import logging
import re
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, time, timedelta
from pathlib import Path

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from . import events
from .config import Settings
from .db import Database
from .feeds import FUTURE_DAYS, PAST_DAYS, Feeds
from .google import GooglePhotos, NotConnected
from .models import AlbumIn, EventIn, FeedIn
from .photos import PhotoLibrary
from .scheduler import daily
from .sports import LEAGUES, Sports

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("kiwi")

STATIC = Path(__file__).parent / "static"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
EVENT_ID = re.compile(r"^[A-Za-z0-9_\-]{1,64}$")
PHOTO_ID = re.compile(r"^[a-f0-9]{1,40}$")


def create_app(settings: Settings | None = None, transport: httpx.AsyncBaseTransport | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    db = Database(settings.data_dir / "kiwi.db")
    client = httpx.AsyncClient(
        timeout=30, follow_redirects=True, headers={"User-Agent": USER_AGENT}, transport=transport
    )
    feeds = Feeds(db, client, settings.tz)
    library = PhotoLibrary(db, settings, client)
    google = GooglePhotos(db, settings, client, library)
    sports = Sports(client, settings.tz)
    background: set[asyncio.Task] = set()

    def spawn(coro) -> None:
        task = asyncio.create_task(coro)
        background.add(task)
        task.add_done_callback(background.discard)

    async def refresh_everything() -> None:
        await feeds.refresh_all()
        await library.refresh_album()

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        log.info("Access token: %s", settings.token)
        if settings.run_scheduler:
            spawn(daily(refresh_everything, settings.sync_time, settings.tz))
        yield
        for task in list(background):
            task.cancel()
        await client.aclose()

    app = FastAPI(title="Kiwi Calendar", lifespan=lifespan, docs_url=None, redoc_url=None)

    def authorized(authorization: str = Header(default="")) -> None:
        supplied = authorization.removeprefix("Bearer ").strip()
        if not hmac.compare_digest(supplied.encode(), settings.token.encode()):
            raise HTTPException(401, "Invalid access token")

    auth = [Depends(authorized)]

    def window(days_back: int, days_ahead: int) -> tuple[int, int]:
        today = datetime.now(settings.tz).date()
        start = datetime.combine(today - timedelta(days=days_back), time(), settings.tz)
        end = datetime.combine(today + timedelta(days=days_ahead), time(), settings.tz)
        return int(start.timestamp()), int(end.timestamp())

    @app.get("/api/health")
    def health() -> dict:
        return {"ok": True}

    @app.get("/api/revision", dependencies=auth)
    def revision() -> dict:
        return {"revision": db.revision()}

    @app.get("/api/sync", dependencies=auth)
    def sync() -> dict:
        return {
            "revision": db.revision(),
            "server_time": datetime.now(settings.tz).isoformat(timespec="seconds"),
            "events": events.events_between(db, *window(PAST_DAYS, FUTURE_DAYS)),
            "photos": library.all(),
        }

    # Events

    @app.get("/api/events", dependencies=auth)
    def list_events(days: int = Query(default=60, ge=1, le=FUTURE_DAYS)) -> list[dict]:
        return events.events_between(db, *window(0, days))

    def save_event(event_id: str, event: EventIn) -> dict:
        if not EVENT_ID.match(event_id):
            raise HTTPException(400, "Invalid event id")
        try:
            return events.upsert_local(db, event_id, event, settings.tz)
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
        except PermissionError as exc:
            raise HTTPException(403, str(exc)) from exc

    @app.post("/api/events", dependencies=auth, status_code=201)
    def create_event(event: EventIn) -> dict:
        return save_event(uuid.uuid4().hex, event)

    @app.put("/api/events/{event_id}", dependencies=auth)
    def put_event(event_id: str, event: EventIn) -> dict:
        return save_event(event_id, event)

    @app.delete("/api/events/{event_id}", dependencies=auth, status_code=204)
    def delete_event(event_id: str) -> Response:
        try:
            found = events.delete_local(db, event_id)
        except PermissionError as exc:
            raise HTTPException(403, str(exc)) from exc
        if not found:
            raise HTTPException(404, "No such event")
        return Response(status_code=204)

    # Subscribed calendars

    @app.get("/api/feeds", dependencies=auth)
    def list_feeds() -> list[dict]:
        return feeds.all()

    @app.post("/api/feeds", dependencies=auth, status_code=201)
    async def add_feed(feed: FeedIn) -> dict:
        created = feeds.add(feed)
        await feeds.refresh(created)
        return next(f for f in feeds.all() if f["id"] == created["id"])

    @app.delete("/api/feeds/{feed_id}", dependencies=auth, status_code=204)
    def delete_feed(feed_id: str) -> Response:
        if not feeds.remove(feed_id):
            raise HTTPException(404, "No such calendar")
        return Response(status_code=204)

    @app.post("/api/feeds/refresh", dependencies=auth)
    async def refresh_feeds() -> list[dict]:
        await feeds.refresh_all()
        return feeds.all()

    # Photos

    @app.get("/api/photos", dependencies=auth)
    def list_photos() -> dict:
        return {"photos": library.all(), "album": library.album_status()}

    @app.get("/api/photos/{pid}", dependencies=auth)
    def photo_file(pid: str) -> FileResponse:
        path = library.path(pid)
        if not PHOTO_ID.match(pid) or not path.exists():
            raise HTTPException(404, "No such photo")
        return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "private, max-age=31536000"})

    @app.put("/api/photos/album", dependencies=auth)
    async def set_album(body: AlbumIn) -> dict:
        try:
            library.set_album(body.url)
        except ValueError as exc:
            raise HTTPException(422, str(exc)) from exc
        spawn(library.refresh_album())
        return library.album_status() | {"refreshing": True}

    @app.post("/api/photos/album/refresh", dependencies=auth)
    async def refresh_album() -> dict:
        spawn(library.refresh_album())
        return library.album_status() | {"refreshing": True}

    @app.delete("/api/photos/album", dependencies=auth, status_code=204)
    def clear_album() -> Response:
        library.clear_album()
        return Response(status_code=204)

    @app.delete("/api/photos/picked", dependencies=auth)
    def clear_picked() -> dict:
        return {"removed": library.remove_source("picker")}

    # Google Photos picker

    @app.get("/api/google", dependencies=auth)
    def google_status() -> dict:
        return google.status()

    @app.post("/api/google/connect", dependencies=auth)
    async def google_connect() -> dict:
        try:
            return await google.connect()
        except NotConnected as exc:
            raise HTTPException(409, str(exc)) from exc

    @app.post("/api/google/pick", dependencies=auth)
    async def google_pick() -> dict:
        try:
            return await google.start_pick()
        except NotConnected as exc:
            raise HTTPException(409, str(exc)) from exc
        except httpx.HTTPError as exc:
            raise HTTPException(502, f"Google Photos request failed: {exc}") from exc

    @app.post("/api/google/disconnect", dependencies=auth)
    def google_disconnect() -> dict:
        google.disconnect()
        return google.status()

    # Sports

    @app.get("/api/sports/leagues", dependencies=auth)
    def leagues() -> list[dict]:
        return Sports.leagues()

    @app.get("/api/sports/teams", dependencies=auth)
    async def teams(league: str) -> list[dict]:
        if league not in LEAGUES:
            raise HTTPException(404, "Unknown league")
        try:
            return await sports.teams(league)
        except httpx.HTTPError as exc:
            raise HTTPException(502, f"Couldn't load teams: {exc}") from exc

    @app.get("/api/scores", dependencies=auth)
    async def scores(teams: str = "") -> list[dict]:
        refs = []
        for item in teams.split(","):
            league, _, team_id = item.strip().partition(":")
            if league and team_id:
                refs.append((league, team_id))
        return await sports.scores(refs[:40])

    app.mount("/", StaticFiles(directory=STATIC, html=True), name="web")
    return app

