# RaceControl for Android

A native Android client for the RaceControl FastF1 backend — a Kotlin/Compose
counterpart to the SwiftUI iOS app in [`../RaceControlApp/`](../RaceControlApp/).

Same backend, same data, same five sections. Android conventions where they
differ from Apple's. **Neither the iOS app nor the backend was modified.**

| Piece | Tech |
|---|---|
| Android app | Kotlin · Jetpack Compose · Material 3 (minSdk 26) |
| iOS app | Swift · SwiftUI (unchanged) |
| Backend | Python · FastAPI · FastF1 (unchanged) |

See [`docs/FEATURES.md`](docs/FEATURES.md) for the full iOS→Android mapping and
[`docs/TASKS.md`](docs/TASKS.md) for the build plan.

---

## 1. Build it

Requirements: **Android Studio Ladybug or newer**, JDK 17.

```bash
# from this directory
./gradlew assembleDebug        # or just open the folder in Android Studio
```

> **First build:** the version catalogue in `gradle/libs.versions.toml` was
> pinned without access to a dependency resolver. Let Android Studio offer its
> upgrades before you build — nothing here depends on APIs newer than the
> versions listed, so bumping them should be safe.

## 2. Point it at the backend

Start the backend first:

```bash
cd ../backend && ./run.sh          # serves on :8000
```

Then, in the app: **⋮ overflow menu → Settings → Server address**.

| Running on | Address |
|---|---|
| Emulator | `http://10.0.2.2:8000` (the default — it's the emulator's alias for your machine) |
| Physical device | `http://<your-machine-LAN-IP>:8000`, e.g. `http://192.168.1.20:8000` |
| Deployed | your HTTPS URL |

Tap **Test Connection**: it probes the unauthenticated `/api/health` first, then
an authenticated endpoint, so a bad token is reported here rather than as
errors scattered across the app.

### Cleartext HTTP

Debug builds permit plain HTTP to any host. **Release builds only permit
cleartext to `localhost` and `10.0.2.2`** — anything else must be HTTPS. That is
stricter than the iOS build's ATS policy, deliberately: Android's network
security config cannot express "any private LAN address", so the choice is
between allowing cleartext everywhere or allowing it only for local development.

If you need a release build to reach a LAN backend over HTTP, add the address to
`app/src/main/res/xml/network_security_config.xml`.

## 3. Authentication

The iOS app gates the API with **Apple App Attest**. This app now has the real
Android counterpart: **Google Play Integrity**. Google Play services vouches
that this is a genuine, unmodified copy of the app on a genuine device,
verified server-side against Google's attestation API — there is nothing
embedded in the APK for anyone to extract with a decompiler, which a static
shared token would be.

`PlayIntegrityTokenProvider` fetches a one-time nonce from the backend,
asks Play Integrity to vouch for the app tied to that nonce, and exchanges the
result for a short-lived JWT, cached encrypted (`PlayIntegrityTokenStore`) so
ordinary requests don't pay for a fresh round trip. `CompositeTokenProvider`
prefers a manually-entered Settings token first — an admin/dev override, or
for a local `./run.sh` backend running without auth at all — and falls back to
Play Integrity otherwise, the same precedence the iOS app already uses (admin
token first, attestation otherwise).

Leave the Settings token empty to rely on Play Integrity (once this build is
signed and distributed through Play) or to talk to a local backend with no
auth configured.

**Backend changes required and made:** `backend/playintegrity.py` (new) mirrors
`attest.py`'s structure and is wired into `main.py` as a third, fully
independent auth mechanism — enabling it never requires or affects App Attest
or the admin token, and `attest.py` itself is untouched. `PLAY_INTEGRITY_ENABLED`
defaults to off exactly like `APP_ATTEST_ENABLED`, so `./run.sh` with no env
vars set stays fully open for local development, unchanged from before.

**What still has to happen outside code**, in Google Cloud + Play Console (see
`backend/.env.example` for the full list): enable the Play Integrity API on a
Google Cloud project, link that project to the app in Play Console, create a
service account with access to the linked app and download its JSON key, and
set `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` in `app/build.gradle.kts` (currently
a placeholder `0L`) to the real project number. None of that can be done from
here.

## 4. What's different from iOS, and why

Everything the iOS app does, this does. The divergences are all Android
platform conventions, and each is documented in `docs/FEATURES.md`:

- **Settings lives in the ⋮ overflow menu**, not top-left — the leading app bar
  slot belongs to navigation on Android.
- **48dp touch targets** throughout. The iOS build targets 44pt, which is below
  Android's accessibility minimum.
- **Tab rows instead of segmented controls** where the option count varies
  (race sessions run 1–6; standings has 4 modes).
- **No Material You dynamic colour.** Wallpaper-derived theming would collide
  with the official F1 team and tyre colours the app uses to convey meaning.
  Dark-only, same OLED-tuned palette as iOS.
- **No FAB.** The app is read-only; there is nothing to promote.
- **Reminders survive reboot** via a `BOOT_COMPLETED` receiver and stay fresh
  via a daily `WorkManager` job — Android alarms don't persist the way iOS
  notifications do, and users may not open the app between races.
- **Charts are Compose Canvas**, not a charting library — see the note at the
  top of `core/ui/RcCharts.kt`.
- **Offline**: a 10 MB OkHttp cache backs the schedule and standings, with a
  "showing cached data" banner. The iOS app has no cache.

## 5. Architecture

```
Compose UI ── StateFlow ── ViewModel ── RaceControlRepository ── Retrofit ── FastAPI
   Material 3      Hilt                    Result<T>            OkHttp cache
```

```
com.owlmedia.racecontrol
├── core/design/     theme, palette, type, dimens, tyre + flag helpers
├── core/ui/         UiState, loading/error/empty, shared components, charts
├── core/util/       date + lap-time formatting, LTTB downsampling
├── data/remote/     Retrofit API (22 endpoints), DTOs, interceptors, JsonValue
├── data/local/      DataStore settings, encrypted token, favourites
├── data/repository/ RaceControlRepository
├── notifications/   scheduler, alarm receiver, boot receiver, refresh worker
└── feature/         schedule · racedetail · replay · drivers · teams ·
                     standings · circuits · analysis · settings
```

`JsonValue` mirrors the iOS `JSONValue`: FastF1 emits numbers where
Ergast/Jolpica emits the same field as a string, and a permissive scalar type
absorbs that so the UI can never crash on a type mismatch.

## 6. Tests

```bash
./gradlew test
```

Covers the logic most likely to silently disagree with the iOS build:
JSON scalar coercion, lap-time and gap formatting, grid deltas, flexible date
parsing, team-colour legibility, and LTTB downsampling.

## 7. Status

**Builds and runs against a live backend.** Verified 20 Jul 2026, Android Studio
Quail 2 (2026.1.2), Medium Phone API 36.1 (Android 16) emulator, with the FastF1
backend running locally on port 8000.

`:app:assembleDebug` — BUILD SUCCESSFUL, 0 errors.

Verified with real 2026-season data:

| Area | Result |
|---|---|
| Races | Up-next banner, 24-race calendar, flags, sprint badges, completed markers |
| Race detail | Header, weekend schedule converted to device timezone, 8-tile analysis grid, session tabs |
| Standings | Points, wins, gap-to-leader bars, gold podium ranks — confirms `JsonValue` string/number coercion |
| Progress chart | Multi-series Canvas line chart, team colours, axis labels, legend |
| Replay | Running order with team liveries, tyre compounds, monospaced lap times; playback advanced lap 1→10 in 8s at 1× (0.9s/lap, matching iOS), position-change arrows correct |
| Settings | Backend URL, connection test, token field, notification toggles |
| Networking | `GET http://10.0.2.2:8000/api/seasons` confirmed in Logcat; offline path shows the ported iOS error text and retry |

### Defects found and fixed during bring-up

| # | Symptom | Cause |
|---|---|---|
| 1 | `Unresolved reference 'map'`, `'edit'` | DataStore/Flow extension imports |
| 2 | `Unresolved reference 'jsonPrimitive'`, `'intOrNull'` | kotlinx-serialization extension *properties* |
| 3 | `toHttpUrlOrNull`, `toMediaType`, `asConverterFactory` unresolved | OkHttp/Retrofit companion extensions |
| 4 | `FormatListNumbered` unresolved | icon is not in the AutoMirrored set |
| 5 | Crash on launch: `IllegalArgumentException: Deep link ... missing: [title]` | `Routes.RaceDetail.title` had no default, but the notification deep link only carries year+round |
| 6 | Progress chart drew a negative y-axis label | domain padding pushed below zero; now clamped at 0 for cumulative points |
| 7 | Circuit map overflowed its card | the track was fitted to the frame **before** the rotation was applied, so rotating pushed it past the edges; rotation also pivoted on the canvas centre while the projection anchored top-left. Coordinates are now rotated in data space first, bounds measured from the rotated points, the result centred, and the Canvas clipped |
| 8 | Telemetry throttle chart drew an axis from -6 to 110 (and gear/speed had the same latent risk) | shared `ChartDomain.cover()` padding has no concept of a metric's real bounds; throttle is 0-100%, gear and speed can't go negative. `TelemetryChartCard` now takes optional `minClamp`/`maxClamp`, applied per channel (throttle 0-100, speed and gear floored at 0) |

Fixes 6, 7 and 8 have now been rebuilt and eyeballed on the emulator against
live 2026 data: Standings > Progress shows a clean 0-based axis, Shanghai's
circuit map (the exact case from the original bug report) renders fully inside
its card, and the Telemetry Speed/Throttle/Gear charts all show sane axis
ranges (0/25/50/75/100 for throttle, no negative labels anywhere).

### Verified this session (20 Jul 2026, second pass)

- Every analysis screen not opened in the first pass — Telemetry (including
  the mini track map and lap comparison table), Lap Times, Strategy,
  Qualifying, Weather, Retirements, the standalone Track Map, Driver detail,
  and Head-to-head — all render correctly against live data.
- Replay playback advances laps correctly (lap 1 → 6 in 5s at 1×, matching the
  documented 0.9s/lap tick) with correct position-change indicators.
- 200% font-scale pass across Schedule, Race Detail, Standings, Circuit
  Detail, Replay and Settings: text reflows and wraps without clipping
  anywhere. One cosmetic-only issue: the bottom nav labels ("Drivers",
  "Standings") can word-break awkwardly at max scale — not clipped, just an
  ungainly line break. Not fixed; low priority polish.
- TalkBack spot-check: enabled TalkBack and tapped through representative
  controls (back button, primary/secondary buttons, a switch, an icon-only
  button, bottom nav tabs) — every one produced a correctly bounded
  accessibility-focus rectangle, confirming content descriptions and touch
  targets are in place. A full linear swipe-navigation sweep of every screen
  needs real touch input and remains a manual follow-up.
- Reminder scheduling verified end-to-end against the real system: toggling
  Race Reminders on triggers the `POST_NOTIFICATIONS` prompt, and
  `adb shell dumpsys alarm` confirms 48 real `RTC_WAKEUP` alarms registered
  against `ReminderAlarmReceiver`, capped near the documented 50-alarm limit.
  The one thing this doesn't cover is the full force-stop/reboot-survives-and-
  fires test, which needs a real session within a couple of minutes of now to
  observe — do this next time a practice/qualifying/race session is imminent.

### Still outstanding

- [ ] Side-by-side data diff against the iOS build on the same race — TASKS.md 10.6
      (can't be done on this Windows machine; iOS build isn't available here)
- [ ] Full TalkBack linear-navigation sweep (swipe-based) on a real device
- [ ] Reminder fire test: toggle on a couple of minutes before a real session,
      force-stop, reboot, confirm it still fires
- [ ] Macrobenchmark: cold start, schedule scroll, replay playback at 60fps
- [ ] Replace the placeholder launcher icon with final brand artwork
- [ ] Compose UI tests and screenshot tests (TASKS.md 10.2, 10.3)

## 8. Attribution

Data is sourced live by FastF1 from the F1 live-timing API and the
Ergast/Jolpica database. Unofficial project, not associated with Formula 1
companies. F1, FORMULA 1 and related marks are trademarks of Formula One
Licensing BV.
