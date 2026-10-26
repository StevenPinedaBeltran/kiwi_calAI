<img src="branding/icon.svg" width="96" alt="">

# Kiwi Calendar

A self-hosted family wall calendar. I built it for one of those cheap Android 14 calendar screens sold for kitchens and hallways, because the app it came with was locked down. It runs on any Android 8.0 or newer tablet.

On the wall it's a black-and-white dashboard: a month calendar with the day's schedule beside it, a detailed local forecast, and a sidebar for photos and settings. When nobody has touched it for a while, it turns into a full-screen photo frame with a clock, the weather, the next few events, and scores for the teams you follow.

Behind it is a small server in Docker. It refreshes photos and subscribed calendars every night and hosts a web page that anyone in the house can open by scanning a QR code on the calendar to add events. The tablet keeps its own copy of everything, so it keeps working when the server or the internet is down.

```
 Phone or computer ── scans QR code ──► web page ─┐
                                                   ▼
 Google Photos ─┐                         ┌──────────┐   checks every 2 min,    ┌──────────┐
 iCal feeds ────┼── refreshed daily ────► │  server  │ ◄── full sync daily ───── │  tablet  │
 ESPN scores ───┤                         │ (Docker) │                           │   app    │
 Open-Meteo ────┘── weather on request ─► └──────────┘ ◄── weather every 15 min ─ └──────────┘
```

## What's here

| Path | Contents |
| --- | --- |
| `android/` | The tablet app: Kotlin and Jetpack Compose, no Google Play services needed |
| `server/` | The server: Python, FastAPI and SQLite, plus the web page in `server/app/static` |
| `.github/workflows/` | Builds the APK and the server image with GitHub Actions |
| `scripts/` | Helper for creating a signing key on Windows |
| `docker-compose.yml` | Runs the server |

## Run your own

You'll need a tablet, an always-on computer at home that runs Docker (a Raspberry Pi 4 is plenty), and a GitHub account to build the APK. Android Studio works too if you'd rather build locally; see [Building locally](#building-locally).

### 1. Fork the repo and turn on Actions

Click **Fork** at the top of this page. GitHub turns off workflows in new forks, so open your fork's **Actions** tab and enable them.

Two workflows are included:

- **Android** builds the APK on every push to `main`. The APK shows up under the run's **Artifacts**, and on your **Releases** page when you push a tag like `v1.0.0`.
- **Server** runs the tests and publishes a Docker image to `ghcr.io/<you>/<repo>`. You don't need this image unless you want to pull it instead of building locally.

### 2. Add a signing key

Android only installs an update over an existing app if both builds are signed with the same key. Without one, every build gets a throwaway key, and each update means uninstalling the app and setting it up again. Make your own key once, before you install anything you want to keep.

**Windows:** from the repo folder, run

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-signing-key.ps1
```

It uses Java's `keytool` if you have it and Docker Desktop if you don't. The key goes in a `kiwi-calendar-signing` folder in your user folder, outside the repo. The script copies the encoded key to your clipboard and prints the four secrets to add.

**Mac or Linux:**

```sh
keytool -genkeypair -v -keystore kiwi-calendar.jks -alias kiwi \
  -keyalg RSA -keysize 4096 -validity 10000

base64 -w0 kiwi-calendar.jks          # Linux
base64 -i kiwi-calendar.jks           # macOS
```

In your fork, go to **Settings > Secrets and variables > Actions** and add:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The base64 text |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | The alias (`kiwi` above, `kiwicalendar` from the Windows script) |
| `KEY_PASSWORD` | The key password, usually the same as the keystore password |

Back up the `.jks` file and its password, and never commit them. If you lose the key, the only way to install new builds is to uninstall the app first.

### 3. Build the APK

Tag a release and push it:

```sh
git tag v1.0.0
git push origin v1.0.0
```

When the Android workflow finishes, `kiwi-calendar-1.0.0.apk` is on your fork's **Releases** page.

### 4. Start the server

On the machine that will run it, clone your fork and set `TZ` in `docker-compose.yml` to your time zone (for example `America/New_York`). Then:

```sh
docker compose up -d --build
docker compose logs kiwi | grep "Access token"
```

The web page is now at `http://<that machine's IP>:8088`. Give the machine a fixed address in your router's settings, since the tablet remembers it. To use the image the Server workflow published instead of building, replace the `build:` lines in `docker-compose.yml` with `image: ghcr.io/<you>/<repo>:latest`. GitHub makes new images private, so either make the package public under your profile's **Packages** tab or run `docker login ghcr.io` first.

### 5. Install it on the tablet

Open your Releases page in the tablet's browser, download the APK, and open it, allowing installs from the browser when Android asks. With USB debugging on, `adb install kiwi-calendar-1.0.0.apk` works too.

The setup screens ask for the server address (like `192.168.1.20:8088`) and the access token, your town for the weather, the teams you follow, and when photo mode should start. You add photos from your phone.

For later updates, push a new tag (`v1.0.1` and so on) and install the new APK over the old one. Your settings stay.

## Using it

### The calendar

The sidebar switches between the calendar and the weather, starts photo mode, shows the QR code for adding events from a phone, and opens settings. Tap a day to see its schedule in the panel on the right. The arrows above the panel step through days, and the calendar button jumps back to today. The greeting at the top uses the name set under **Settings > Display**.

The tablet app is strictly black and white, and each event's color shows as a shade of gray. Events created on the tablet pick a shade by type: Important, Family, Work or Personal. The web page uses dark gray with orange and green accents.

### Adding events

- **On the calendar:** tap **+** above the month, or tap an event you made to edit or delete it.
- **From a phone:** tap **From phone** on the calendar and scan the code. The page remembers the phone afterwards.
- **From a computer:** open `http://<server>:8088` and paste the access token.

Events added elsewhere show up within about two minutes. Events made on the tablet while it's offline are queued and sent once the server is reachable.

To show calendars you already keep somewhere else, add their iCal links on the web page under **Calendars**. For Google Calendar, open its settings on a computer, select the calendar, and copy **Secret address in iCal format**. These events are read-only on the wall; edit them where they came from.

### Photos

Google stopped letting third-party apps read Google Photos albums in 2025, so there are two ways to get photos in. You can use both.

**Shared album link.** The easy one. In Google Photos, open an album, tap **Share**, create a link, and paste it on the web page's **Photos** tab. The server downloads the photos, resizes them for the screen, and checks the album every night, so adding photos to the album is all you do later. This reads the album's public share page, which isn't an official API, so it can break if Google changes that page. Anyone with the link can see the album.

**Picking photos from your Google account.** This uses Google's official Photos Picker: you choose photos in the Google Photos app (you can search by album while choosing), and the server downloads them once. It needs a free Google Cloud OAuth client, which takes about ten minutes:

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project.
2. Under **APIs & Services > Library**, enable the **Photos Picker API**.
3. Set up the OAuth consent screen (Google Auth Platform). Choose **External**, fill in the app name and your email, and add your Google account under **Test users**.
4. Under **Clients** (or **Credentials**), create an OAuth client of type **TVs and Limited Input devices**. It signs in with a short code instead of a redirect, so it works for a server with no public address.
5. Put the client ID and secret in `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `docker-compose.yml` and run `docker compose up -d`.
6. On the web page's **Photos** tab, tap **Connect Google account**, enter the code, then tap **Pick photos**.

While the consent screen is in testing mode, Google ends the sign-in after seven days. Downloaded photos stay; connect again when you want to pick more.

### Weather

Pick your town during setup or under **Settings > Weather**, and choose Fahrenheit or Celsius.

- **Calendar header:** current conditions with today's high and low. Tap it for the full forecast.
- **Weather screen:** feels-like temperature, wind on a compass, humidity and dew point, UV index, air quality (US AQI), pressure, visibility, a 24-hour temperature and rain chart, a 7-day forecast, and sunrise, sunset and daylight.
- **Photo mode:** a card with current conditions and the next six hours.

Forecasts come from [Open-Meteo](https://open-meteo.com/), which needs no API key and is free for non-commercial use. The server caches each forecast for ten minutes. The tablet refreshes every fifteen and keeps the last one for when it's offline.

### Sports scores

Pick teams during setup or in Settings. Supported leagues: NFL, college football, NBA, WNBA, men's and women's college basketball, MLB, NHL, MLS, Premier League, La Liga and the Champions League.

A team's score only shows if its game started in the last seven days. Live games come first, then the latest finals. Upcoming games and older results are hidden, even from the offline cache. Scores refresh every two minutes while the app is open. They come from ESPN's unofficial site API, which can change without notice.

### Photo mode and quiet hours

Photo mode starts after the calendar sits untouched for the time you choose (five minutes by default), or when you tap the photos button in the sidebar. Photos fill the screen with a slow zoom. Tap anywhere to go back to the calendar.

Sometimes I just want it to look like a picture frame. Long-press anywhere in photo mode and the clock and widgets fade out; long-press again to bring them back. It remembers how you left it. To hide only some of it, there are switches under **Settings > Display > While photos play**.

With quiet hours on, the screen dims overnight and shows only a clock. Touching it brings the calendar back for a minute.

### Setting up the tablet

- Under Settings, turn on **Open automatically** and choose Kiwi Calendar when Android asks for a home app. The calendar then comes back on its own after a restart or power cut.
- The app keeps the screen on while it's open, so the system screen timeout doesn't matter.
- Some tablets ship aggressive battery managers. If the daily sync doesn't seem to run, set Kiwi Calendar's battery usage to **Unrestricted** in the system app settings.

## Make it yours

I built this for my own wall, but fork it and change whatever you need: different hardware, other leagues, a new look, or ripping out the parts you don't use. No need to ask first. A few places to start:

- **App ID:** change `applicationId` in `android/app/build.gradle.kts` if you want your version installed next to the original. The Kotlin `namespace` can stay.
- **Logo:** the source is `branding/logo.svg`. It's drawn into `android/app/src/main/res/drawable/ic_launcher_foreground.xml` (launcher and themed icon), `ic_logo.xml` in the same folder (sidebar, setup and Settings), and `server/app/static/logo.svg`, `icon.svg` and `apple-touch-icon.png` (web page, browser tab and home-screen shortcut).
- **Colors:** the app's palette and event shades are in `android/app/src/main/java/dev/kiwicalendar/ui/Theme.kt`, the frosted panels in `ui/Glass.kt`, and the launcher background is `ink` in `android/app/src/main/res/values/colors.xml`. The web page's colors are at the top of `server/app/static/app.css`.
- **Leagues:** the list is `LEAGUES` in `server/app/sports.py`. Each entry points at an ESPN path.

## Reference

### Server settings

Set these under `environment` in `docker-compose.yml`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `TZ` | `UTC` | Time zone for calendar feeds and the daily refresh |
| `KIWI_TOKEN` | generated | Access token for the app and web page. If unset, one is generated on first start and kept in the data volume |
| `SYNC_TIME` | `03:00` | Local time of the daily refresh. A refresh also runs at startup |
| `MAX_PHOTOS` | `500` | Most photos taken from a shared album |
| `PHOTO_MAX_EDGE` | `1920` | Longest side, in pixels, that photos are resized to |
| `GOOGLE_CLIENT_ID` | | OAuth client for the Photos Picker |
| `GOOGLE_CLIENT_SECRET` | | OAuth client secret for the Photos Picker |
| `KIWI_DATA` | `/data` | Where the database, token and photos are stored |

The web page has buttons to refresh feeds and the album right away, so you rarely need to wait for the nightly run.

### Building locally

The app needs JDK 17 and the Android SDK. Open `android/` in Android Studio, or:

```sh
cd android
./gradlew assembleDebug
```

The server:

```sh
cd server
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
python -m pytest
KIWI_DATA=./data TZ=America/Chicago uvicorn app.main:create_app --factory --reload --port 8080
```

### Server API

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
| `GET /api/weather?lat=42.07&lon=-87.94&units=imperial` | Current conditions, 24 hours, 7 days and air quality |
| `GET /api/weather/search?q=Chicago` | Places matching a name |

Timed events use ISO 8601 with an offset (`2026-10-26T18:30:00-05:00`). All-day events use dates with an exclusive end (`"start": "2026-10-30", "end": "2026-11-02"` covers three days).

## Troubleshooting

- **Android won't install an update** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): the installed app was signed with a different key, usually a build made before the signing secrets were added. Uninstall it (`adb uninstall dev.kiwicalendar`) and install again. You'll go through setup once more.
- **Photo mode is empty:** try **Settings > Photos > Retry photo downloads**, and check that quiet hours aren't on, since quiet mode only shows a clock.
- **The tablet can't reach the server:** use the server machine's local IP, not `localhost`, include port `8088`, and make sure its firewall allows that port.
- **Updating the server doesn't update the app.** The APK comes from the Android workflow; install the new one on the tablet.
- **Lost data after moving the server folder:** Docker names the data volume after the folder. Run `docker compose -p <old folder name> up -d --build` to reuse the old one. Never run `docker compose down -v` unless you mean to delete everything.

## Security

The access token works like a shared password, and the QR code contains it, so anyone who scans or photographs the code can add and delete events. Keep the server on your home network. To add events from outside the house, use a VPN like Tailscale or WireGuard, or a reverse proxy with HTTPS, rather than forwarding the port. To change the token, set `KIWI_TOKEN` (or delete the `token` file in the data volume), restart, and enter the new token on the tablet under Settings.

The app allows plain HTTP because home servers rarely have certificates.

## License

MIT, see [LICENSE](LICENSE). Two fonts are bundled under the SIL Open Font License 1.1: Outfit in the app (`android/app/src/main/assets/outfit-OFL.txt`) and Atkinson Hyperlegible on the web page (`server/app/static/fonts/OFL.txt`). Weather data is from Open-Meteo.
