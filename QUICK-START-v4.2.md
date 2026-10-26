# Kiwi Calendar v4.2 — Complete project

This archive contains the **entire source tree**, not a patch: Docker/server code, static web dashboard, Android Gradle project, fonts/assets, tests, documentation, and GitHub Actions workflows. It is **not** an APK or a prebuilt Docker image.

## Windows Docker Desktop
1. Extract this archive; open PowerShell in the inner `kiwi-calendar` folder.
2. Start Docker Desktop.
3. Run `docker compose up -d --build --force-recreate`.
4. Open http://localhost:8088 and get your app access token with `docker compose logs kiwi --tail=60`.

If this is an **upgrade**, use the same Compose project name as before, e.g. `docker compose -p YOUR_OLD_PROJECT up -d --build --force-recreate`. The named `kiwi-data` volume stores your settings, photos, and token. Do not use `docker compose down -v`. Back up your data volume before upgrading.

You may need an internet connection to fetch Docker's public `python:3.12-slim` image and Python packages. No GitHub Container Registry token is needed.

## Android APK
1. Upload the whole project to your GitHub repository on a feature branch.
2. Run the `Android` workflow under GitHub Actions.
3. Download the generated APK artifact, then install via `adb install -r PATH_TO_APK`.

An app already installed on your DF-18T can update without losing its local data **only if the signing certificate matches**. If it does not match, back up first and reinstall with the correct signing setup. See README.

## Connect tablet
Set the Android app's server address to `http://YOUR_WINDOWS_LAN_IP:8088` (never `localhost` on the tablet) and enter the Kiwi app access token. Use `ipconfig` to find your Windows LAN IPv4.

## Included features
- v4.2 sports scoreboards with validated logos/fallbacks, live/final states, rolling 7-day result filtering.
- v4.2 weather conditions and hourly forecast cards.
- Existing calendar, iCal, photos, sleep display, light/dark mode, and sync features.

## Checks / limits
- Python and JS compilation/syntax and targeted widget tests can be run from `server/`.
- The full server pytest suite could not be executed in the packaging environment because `icalendar` was unavailable.
- The Android and Docker builds have not been executed here. Run GitHub Actions and Docker locally for full validation.
