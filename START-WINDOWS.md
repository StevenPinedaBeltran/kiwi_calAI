# Kiwi Calendar — Windows Docker Desktop

This edition contains the **redesigned Docker dashboard and updated Android app source**. Both share Outfit typography, neutral, high-contrast surfaces, and light/dark themes. Web theme selection is saved in the browser; Android theme selection is in **Settings → Display → Appearance** (System, Dark, Light). The Docker image alone does not change the APK on your tablet.

## Install or update

1. Extract the ZIP. Open **Docker Desktop** and wait until its Engine is running.
2. Open PowerShell in the extracted **`kiwi-calendar`** folder (it contains `docker-compose.yml`).
3. Run:

   ```powershell
   docker compose up -d --build --force-recreate
   docker compose ps
   ```

4. Open **http://localhost:8088** on Windows.
5. To find the server access token, run:

   ```powershell
   docker compose logs kiwi --tail=60
   ```

   Look for `Access token: ...` in the log and enter it in the web app. Never publish your token.

No GitHub token is needed: the image is built locally from `./server` using the supplied source. Docker may still download a public base image and dependencies on its first build.

### Existing data / old Docker projects

**Back up first.** Keep using the same Compose project name as your previous Kiwi deployment so Docker reuses its `kiwi-data` volume, which holds events, photos, and the token. A different extraction folder can create a different Compose project name and a new (empty) volume. If you've moved the files, find the original project name in Docker Desktop and use `docker compose -p YOUR_ORIGINAL_PROJECT_NAME up -d --build --force-recreate` in the new folder. Never run `docker compose down -v` unless you really intend to delete the data.

If port 8088 is already allocated, stop your old Kiwi container before starting the replacement, or adjust the port mapping.

### Android calendar connection

The tablet must use your **Windows computer's actual local IP**, not `localhost`:

1. Run `ipconfig` in PowerShell and find your active network adapter's **IPv4 Address**.
2. Enter `http://YOUR_WINDOWS_IP:8088` and the Kiwi access token in the Android app's server settings.
3. Keep the Windows PC and tablet on the same network, and allow the port through Windows Firewall if necessary.

The website's `SERVER OK` indicator means the **server API** responded; it does not claim the tablet is online. The Android changes must be built and installed as a new APK separately.

### Logs

```powershell
docker compose logs kiwi --tail=100
```

### What's new

- Android and web have new high-contrast themes: near-black surfaces in dark mode, white/slate surfaces in light mode, Outfit typography, and a shared panel language.
- Clickable six-week monthly calendar that also supports past events, with dates, multi-day events, and event details.
- Selected day focus with direct **add event**; local events can be edited or deleted; subscribed events are read-only.
- Agenda search and filter by local vs subscribed calendars.
- Feed health, photo library status, server revision, and an explicit refresh of subscribed calendar feeds.
- Improved multi-day event creation using separate start and end dates.
- Mobile responsive layout, including readable calendar markers on narrow screens.
- Existing backend, database format, photos, iCal feeds, Google Photos, and Android source retained.

### Build and install the new Android APK

Push this project's `android/` and `.github/workflows/android.yml` directories to your GitHub repository, then open **Actions → Android → Run workflow**. Download the `kiwi-calendar-apk` artifact after the workflow succeeds. You can alternatively use Android Studio to build the APK.

Connect Android with USB debugging and run:

```powershell
.\adb.exe install -r "C:\path\to\kiwi-calendar.apk"
```

**Important:** `-r` preserves data **only when the APK is signed with the same signing key as the installed app**. A signing mismatch (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) cannot be bypassed. For a different key, back up device settings/data and uninstall the old package before installing the new build (`.\adb.exe uninstall dev.kiwicalendar`). Configure consistent GitHub signing secrets as documented in the README to avoid this on future updates.

### If sleep photos appear blank

1. In Android **Settings → Photos**, check the number of photos saved, then tap **Refresh saved photos** or **Retry photo downloads**.
2. Check **Settings → Display → Quiet hours**. Quiet hours intentionally show only a dim clock (no photos).
3. Keep Android and Docker on the same Wi-Fi/LAN. Android now detects missing cached images, retries downloads during periodic checks, and tries again when sleep mode starts without usable photos.
4. Open **Photos** on the Docker dashboard to see previews; if an album says it failed, use **Refresh photos** and check `docker compose logs kiwi --tail=100`.

Photo playback is from files cached locally on Android. A Google Photos share link still requires the Docker server to access Google's service at least once to download the images.

The Android APK is **not** rebuilt by starting Docker; its source stays in `android/` for a separate build.

### Weather and sports after upgrading

The Home page now has Weather and Sports scores cards. Search for your weather location and choose a team to follow. On Android, select the location in **Settings → Weather** and teams in **Settings → Teams**. The tablet's sports scores now refresh while the app is running (not only while the sleep screen is open). Weather follows your selected city's timezone and units.

See **`WIDGETS-AND-TESTING.md`** for widget refresh rates, data sources, offline behavior, and troubleshooting. Website selections remain in the current browser; Android team/location settings are separate.
