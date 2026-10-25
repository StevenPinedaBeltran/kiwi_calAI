import logging
import os
import secrets
from dataclasses import dataclass
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

log = logging.getLogger("kiwi")

TOKEN_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"


@dataclass
class Settings:
    data_dir: Path
    token: str
    tz: ZoneInfo
    sync_time: str = "03:00"
    max_photos: int = 500
    photo_max_edge: int = 1920
    google_client_id: str = ""
    google_client_secret: str = ""
    run_scheduler: bool = True

    @classmethod
    def from_env(cls) -> "Settings":
        data_dir = Path(os.environ.get("KIWI_DATA", "/data"))
        data_dir.mkdir(parents=True, exist_ok=True)
        return cls(
            data_dir=data_dir,
            token=load_token(data_dir),
            tz=load_zone(os.environ.get("TZ") or "UTC"),
            sync_time=os.environ.get("SYNC_TIME", "03:00"),
            max_photos=int(os.environ.get("MAX_PHOTOS", "500")),
            photo_max_edge=int(os.environ.get("PHOTO_MAX_EDGE", "1920")),
            google_client_id=os.environ.get("GOOGLE_CLIENT_ID", "").strip(),
            google_client_secret=os.environ.get("GOOGLE_CLIENT_SECRET", "").strip(),
        )


def load_zone(name: str) -> ZoneInfo:
    try:
        return ZoneInfo(name)
    except (ZoneInfoNotFoundError, ValueError):
        log.warning("Unknown time zone %r, falling back to UTC", name)
        return ZoneInfo("UTC")


def load_token(data_dir: Path) -> str:
    from_env = os.environ.get("KIWI_TOKEN", "").strip()
    if from_env:
        return from_env
    path = data_dir / "token"
    if path.exists():
        return path.read_text().strip()
    raw = "".join(secrets.choice(TOKEN_ALPHABET) for _ in range(16))
    token = "-".join(raw[i : i + 4] for i in range(0, 16, 4))
    path.write_text(token + "\n")
    return token
