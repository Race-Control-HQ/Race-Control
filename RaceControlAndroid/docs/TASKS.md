# RaceControl Android — Task List

Build order for the Android client described in [`FEATURES.md`](FEATURES.md).
Ten phases. Each phase ends in something runnable on a device.

**Rule for every phase:** `RaceControlApp/` and `backend/` are read-only. If a task appears
to need a backend change, stop and raise it rather than editing.

Legend — `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 0 — Decisions & prerequisites  ✅ resolved

| # | Decision | Answer |
|---|---|---|
| 0.1 | Stack | Kotlin + Jetpack Compose, Material 3 |
| 0.2 | Auth | Google Play Integrity (Android counterpart of iOS App Attest), admin API token as an optional override — see FEATURES.md §7 |
| 0.3 | Charts | ~~Vico for line/bar~~ — abandoned before implementation (no network access to verify Vico's API against a real artifact at the time); built as an in-house Compose `Canvas` layer instead (`core/ui/RcCharts.kt`: `RcLineChart`, track maps, stint timelines, sparklines) |
| 0.4 | Delivery | Plan first, then phased build with a check-in between phases |

**Still open — I will ask before the phase that needs them:**

| # | Question | Needed by |
|---|---|---|
| 0.5 | `minSdk` — 26 (Android 8, ~99% reach) or 29? Affects `java.time` desugaring and adaptive icons. | Phase 1 |
| 0.6 | `applicationId` — `com.owlmedia.racecontrol` (matches iOS bundle id) or a separate `.android` suffix? | Phase 1 |
| 0.7 | Where does the module live — `RaceControlAndroid/` beside `RaceControlApp/` (assumed), or its own repo? | Phase 1 |
| 0.8 | Default backend URL — iOS uses `http://localhost:8000`, useless on an Android device. Emulator uses `10.0.2.2:8000`. Ship that as the default, or blank-with-onboarding? | Phase 2 |
| 0.9 | App icon and Play Store assets — reuse the iOS artwork from `Assets.xcassets`, or is new artwork coming? | Phase 9 |

---

## Phase 1 — Project scaffold

- [ ] 1.1 Gradle project: Kotlin DSL, version catalog (`libs.versions.toml`), single `:app` module
- [ ] 1.2 Compile SDK 35, target 35, min per 0.5; Java 17; core library desugaring
- [ ] 1.3 Dependencies: Compose BOM, Material 3, Navigation-Compose, Hilt, Retrofit,
      OkHttp, kotlinx-serialization, Coil 3, DataStore, security-crypto, WorkManager
      (no Vico — charts are an in-house Canvas layer, see 0.3)
- [ ] 1.4 `RaceControlApplication` (`@HiltAndroidApp`), `MainActivity` with `enableEdgeToEdge()`
- [ ] 1.5 Manifest: INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED,
      `enableOnBackInvokedCallback="true"`, cleartext permitted only for localhost/LAN via
      a network security config (mirrors the iOS ATS policy)
- [ ] 1.6 Package structure per FEATURES.md §1
- [ ] 1.7 Detekt + ktlint + a CI-ready `./gradlew check`

**Done when:** empty app installs and launches edge-to-edge on a device and an emulator.

---

## Phase 2 — Design system & core UI

- [ ] 2.1 `Color.kt` — port all 18 palette colours verbatim from `Theme.swift`
- [ ] 2.2 `RaceControlColors` (tyre, positive/negative/warning/info, team helpers) via
      `CompositionLocal`; `teamColor(hex: String?)` with red fallback
- [ ] 2.3 Material 3 `darkColorScheme` mapping (primary/background/surface/outline/error);
      **dynamic colour explicitly disabled** — documented deviation
- [ ] 2.4 `Type.kt` — Roboto on the M3 type scale, `sp` throughout, tabular-figures style
      for lap times and positions
- [ ] 2.5 `Dimens.kt` — 4/8/16/24/32dp spacing, 8/14/20/pill radii, `minTouch = 48.dp`
- [ ] 2.6 `TyreCompound` (colour + letter) and `CountryFlag` (country → emoji) ported
- [ ] 2.7 `UiState<T>` sealed interface + `LoadableContent` composable
- [ ] 2.8 `LoadingIndicator`, `ErrorState` (with retry), `EmptyState`
- [ ] 2.9 Components: `SeasonPickerChip`, `DriverAvatar`, `TeamAccentBar`, `PositionBadge`,
      `PointsPill`, `GridDeltaTag`, `TyreBadge`, `RcCard`, `StatCell`, `FavoriteStar`
- [ ] 2.10 `Haptics` wrapper (selection / impact / success)
- [ ] 2.11 Compose previews for every component, light-on-dark, at 1.0× and 2.0× font scale
- [ ] 2.12 Run `check-contrast.py` on every text/background pair; record results

**Verify:** contrast ≥ 4.5:1 for body text, ≥ 3:1 for large text and component boundaries;
every touchable ≥ 48dp; every component has a ripple.

---

## Phase 3 — Networking & data layer

- [ ] 3.1 DTOs for all 22 endpoints (`kotlinx.serialization`, `@SerialName` where needed)
- [ ] 3.2 `JsonValue` equivalent — a custom serializer absorbing string-or-number fields
      (FastF1 vs Ergast inconsistency the iOS app handles with `JSONValue`)
- [ ] 3.3 `RaceControlApi` Retrofit interface — full endpoint parity with `APIClient.swift`
- [ ] 3.4 OkHttp: 45s call / 90s read timeouts (matching iOS), 10 MB response cache
- [ ] 3.5 `TokenProvider` interface + `StaticTokenProvider`; `AuthInterceptor` adding
      `Authorization: Bearer`, with the 401 single-retry behaviour
- [ ] 3.6 `SettingsDataStore` (base URL, all notification prefs) — same keys/defaults as iOS
- [ ] 3.7 `SecureTokenStore` on EncryptedSharedPreferences
- [ ] 3.8 `FavoritesStore` (driver + team id sets) on DataStore, exposed as `Flow`
- [ ] 3.9 Repositories per domain, returning `Result<T>`; error messages ported from
      `APIError.errorDescription` verbatim so wording matches iOS
- [ ] 3.10 Hilt modules wiring it together
- [ ] 3.11 Unit tests against a MockWebServer using captured real backend payloads

**Verify:** every endpoint round-trips a real backend response without a serialization error.
Point the test at a live `./run.sh` backend for at least one season.

---

## Phase 4 — Navigation shell

- [ ] 4.1 `NavigationBar` with the 5 destinations, filled/outlined icon states, visible labels
- [ ] 4.2 Nested nav graphs per tab with `saveState`/`restoreState`
- [ ] 4.3 Type-safe routes (Kotlin serialization-based navigation)
- [ ] 4.4 `AppStateViewModel` — seasons list, selected year, `loadSeasons()` with the same
      static 2018..now fallback the iOS app uses when the server is unreachable
- [ ] 4.5 Predictive back verified on Android 14+
- [ ] 4.6 Deep link `racecontrol://race/{year}/{round}` → race detail
- [ ] 4.7 `NavigationRail` swap at ≥600dp width
- [ ] 4.8 Back-stack test: deep-navigate → rotate → back out; state survives

---

## Phase 5 — Races & Race Detail

- [ ] 5.1 Schedule screen: `LargeTopAppBar`, season chip, overflow → Settings
- [ ] 5.2 Up-next banner (gradient card)
- [ ] 5.3 Race row card: round, flag, name, location, date, SPRINT badge, completed state
- [ ] 5.4 `PullToRefreshBox`
- [ ] 5.5 Race detail header card
- [ ] 5.6 `WeekendScheduleCard` — sessions with local times
- [ ] 5.7 Analysis grid, 3 columns, 8 tiles, gated on `completed`
- [ ] 5.8 Session `PrimaryScrollableTabRow` (Race forced first) + selection haptic
- [ ] 5.9 Results rows: podium gradient badge, accent bar, avatar, name/team, and the
      race-time formatter (leader absolute, others `+gap`, DNF status passthrough)
- [ ] 5.10 Qualifying variant showing best of Q3/Q2/Q1
- [ ] 5.11 Loading / error+retry / empty on both screens

**Verify side by side against the iOS build** on the same race, same season — positions,
gaps, points and grid deltas must match exactly.

---

## Phase 6 — Drivers, Teams, Standings, Circuits

- [ ] 6.1 Drivers list + Material 3 `SearchBar` (name / code / number)
- [ ] 6.2 Favourites pinned to top; `IconToggleButton` with TalkBack state description
- [ ] 6.3 Driver detail: stats, form sparkline (`Canvas`), per-round results
- [ ] 6.4 Head-to-head: two pickers + comparison table
- [ ] 6.5 Teams list and detail with livery colours and rosters
- [ ] 6.6 Standings `PrimaryTabRow` — Drivers / Teams / Progress / Reliability
- [ ] 6.7 Standings rows with gap-to-leader bars and win counts
- [ ] 6.8 Standings evolution chart (`RcLineChart` multi-line, `core/ui/RcCharts.kt`)
- [ ] 6.9 Reliability stacked bars (`Canvas`) per driver and team
- [ ] 6.10 Circuits list with Raced/Upcoming status
- [ ] 6.11 Circuit detail: stats, fastest lap, podium, quick actions
- [ ] 6.12 `TrackMapRich` on `Canvas` — outline path, rotation, numbered corners,
      speed-coloured trace, DRS zones, pinch-zoom and pan

---

## Phase 7 — Replay & Analysis

- [ ] 7.1 Replay: lap header with animated counter (reduce-motion aware)
- [ ] 7.2 Running order `LazyColumn`, stable keys, `animateItem` position changes
- [ ] 7.3 Movement indicators vs previous lap
- [ ] 7.4 Transport controls — slider, first, −5, play/pause, +5, last; all 48dp, all labelled
- [ ] 7.5 Speed segmented buttons (0.5/1/2/4×), tick = `0.9s / speed`
- [ ] 7.6 Coroutine playback loop, cancelled on lifecycle STOP; keep-screen-on while playing
- [ ] 7.7 Telemetry: driver `FilterChip` multi-select (max 3), speed/throttle/gear traces
- [ ] 7.8 Telemetry lap replay — moving dot on mini map, live readouts, chart playhead
- [ ] 7.9 Two-driver comparison summary
- [ ] 7.10 LTTB downsampling before charting (cap ~800 points per trace)
- [ ] 7.11 Lap times chart, outlier toggle, All/None
- [ ] 7.12 Strategy stint timeline
- [ ] 7.13 Qualifying Q1/Q2/Q3 with gap-to-pole
- [ ] 7.14 Weather stat cards
- [ ] 7.15 Retirements grouped by cause

**Verify:** replay playback holds 60fps on a mid-range device (Pixel 6a class) with a
20-driver field; profile with Macrobenchmark, not by eye.

---

## Phase 8 — Settings & Notifications

- [ ] 8.1 Settings screen (full destination, not a sheet), saving on change
- [ ] 8.2 Backend URL field + Test Connection (health probe, then authenticated probe,
      distinguishing unreachable / unhealthy / token rejected)
- [ ] 8.3 Token field with visibility toggle → EncryptedSharedPreferences
- [ ] 8.4 Security section text explaining the token model (replacing App Attest)
- [ ] 8.5 Reset to default
- [ ] 8.6 Notification toggles — master, three timings, four session types, iOS defaults
- [ ] 8.7 `POST_NOTIFICATIONS` runtime permission flow with a denied-state explanation
- [ ] 8.8 Exact-alarm permission check with graceful inexact fallback
- [ ] 8.9 `NotificationChannel` + `SessionReminderScheduler` (≤50 alarms, soonest first)
- [ ] 8.10 `BOOT_COMPLETED` receiver re-scheduling alarms
- [ ] 8.11 Daily `WorkManager` refresh of the reminder window
- [ ] 8.12 Notification tap → deep link to the race
- [ ] 8.13 About section

**Verify:** set a reminder 2 minutes out, force-stop, reboot, confirm it still fires.

---

## Phase 9 — Polish & platform compliance

- [ ] 9.1 Adaptive launcher icon + monochrome (themed icons), splash screen API
- [ ] 9.2 All strings in `strings.xml`, plurals where needed
- [ ] 9.3 TalkBack pass over every screen; fix unlabelled controls
- [ ] 9.4 200% font scale pass; fix clipping
- [ ] 9.5 Landscape + split-screen pass
- [ ] 9.6 Offline behaviour: cached render + "showing cached data" banner
- [ ] 9.7 Reduce-motion honoured everywhere
- [ ] 9.8 R8/ProGuard rules; verify release build; strip all logging
- [ ] 9.9 Baseline Profile for startup and scroll performance
- [ ] 9.10 README for the Android app mirroring the iOS section

---

## Phase 10 — Verification

- [ ] 10.1 Unit tests: serializers, lap-time and gap formatting, reminder scheduling logic,
      outlier filtering, LTTB downsampling
- [ ] 10.2 Compose UI tests for the four `UiState` branches on each screen
- [ ] 10.3 Screenshot tests for the design-system components
- [ ] 10.4 `accessibility-audit.sh` and `validate-touch-targets.sh` from the design skill
- [ ] 10.5 `mobile_audit.py` from the mobile-design skill
- [ ] 10.6 **Parity matrix** — every iOS screen against its Android counterpart, same data,
      differences either justified in FEATURES.md or filed as bugs
- [ ] 10.7 Confirm `git status` shows zero changes under `RaceControlApp/` and `backend/`
- [ ] 10.8 Macrobenchmark: cold start, schedule scroll, replay playback

---

## Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Telemetry payloads are large (thousands of points × 8 channels) | Jank, OOM on low-end devices | LTTB downsample in the repository layer, before Compose ever sees it |
| ~~Vico may not support the telemetry playhead overlay cleanly~~ (materialized: Vico was dropped before implementation) | Rework mid-phase | Resolved by building the playhead as a plain `Canvas` overlay on the in-house `RcLineChart`, which owns its own drawing and composes cleanly with an overlay |
| Backend cold FastF1 loads take 30s+ | Looks broken on mobile | 45s timeout (as iOS), plus a "first load for this race is slow" hint after 8s |
| Exact alarms are restricted on Android 14+ | Reminders silently don't fire | Permission check + inexact fallback + surface the state in Settings |
| Team colours arrive as arbitrary hex | Contrast failures on dark surfaces | Luminance check; lighten below a threshold before using as text colour |
| `JSONValue` string-or-number ambiguity | Runtime crashes on odd seasons | Permissive custom serializer + a test sweeping every season 2018→now |

---

## Progress

| Phase | Status |
|---|---|
| 0 — Decisions | ✅ resolved |
| 1 — Scaffold | ✅ built |
| 2 — Design system | ✅ built |
| 3 — Networking | ✅ built (all 22 endpoints + health probe) |
| 4 — Navigation | ✅ built |
| 5 — Races | ✅ built |
| 6 — Drivers/Teams/Standings/Circuits | ✅ built |
| 7 — Replay/Analysis | ✅ built |
| 8 — Settings/Notifications | ✅ built |
| 9 — Polish | 🟡 partial — strings, icons, ProGuard, README done; device passes outstanding |
| 10 — Verification | 🟡 partial — unit tests + static checks done; **no compile yet** |

### Outstanding before this can ship

These need a machine with the Android SDK; none could be done here.

- [ ] **First compile.** Written without an SDK or dependency resolver. Let
      Android Studio reconcile `gradle/libs.versions.toml` first.
- [ ] Run against a live backend and diff a race side-by-side with the iOS build
      (positions, gaps, points, grid deltas) — task 10.6.
- [ ] TalkBack pass over every screen.
- [ ] 200% font-scale pass.
- [ ] Reminder lifecycle test: schedule 2 minutes out, force-stop, reboot, confirm it fires.
- [ ] Macrobenchmark: cold start, schedule scroll, replay playback at 60fps.
- [ ] Replace the placeholder launcher icon with final brand artwork.
- [ ] Compose UI tests and screenshot tests (10.2, 10.3).
