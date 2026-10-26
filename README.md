> **Dashboard redesign:** The Docker-hosted web control center now uses a compact dark dashboard with live event, photo, and calendar statistics. For the updated Windows/Docker Desktop install on **port 8088**, read [START-WINDOWS.md](START-WINDOWS.md). The Android-native UI has not been redesigned in this update.

<img src="branding/icon.svg" width="96" alt="">

# Kiwi Calendar

A self-hosted family wall calendar for the inexpensive Android 14 calendar displays sold as kitchen or hallway screens.

The tablet app is a black-and-white dashboard: a month calendar with the day's schedule beside it, a detailed local forecast, and a sidebar for photos and settings. When nobody has touched it for a while it becomes a full-screen photo frame with a large clock, the weather, the next few events, and live scores for the teams you follow. A small server running in Docker refreshes photos and subscribed calendars every night, and hosts a web page your phone opens from a QR code on the calendar so anyone in the house can add events.

The tablet keeps its own copy of everything, so it keeps working if the server or the internet goes down.

```
 Phone or computer ── scans QR code ──► web page ─┐
                                                   ▼
 Google Photos ─┐                         ┌──────────┐   checks every 2 min,    ┌──────────┐
 iCal feeds ────┼── refreshed daily ────► │  server  │ ◄── full sync daily ───── │  tablet  │
 ESPN scores ───┤                         │ (Docker) │                           │   app    │
 Open-Meteo ────┘── weather on request ─► └──────────┘ ◄── weather every 15 min ─ └──────────┘
```

## What's in the repository

| Path | Contents |
| --- | --- |
| `android/` | The tablet app: Kotlin, Jetpack Compose, no Google Play services required |
| `server/` | The server: Python, FastAPI, SQLite, plus the phone web page in `server/app/static` |
| `.github/workflows/` | Builds the APK and publishes the server image on every push |
| `docker-compose.yml` | Runs the server |

## Getting started

### 1. Put the code on GitHub

Create a repository and push this code to it. Two workflows start on their own:

- **Android** builds the APK. It appears under the workflow run's **Artifacts**, and on the **Releases** page when you push a tag such as `v1.0.0`.
- **Server** runs the tests and publishes a Docker image to `ghcr.io/<your-user>/<your-repo>`.

GitHub makes new container images private. Either open the package under your profile's **Packages** tab and set its visibility to public, or run `docker login ghcr.io` on the machine that will host the server.

Before installing builds you intend to keep, set up a signing key (see [Signing](#signing)). Otherwise every build is signed with a different throwaway key, and each update means uninstalling the app and running setup again.

### 2. Start the server

On any always-on machine at home (a Raspberry Pi 4 or newer is plenty), edit `docker-compose.yml`:

- Set `image` to your published image, or replace it with `build: ./server`.
- Set `TZ` to your time zone, for example `America/New_York`.

Then:

```sh
docker compose up -d
docker compose logs kiwi | grep "Access token"
```

The server listens on port 8080 inside the container; the updated Docker Compose file exposes it on Windows host port 8088. Note the machine's local IP address and give it a fixed address in your router's DHCP settings, since the tablet stores it.

### 3. Install the app on the calendar

Download the APK onto the tablet and open it, allowing installs from your browser when Android asks. With a USB cable and developer mode enabled, `adb install kiwi-calendar-*.apk` also works.

Open Kiwi Calendar and follow the setup screens. You'll enter the server address (for example `192.168.1.20:8080`) and the access token, search for your town for the weather, choose teams, add photos from your phone, and decide when photo mode starts.

## Using the calendar

The sidebar on the left switches between the calendar and the weather, starts photo mode, shows the code for adding events from a phone, and opens settings. Tap any day to see its schedule in the panel on the right; the arrows above that panel step through days, and its calendar button jumps back to today. The greeting at the top uses the name you set under **Settings > Display**.

The Android app is strictly black and white; the Docker web dashboard uses dark gray with orange and green accents. Each event's color shows as a shade of gray, and events created on the tablet pick a shade by type: Important, Family, Work, or Personal.

## Adding events

- **On the calendar:** tap **+** above the month, or tap an event you created to edit or delete it.
- **From a phone:** tap **From phone** on the calendar and scan the code. The page remembers the phone afterwards.
- **From a computer:** open `http://<server>:8080` and paste the access token.

Events added elsewhere appear on the calendar within about two minutes. Events created on the tablet while it's offline are queued and sent when the server is reachable again.

To show calendars you already keep elsewhere, add their iCal links on the web page under **Calendars**. For Google Calendar, open Settings on a computer, select the calendar, and copy **Secret address in iCal format**. These events are read-only on the wall; edit them at the source.

## Photos

Google stopped letting third-party apps read albums from a Google Photos library in 2025, so Kiwi Calendar offers the two methods that still work. You can use both at once.

### Shared album link

The simplest option. In Google Photos, open an album, tap **Share**, create a link, and paste it on the web page's **Photos** tab. The server downloads the photos, resizes them for the screen, and checks the album for changes every night, so adding photos to the album is all you need to do later.

This reads the album's public share page, which is not an official API. If Google changes that page, this method can stop working until the parser is updated. Anyone with the link can view the album.

### Picking photos from your Google account

This uses Google's official Photos Picker. You choose photos in the Google Photos app (you can search by album name while choosing), and the server downloads them once. It needs a free Google Cloud OAuth client, which takes about ten minutes to set up:

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project.
2. Under **APIs & Services > Library**, enable the **Photos Picker API**.
3. Configure the OAuth consent screen (Google Auth Platform). Choose **External**, fill in the app name and your email, and add your own Google account under **Test users**.
4. Under **Clients** (or **Credentials**), create an OAuth client with the application type **TVs and Limited Input devices**. This type signs in with a short code instead of a redirect, so it works for a server on a home network with no public address.
5. Put the client ID and secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `docker-compose.yml`, then run `docker compose up -d`.
6. On the web page's **Photos** tab, tap **Connect Google account**, enter the code shown, then tap **Pick photos**.

While the consent screen is in testing mode, Google ends the sign-in after seven days. Photos already downloaded stay on the calendar; connect again when you want to pick more.

## Weather

Search for your town during setup, or later under **Settings > Weather**, and choose Fahrenheit or Celsius.

- **On the calendar:** the header shows current conditions with today's high and low. Tap it for the full forecast.
- **On the Weather screen:** current conditions and feels-like temperature, wind speed and direction on a compass, humidity and dew point, UV index, air quality (US AQI), pressure, visibility, a 24-hour temperature and rain chart, a 7-day forecast with each day's range drawn against the week, and sunrise, sunset, and daylight.
- **In photo mode:** a card with the current conditions and the next six hours.

Forecasts come from [Open-Meteo](https://open-meteo.com/), which needs no API key and is free for non-commercial use. The server caches each forecast for ten minutes, and the tablet refreshes every fifteen and keeps the last forecast for when it's offline.

## Sports scores

Choose teams during setup or later in Settings. Supported leagues: NFL, college football, NBA, WNBA, men's and women's college basketball, MLB, NHL, MLS, Premier League, La Liga, and the Champions League.

For each team, the photo screen shows the most relevant game: a live game first, then today's game, then the latest result, then the next game. Scores update every two minutes while photos are showing. They come from ESPN's public site API, which is free and widely used but undocumented, so it can change without notice.

## Photo mode and quiet hours

Photo mode starts after the calendar sits untouched for the time you choose (five minutes by default), or when you tap the photos button in the sidebar. Photos fill the whole screen, cropping the edges where a photo's shape doesn't match the display, with a slow zoom while each one shows. Tap anywhere to return to the calendar.

With quiet hours turned on, the screen dims overnight and shows only a clock. Touching it brings the calendar back for a minute.

## Setting up the tablet

- In Settings on the calendar, turn on **Open automatically** and choose Kiwi Calendar when Android asks for a home app. The calendar then reopens on its own after a restart or power cut.
- The app keeps the screen on while it's open, so the system screen timeout doesn't matter.
- Some manufacturers ship aggressive battery managers that stop background work. If the daily sync seems not to run, set Kiwi Calendar's battery usage to **Unrestricted** in the system app settings.

## Changing the logo or colors

The logo lives in `branding/logo.svg`. It's used in four places:

| File | Where it shows |
| --- | --- |
| `android/app/src/main/res/drawable/ic_launcher_foreground.xml` | Launcher icon, also used for Android's themed icons |
| `android/app/src/main/res/drawable/ic_logo.xml` | Sidebar, setup, and Settings screens |
| `server/app/static/logo.svg`, `icon.svg`, `apple-touch-icon.png` | Phone web page, browser tab, and home-screen shortcut |
| `branding/icon.svg` | This README |

The launcher icon background is the `ink` color in `android/app/src/main/res/values/colors.xml`. The app's black-and-white palette and event shades are in `android/app/src/main/java/dev/kiwicalendar/ui/Theme.kt`, and its frosted-panel styles are in `ui/Glass.kt`. The phone web page keeps its own green palette, set at the top of `server/app/static/app.css`.

## Server configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `TZ` | `UTC` | Time zone used for calendar feeds and the daily refresh |
| `KIWI_TOKEN` | generated | Access token for the app and web page. If unset, one is generated on first start and kept in the data volume |
| `SYNC_TIME` | `03:00` | Local time of the daily refresh of feeds and the shared album. A refresh also runs at startup |
| `MAX_PHOTOS` | `500` | Most photos taken from a shared album |
| `PHOTO_MAX_EDGE` | `1920` | Photos are resized so their longest side is at most this many pixels |
| `GOOGLE_CLIENT_ID` | | OAuth client for the Photos Picker |
| `GOOGLE_CLIENT_SECRET` | | OAuth client secret for the Photos Picker |
| `KIWI_DATA` | `/data` | Where the database, token, and photos are stored |

The web page has buttons to refresh feeds and the album immediately, so you rarely need to wait for the nightly run.

## Signing

Android only installs an update over an existing app if both are signed with the same key. Create one key, once, and store it as repository secrets:

```sh
keytool -genkeypair -v -keystore kiwi-calendar.jks -alias kiwi \
  -keyalg RSA -keysize 4096 -validity 10000

base64 -w0 kiwi-calendar.jks          # Linux
base64 -i kiwi-calendar.jks           # macOS
```

Under **Settings > Secrets and variables > Actions**, add:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The base64 output |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | `kiwi` |
| `KEY_PASSWORD` | The key password (the same as the keystore password unless you chose otherwise) |

Keep a backup of `kiwi-calendar.jks` somewhere safe and never commit it. If it's lost, the only way to install new builds is to uninstall the app first.

## Building locally

The app needs JDK 17 and the Android SDK. Open `android/` in Android Studio, or:

```sh
cd android
./gradlew assembleDebug
```

To change the package name, edit `applicationId` in `android/app/build.gradle.kts`. The Kotlin package (`namespace`) can stay as it is.

For the server:

```sh
cd server
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
python -m pytest
KIWI_DATA=./data TZ=America/Chicago uvicorn app.main:create_app --factory --reload --port 8080
```

## Server API

Every endpoint except `/api/health` needs an `Authorization: Bearer <token>` header.

| Method and path | Purpose |
| --- | --- |
| `GET /api/revision` | A number that changes whenever events or photos change |
| `GET /api/sync` | Everything the tablet stores: events from 31 days ago to about a year ahead, plus the photo list |
| `GET /api/events?days=60` | Upcoming events |
| `POST /api/events` | Create an event |
| `PUT /api/events/{id}` | Create or replace an event with a client-chosen id |
| `DELETE /api/events/{id}` | Delete an event |
| `GET, POST /api/feeds`, `DELETE /api/feeds/{id}`, `POST /api/feeds/refresh` | Subscribed calendars |
| `GET /api/photos`, `GET /api/photos/{id}` | Photo list and image files |
| `PUT, DELETE /api/photos/album`, `POST /api/photos/album/refresh` | Shared album link |
| `GET /api/google`, `POST /api/google/connect`, `POST /api/google/pick` | Photos Picker |
| `GET /api/sports/leagues`, `GET /api/sports/teams?league=nfl` | Leagues and teams |
| `GET /api/scores?teams=nfl:3,nba:4` | Current scores for the given teams |
| `GET /api/weather?lat=42.07&lon=-87.94&units=imperial` | Current conditions, 24 hours, 7 days, and air quality |
| `GET /api/weather/search?q=Chicago` | Places matching a name, for choosing a location |

Timed events use ISO 8601 with an offset (`2026-10-26T18:30:00-05:00`). All-day events use dates, with an exclusive end (`"start": "2026-10-30", "end": "2026-11-02"` covers three days).

## Security

The access token works like a shared password, and the QR code contains it, so anyone who scans or photographs the code can add and delete events. Keep the server on your home network. If you want to add events from outside the house, use a VPN such as Tailscale or WireGuard, or put the server behind a reverse proxy with HTTPS, rather than forwarding port 8080. Change the token by setting `KIWI_TOKEN` (or deleting the `token` file in the data volume) and restarting; then enter the new one on the tablet under Settings.

The app allows plain HTTP because home servers rarely have certificates.

## License

MIT, see [LICENSE](LICENSE). Two fonts are bundled under the SIL Open Font License 1.1: Outfit in the tablet app (see `android/app/src/main/assets/outfit-OFL.txt`) and Atkinson Hyperlegible on the web page (see `server/app/static/fonts/OFL.txt`). Weather data is from Open-Meteo.

## Unified interface / photo repair update

The `server/app/static/` dashboard uses the same family-oriented navigation as the Android app: an interactive family calendar, agenda, sync health, and locally cached photo thumbnails. Both support accessible dark and light palettes with Outfit text. On the web, use the **Light mode / Dark mode** button in the upper-right. On Android, select **Settings → Display → Appearance → System / Dark / Light**.

If the Android sleep/slideshow view is empty, check **Settings → Photos → Retry photo downloads** and whether **Quiet hours** is enabled (quiet mode intentionally shows only a clock). The native app now retries missing photo files even when the server revision has not changed, downloads each photo to a validated temporary file before replacing its cache, and refreshes on entering sleep mode when no usable photos exist. The server now bumps its data revision when an album refresh adds or removes pictures. Dashboard photo previews are fetched using the authenticated photo API, not unprotected image URLs.

A Docker update does not automatically update the Android APK. Build Android with the included GitHub Actions workflow (or Android Studio), then install the APK on the tablet. Consistent signing keys are required to upgrade without uninstalling. See `START-WINDOWS.md`.
