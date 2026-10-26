# Weather and sports widgets — verification and setup

## Docker dashboard

Open `http://localhost:8088` after starting Docker. The Home page has **Weather** and **Recent scores** cards.

- Weather: search for a city, select the suggested place, and choose °F or °C. The card shows current temperature, feels-like, high/low, chance of rain, and the next six hours. It refreshes every 10 minutes while the dashboard is open. A failed refresh shows an outage message and, if available, its previously loaded forecast.
- Sports: select a league, select a team, click **Follow**. Scores refresh every 2 minutes while the Home page is open. The card shows live, final, or upcoming games. It will tell you when no schedule is available. Use **Refresh** for a manual attempt.
- Browser-only selections are stored in that browser's localStorage. Android tablet selections are configured separately in Android Settings. The server does **not** send sports preferences from browser to tablet or vice versa.
- Both widgets require the server access token and an internet connection from Docker to its providers. Internet-connected browser alone is insufficient.

## Android tablet

- In **Settings → Weather**, select a location and units. Weather refreshes every 15 minutes while the app is visible; the saved forecast remains available if the network drops. Changing locations/units hides the old location's report while new data loads. The photo/sleep screen shows current local weather when not in quiet-clock mode.
- In **Settings → Teams**, choose leagues and teams to follow. The tablet polls scores every 2 minutes while the app is visible, including outside the sleep screen. The sleep screen shows up to four eligible recent games and labels out-of-date live scores as saved results rather than claiming they are LIVE.
- Both widgets use the Kiwi Docker server API. Set its reachable LAN address (e.g. `http://192.168.x.x:8088`) and access token in Android setup, not `http://localhost:8088`.
- Android quiet hours show clock only by design; photos and overlays (sports/weather) are hidden during quiet hours.

## Providers / known constraints

- Weather source: [Open-Meteo](https://open-meteo.com/). Air-quality outage does not block the forecast. The provider's timezone is used so future dates across a daylight-saving change get the correct UTC offsets. Open-Meteo's free usage may be limited to non-commercial scenarios; review provider terms for production use.
- Sports source: ESPN's **undocumented** public site API. Only an active game or the latest completed game started in the past **7 × 24 hours** will appear for each followed team; future fixtures and older scores are excluded. If no game qualifies, that team has no visible score. Web and Android also filter cached results so old scores disappear even offline. The server checks a team schedule when ESPN's scoreboard has no recent score. For production reliability, use a contracted official data source.
- Source data is fetched at runtime; test fixtures and screenshots use **sample** responses, not proof of live provider availability.

## Troubleshooting on Windows

```powershell
docker compose up -d --build --force-recreate
docker compose ps
docker compose logs kiwi --tail=100
```

Check the Kiwi server API in your browser's developer tools (Network tab): `/api/weather`, `/api/sports/teams`, `/api/scores` must return 200 with the access token. Provider outages return 502 rather than misleadingly returning an empty success response. Be sure Docker Desktop can reach the internet and your tablet can reach the Windows host's local IP.

The provider code was verified with deterministic isolated tests; browser behavior was checked in dark, light, and mobile layouts. Full Docker, Android Gradle builds, and live ESPN/Open-Meteo integration could not be executed in this environment. Run those checks on your devices before considering the widgets production-verified.
