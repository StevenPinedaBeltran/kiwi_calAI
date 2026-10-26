# Kiwi Calendar changelog

## v4.3 — Clean photo mode
- Long-press photo mode to hide/show the clock and widgets (remembered)
- Settings > Display > While photos play: toggles for clock/date, Up next, weather, scores
- Scrims fade out with the widgets so it's just the photo

## v4.2.1 — CI maintenance
- GitHub Actions moved to Node 24 releases: checkout v7, setup-java v6, upload-artifact v7, setup-gradle v6, setup-python v7, action-gh-release v3, and current Docker actions.
- `scripts/make-signing-key.ps1` creates the release signing key on Windows and prints the repository secrets.

## v4.2 — Custom sports & weather widgets
- Custom sports scoreboard cards for web dashboard and Android sleep display.
- ESPN-hosted team logos, validated HTTPS URLs, and accessible abbreviation fallbacks.
- Prominent scores and distinct LIVE, FINAL and cached-result indicators.
- Weather display with condition illustrations, large current temperature, high/low, rain, humidity, wind, and upcoming hourly forecast.
- Maintains dark and light mode with readable fixed-contrast sleep overlays.

## v4.1 — Recent sports results
- Show live games and each followed team's most recent final within a rolling seven-day window.
- Hide games older than seven days, including from browser and Android caches.
- Exclude upcoming fixtures from recent scores.

## v4.0 — Weather and sports integration
- Weather location search, selectable units, caching and periodic refresh.
- Followed teams and score refresh separate from the sleep screen.
- Schedule fallback for teams missing from short scoreboards.

## v3 — Unified web and Android UI
- Dark/light styles, calendar agenda, photos and connected-calendar management.
- Photo retry and missing-file recovery improvements for Android sleep display.

### Verification status
- Python syntax checks and isolated widget tests passed.
- JavaScript syntax and web widget filtering tests passed.
- Complete Docker and Android builds were **not** executed in this environment.
- Live providers and physical DF-18T tablet integration remain to be verified.
