# Kiwi Calendar — Recent scores update (v4.1)

The sports widget now displays **live games or the latest completed game within the previous 7 × 24 hours** for each followed team. It does not show upcoming fixtures or completed scores older than a week. When a followed team has no eligible result, no score card appears for that team.

- Docker API filters to recent scores and checks team schedules if a scoreboard lacks them.
- Docker web dashboard filters server results and saved/cached scores, and displays “No games played in the past 7 days” when empty.
- Android sleep screen filters cached scores with a clock that updates every minute.
- No changes to weather, photo sync, calendar data, the token, or Docker volume.

## Update Windows Docker

Extract the ZIP and, in its `kiwi-calendar` folder, run:

```powershell
docker compose up -d --build --force-recreate
```

Open http://localhost:8088 and refresh the browser (Ctrl+F5). Preserve the existing Compose project name/volume.

## Update Android

Commit the updated Android source to the same GitHub branch, build a release APK through the repository Actions workflow, and install the APK on the tablet. Upgrades must use the original signing key; do not uninstall unless you intend to clear the app's local data.

## Tests

Recent-score test cases cover last-7-day window, older scores, future fixtures, live games, ESPN schedule fallback, deduplication and provider outages. Provider tests use mocked HTTP responses; live ESPN connectivity and installation on the physical tablet still need verification.
