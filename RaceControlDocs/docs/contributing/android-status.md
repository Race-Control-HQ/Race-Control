# Android Status

A record of what has been verified on the Android app and what is still outstanding. Update it when you verify something on a device or close one of the open items.

## Verified

**Builds and runs against a live backend.** Verified 20 Jul 2026 with Android Studio Quail 2 (2026.1.2) on a Medium Phone API 36.1 (Android 16) emulator, with the FastF1 backend running locally on port 8000.

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

## Defects found and fixed during bring-up

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

## Second pass (20 Jul 2026)

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

## Still outstanding

- [ ] Side-by-side data diff against the iOS build on the same race ([build plan](/contributing/android-build-plan) 10.6)
- [ ] Full TalkBack linear-navigation sweep (swipe-based) on a real device
- [ ] Reminder fire test: toggle on a couple of minutes before a real session, force-stop, reboot, confirm it still fires
- [ ] Macrobenchmark: cold start, schedule scroll, replay playback at 60fps
- [ ] Replace the placeholder launcher icon with final brand artwork
- [ ] Compose UI tests and screenshot tests ([build plan](/contributing/android-build-plan) 10.2, 10.3)
- [ ] Automated tests for the backend's Play Integrity verification (`test_playintegrity.py`)

Any of these is a good first contribution. Say which one you are picking up in an issue first.
