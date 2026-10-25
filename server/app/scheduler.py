import asyncio
import logging
from datetime import datetime, time, timedelta, tzinfo

log = logging.getLogger("kiwi.scheduler")


def seconds_until(hhmm: str, tz: tzinfo, now: datetime | None = None) -> float:
    hour, minute = (int(part) for part in hhmm.split(":", 1))
    now = now or datetime.now(tz)
    target = datetime.combine(now.date(), time(hour, minute), tz)
    if target <= now:
        target += timedelta(days=1)
    return (target - now).total_seconds()


async def daily(job, hhmm: str, tz: tzinfo) -> None:
    """Run `job` once at startup and then every day at `hhmm` local time."""
    while True:
        try:
            await job()
        except Exception:  # noqa: BLE001 - keep the loop alive no matter what
            log.exception("Daily refresh failed")
        delay = seconds_until(hhmm, tz)
        log.info("Next refresh in %.1f hours", delay / 3600)
        await asyncio.sleep(delay)
