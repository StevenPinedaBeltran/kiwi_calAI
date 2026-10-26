# Kiwi color and accessibility review

Main body foreground/background pairs (contrast ratios computed from sRGB luminance):

| Pair | Ratio | WCAG AA normal text |
|---|---:|---|
| Dark primary on card | 16.74:1 | Pass |
| Dark muted on card | 9.47:1 | Pass |
| Dark subdued on card | 6.69:1 | Pass |
| Light primary on card | 16.40:1 | Pass |
| Light muted on card | 7.47:1 | Pass |
| Light subdued on card | 5.67:1 | Pass |
| Dark primary action label | 17.23:1 | Pass |
| Light primary action label | 16.41:1 | Pass |
| Android dark muted on card | 9.08:1 | Pass |
| Android light muted on card | 7.42:1 | Pass |

This covers major palette tokens, not an automated accessibility certification of every possible state, photo, dialog, or event category. The sleep slideshow keeps white, translucent text overlays over dark scrims in both appearance modes. Reduced-motion preferences disable web animations. Text and controls use legible Outfit typography.

## Checks performed

- JavaScript syntax (`node --check app.js`): passed.
- Python server syntax (`python -m compileall`): passed.
- Web UI using mocked server API data (desktop dark, desktop light, 390px viewport): month calendar, login/session flow, photo thumbnail requests, photo library previews, agenda navigation, theme toggle, and no JS runtime errors: passed.
- Docker container image build / Android Gradle APK compile / physical DF-18T sleep test: unavailable in the build environment. These must be verified on Windows Docker Desktop and the Android tablet.
