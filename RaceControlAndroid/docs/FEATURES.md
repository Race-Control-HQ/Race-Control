# RaceControl for Android — Feature Inventory

Derived by auditing the iOS app (`RaceControlApp/`, 43 Swift files, ~6,300 LOC) and the
FastAPI backend. This document is the **contract**: every iOS behaviour is listed with its
Android equivalent. `TASKS.md` is the build order.

**The iOS app and the backend are not modified by this work.** The Android client consumes
the existing REST API unchanged.

---

## 0. Design checkpoint

Per the `mobile-design` skill, completed before any code:

```
Platform:   Android (phone-first, tablet-tolerant)
Framework:  Kotlin + Jetpack Compose (Material 3)
Files read: mobile-design/SKILL.md, mobile-design-thinking.md, platform-android.md,
            mobile-app-design-standards/SKILL.md

3 Principles I Will Apply:
1. LazyColumn with stable keys + contentType for every list; no Column-in-scroll for
   unbounded data (replay order, results, standings, driver lists).
2. 48dp minimum touch targets and Material ripple on every touchable — the iOS build
   targets 44pt, which is below Android's minimum and must be raised, not copied.
3. Material 3 navigation semantics: NavigationBar (not a copied iOS TabView), TopAppBar
   with overflow, predictive back, edge-to-edge insets.

Anti-Patterns I Will Avoid:
1. Cloning iOS chrome — no segmented controls where Material wants tabs/chips, no
   iOS-style back chevrons, no bottom-sheet-for-everything.
2. API token in SharedPreferences → EncryptedSharedPreferences (mirrors iOS Keychain).
3. Timer-driven recomposition of the whole replay screen → hoisted lap state so only
   the order list recomposes.
```

### Anti-default decisions taken deliberately

| Default I questioned | Decision | Reason |
|---|---|---|
| 5-item bottom nav because iOS has 5 tabs | **Keep 5** | All five are genuinely top-level and equally weighted; Material allows 3–5. A drawer would bury them. |
| FAB on list screens | **No FAB anywhere** | The app is read-only. There is no create action to promote. A FAB with no purpose is cargo-cult Material. |
| Pull-to-refresh everywhere | **Only where iOS has it** (Races, Circuits) + Standings | Elsewhere data is per-navigation and a manual retry button already exists in the error state. |
| Bottom sheet for Settings | **Full screen destination** | Settings is a long form with text entry; a sheet fights the keyboard. |
| Dynamic Color (Material You) | **Opt-out — fixed dark brand theme** | The app's identity is F1 red on OLED black with official team/tyre colours. Wallpaper-derived colour would destroy the team-colour semantics. Documented deviation. |

---

## 1. Global architecture

| Concern | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose + Material 3 |
| Pattern | MVVM, `@MainActor` view models | MVVM, `ViewModel` + `StateFlow` |
| Async state | `Loadable<T>` enum (idle/loading/loaded/failed) | `UiState<T>` sealed interface, same four cases |
| Async | Swift Concurrency (`async/await`, `actor`) | Coroutines + Flow |
| Networking | `URLSession` in an `actor APIClient` | Retrofit + OkHttp + `kotlinx.serialization` |
| DI | Singletons (`.shared`) | Hilt |
| Navigation | `TabView` + `NavigationStack` | `NavigationBar` + Navigation-Compose, type-safe routes |
| Images | `AsyncImage` | Coil 3 `AsyncImage` |
| Charts | Swift Charts | In-house Compose `Canvas` charting layer (`core/ui/RcCharts.kt`) — line/bar via `RcLineChart`, plus track maps and stint timelines. Vico was the original plan but was abandoned before implementation; see `RcCharts.kt`'s doc comment. |
| Global state | `AppState: ObservableObject` (selected season) | `AppStateViewModel` scoped to the activity, exposed via `CompositionLocal` |
| Prefs | `UserDefaults` / `@AppStorage` | DataStore (Preferences) |
| Secrets | Keychain | EncryptedSharedPreferences |
| Haptics | `UIImpactFeedbackGenerator` etc. | `HapticFeedback` / `View.performHapticFeedback` |
| Notifications | `UNUserNotificationCenter`, 64-item cap | `AlarmManager` + `NotificationManagerCompat`, WorkManager refresh |
| Auth | Apple App Attest (+ optional admin token) | **Google Play Integrity** (+ optional admin token) — see §7 |

### Package layout

```
com.owlmedia.racecontrol
├── RaceControlApplication.kt        @HiltAndroidApp
├── MainActivity.kt                  edge-to-edge, ComponentActivity
├── core/
│   ├── design/                      Theme, Color, Type, Dimens, TyreCompound, CountryFlag
│   ├── ui/                          UiState, LoadableContent, LoadingIndicator,
│   │                                ErrorState, EmptyState, common components
│   └── util/                        date/time formatting, lap-time formatting
├── data/
│   ├── remote/                      RaceControlApi (Retrofit), AuthInterceptor, dto/
│   ├── local/                       SettingsDataStore, SecureTokenStore, FavoritesStore
│   └── repository/                  one repository per domain area
├── domain/model/                    Kotlin data classes (JsonValue equivalent)
├── notifications/                   SessionReminderScheduler, BootReceiver, Worker
└── feature/
    ├── schedule/  drivers/  teams/  standings/  circuits/
    ├── racedetail/  replay/  settings/
    └── analysis/  (telemetry, laptimes, strategy, qualifying, weather, retirements)
```

---

## 2. Navigation

**iOS:** `TabView` with 5 tabs, each wrapping its own `NavigationStack`.

**Android:** `NavigationBar` with 5 destinations, each a nested nav graph so back-stack is
preserved per tab (`saveState`/`restoreState`).

| Tab | iOS SF Symbol | Android Material Symbol | Route |
|---|---|---|---|
| Races | `flag.checkered` | `sports_score` | `races` |
| Drivers | `person.2.fill` | `groups` | `drivers` |
| Teams | `car.2.fill` | `directions_car` | `teams` |
| Standings | `trophy.fill` | `emoji_events` | `standings` |
| Circuits | `map.fill` | `map` | `circuits` |

Android-specific requirements not present on iOS:

- **System back** must pop the nested stack, then switch to the start tab, then exit.
- **Predictive back** (Android 14+): opt in via `android:enableOnBackInvokedCallback`.
- **Edge-to-edge** (mandatory Android 15+): `enableEdgeToEdge()`, consume insets in
  scaffolds, `WindowInsets.safeDrawing` on scrollable content.
- **Deep links** planned from day one: `racecontrol://race/{year}/{round}` so session
  reminder notifications open the right race.
- Active tab uses the **filled** icon variant + indicator pill; inactive uses outlined.
- Labels **always visible** (accessibility), never icon-only.

---

## 3. Screens

### 3.1 Races (Schedule) — `ScheduleView.swift`

| Element | Behaviour | Android notes |
|---|---|---|
| Season title | "Season 2026", large title | `LargeTopAppBar`, collapses on scroll |
| Settings entry | Gear, top-**left** | Move to **overflow menu (⋮) top-right** — Android convention; top-left is reserved for back/nav |
| Season picker | Menu of years, top-right | `DropdownMenu` anchored to an outlined chip in the app bar actions |
| "Up next" banner | First non-completed event, red gradient card, flag emoji, tap → detail | Same, `Card` with `Brush.linearGradient` |
| Race rows | Round number, flag, name, location, date, SPRINT badge, completed check, chevron | `Card` (outlined), 12dp radius; drop the chevron (not a Material affordance), keep ripple |
| Pull to refresh | `.refreshable` | `PullToRefreshBox` (Material 3) |
| States | loading / error+retry / empty ("No Races") | Same |

Data: `GET /api/schedule/{year}`

### 3.2 Race Detail — `RaceDetailView.swift`

- Header card: flag emoji, official name, location + country, long-form date.
- **Weekend schedule card** (`WeekendScheduleCard.swift`): each session with name, local
  date/time, and a per-session reminder affordance.
- **Analysis grid** — only if `event.completed`. 3-column grid of 10 tinted tiles:
  Replay, Telemetry, Lap Times, Strategy, Qualifying, Track Map, Weather, Retirements, Flags,
  Race Control.
  Android: `LazyVerticalGrid(GridCells.Fixed(3))`, each tile min 72dp tall, ≥48dp target.
- **Session picker** — segmented control over available sessions (Race / Quali / Sprint /
  Sprint Q / FP1–3), Race forced first. Android: **`PrimaryScrollableTabRow`**, because
  session counts vary 1–6 and Material segmented buttons cap at ~5 and don't scroll.
  Selection triggers a haptic on both platforms.
- **Results table** rows: position badge (gold gradient for podium), team accent bar,
  driver avatar, name, team, then either qualifying Q1/Q2/Q3 best time, or race time
  (leader absolute `m:ss.SSS`, others `+s.SSS` gap), grid delta arrow, points.
  Non-finishers show status text ("DNF", "+1 Lap", "Accident").

Data: `GET /api/results/{year}/{round}/{session}`

### 3.3 Race Replay — `ReplayView.swift`

The most stateful screen.

- Lap counter header with animated numeric transition (respects reduce-motion).
- Running-order list: position, movement triangle (up/down/level vs previous lap), team
  accent bar, driver code, team name, tyre compound badge, lap time.
- Position changes animate as a spring; disabled under reduce-motion.
- Transport: slider (scrub any lap), first lap, −5, play/pause, +5, final lap.
- Speed segmented control: 0.5× / 1× / 2× / 4×. Tick interval = `0.9s / speed`.
- Playback stops on leaving the screen.

Android specifics:
- `LazyColumn` with `key = entry.driver` so Compose animates item movement via
  `Modifier.animateItem()` — the natural Compose equivalent of the SwiftUI transition.
- Reduce-motion: check `ANIMATOR_DURATION_SCALE == 0f`, skip `animateItem` and numeric
  animation.
- Playback loop is a coroutine in the ViewModel tied to `viewModelScope`, cancelled in
  `onCleared` and on lifecycle STOP — no leaked `Timer`.
- Speed control → `SingleChoiceSegmentedButtonRow` (4 options, fits).
- Transport buttons: `IconButton` at 48dp with `contentDescription` for each.
- Keep-screen-on while playing (`FLAG_KEEP_SCREEN_ON`) — an Android nicety the iOS build
  doesn't need because playback is short; flagged as an addition, not a divergence.

Data: `GET /api/replay/{year}/{round}`

### 3.4 Drivers — `DriversView.swift`, `DriverDetailView.swift`, `HeadToHeadView.swift`

- Searchable roster (search by name, code, number), headshot avatars, team colour accent,
  championship position, points, wins.
- Favourites: star toggle, favourites pinned to the top of the list.
- Head-to-head entry point.
- **Driver detail:** headshot, number, team, nationality flag, DOB, season stat cells
  (points / wins / position), **form sparkline** across rounds, full per-round result list.
- **Head-to-head:** two driver pickers, compares points, wins, podiums, poles, best finish,
  DNFs, and direct race/qualifying win records.

Android: `SearchBar` (Material 3 docked) rather than iOS `.searchable`; favourite star is a
48dp `IconToggleButton` with state description for TalkBack; sparkline is a Compose `Canvas`.

Data: `GET /api/drivers/{year}`, `/api/drivers/{year}/{driverId}`, `/api/compare/{year}/{d1}/{d2}`,
`/api/racedrivers/{year}/{round}`

### 3.5 Teams (Constructors) — `TeamsView.swift`, `TeamDetailView.swift`

Standings-ordered cards with livery colour, position, points, wins, and the driver line-up
(avatars + names). Favourite toggle. Detail shows the same plus full roster.

Data: `GET /api/teams/{year}`, `/api/teams/{year}/{teamId}`

### 3.6 Standings — `StandingsView.swift` + 2 sub-views

Four modes behind a segmented control: **Drivers · Teams · Progress · Reliability**.

- **Drivers / Teams:** rank, name, team, points, wins, and a gap-to-leader progress bar.
- **Progress** (`StandingsEvolutionView`): multi-line cumulative points chart by round.
- **Reliability** (`ReliabilityView`): per-driver and per-team finish rate with a stacked
  bar broken down by mechanical / accident / disqualified / other.

Android: 4 modes exceeds comfortable segmented-button width on compact screens →
**`PrimaryTabRow`**. Both Progress and Reliability charts render via the in-house
`core/ui/RcCharts.kt` Canvas layer (`RcLineChart` for Progress, stacked bars for
Reliability) — Vico was never adopted.

Data: `/api/standings/drivers/{year}`, `/api/standings/constructors/{year}`,
`/api/standings-evolution/{year}`, `/api/reliability/{year}`

### 3.7 Circuits — `CircuitsView.swift`, `CircuitDetailView.swift`, `TrackMapRich.swift`

- Season venues in calendar order, each tagged **Raced** or **Upcoming**. Only raced
  circuits are tappable.
- Detail: rich **track map**, length (km/mi), corner count, total laps, fastest lap
  (driver, team, time, compound), podium, and quick actions (open replay / full results).
- `TrackMapRich` renders the outline plus numbered corner markers, rotated by the
  backend-supplied `rotation`, and colours the trace by speed with DRS zones highlighted.

Android: the entire track map is a Compose `Canvas` — `Path` from the outline points,
`drawIntoCanvas` for corner labels, `graphicsLayer { rotationZ = rotation }`. Pinch-zoom
and pan via `Modifier.transformable`.

Data: `/api/circuits/{year}`, `/api/circuit/{year}/{round}`

### 3.8 Analysis screens

| Screen | Content | Android chart approach |
|---|---|---|
| **Telemetry** | Multi-select up to 3 drivers; speed / throttle / gear traces over lap distance; a lap "replay" sweeping a dot along a mini track map with live speed/gear readouts and a chart playhead; 2-driver comparison summary | `RcLineChart` traces sharing an x-axis (`core/ui/RcCharts.kt`); playhead as a `Canvas` overlay; mini map as `Canvas` |
| **Lap Times** | Multi-driver lap-time evolution; "hide outliers" toggle; All/None chips | `RcLineChart` multi-series line |
| **Strategy** | Per-driver stint timeline, compound colours, pit-stop count | `Canvas` horizontal stacked bars |
| **Qualifying** | Q1/Q2/Q3 breakdown, gap to pole, elimination shading | Table + `LinearProgressIndicator` |
| **Weather** | Air/track temp (+ max), humidity, pressure, wind, rainfall flag | Stat cards |
| **Retirements** | Non-finishers grouped by cause (mechanical / accident / DSQ / other) | Grouped list |
| **Flags** | Chronological flag/safety-car periods (collapsed) with the raw race-control timeline underneath; bands the Lap Times chart and badges the Telemetry lap in view | See §3.10 below |

Driver selection chips → Material 3 `FilterChip` (already the right primitive).

Data: `/api/telemetry/...`, `/api/telemetry-compare/...`, `/api/laptimes/...`,
`/api/strategy/...`, `/api/weather/...`, `/api/retirements/...`, `/api/flags/...`

### 3.10 Flags — `FlagsView.swift` → `FlagsScreen.kt`

New screen (ninth analysis tile) wrapping `GET /api/flags/{year}/{round}?session=R`, which
returns a raw race-control event timeline plus a collapsed `periods` list (inclusive lap
ranges per `YELLOW` / `DOUBLE_YELLOW` / `RED` / `SC` / `VSC`).

| Element | iOS | Android |
|---|---|---|
| Primary list | `VStack` of `FlagPeriodRow`: icon, colour, type label, lap range, reason | `LazyColumn` of the same row shape — matches the list-of-cards convention every other analysis screen (Retirements, Strategy) already uses, rather than introducing a new pattern for one screen |
| Raw timeline | `DisclosureGroup` ("RACE CONTROL TIMELINE (N)") | No native disclosure-group primitive in Material 3, so this is a clickable `SectionHeader` row with a rotating `ExpandMore` chevron (`animateFloatAsState`) toggling a boolean; expanded rows are lazy list items, not a separately-scrolling nested list |
| Empty state | `EmptyStateView` (`flag.checkered`) | `EmptyState` (`Icons.Filled.EmojiFlags`) — reuses the shared empty-state composable per the existing pattern, no bespoke view |
| Colour | Five colours from `FlagStyle` (Swift) | Ported to `RcPalette` (`FlagYellow` #FFD500, `FlagDoubleYellow` #FF9500, `FlagRed` #FF453A reusing the negative hue, `FlagSafetyCar` #FF6A00, `FlagVirtualSafetyCar` #AF52DE) + a Kotlin `FlagStyle` object mirroring the Swift one, alongside `TyreCompound` in `core/design/` — same "colour is data" reasoning as tyre compounds |
| Icon | SF Symbol per type | `Icons.Filled.Flag` for the three flag types, `Icons.Filled.DirectionsCar` for SC/VSC (no direct Material equivalent of a safety-car symbol) |

**Two Android-only overlay additions, not present on iOS** (the task explicitly asked for
these beyond parity):

- **Lap Times chart bands** — `LapTimesViewModel` fetches `/api/flags` alongside
  `/api/laptimes` (parallel coroutine, best-effort: a failed flags fetch just means no bands,
  never a second error state on top of the lap-time one). `RcLineChart` gained a `bands:
  List<ChartBand>` parameter, drawn as translucent (`alpha = 0.16f`) rects behind the grid so
  the line traces stay legible; each period is widened ±0.5 laps so a single-lap period reads
  as a visible band rather than a hairline.
- **Telemetry lap badge** — `TelemetryViewModel` fetches flags alongside the driver list;
  `TelemetryScreen`'s per-driver "LAP" row shows an inline `FlagLapBadge` ("Safety Car (lap
  20)") when `trace.lapNumber` falls inside a period, via a `List<FlagPeriodDto>.periodContaining(lap)`
  helper in `AnalysisDtos.kt`.

`periodContaining`/`FlagPeriodDto.contains` is pure Kotlin with no Android dependency, so it
has a JVM unit test (`FlagPeriodTest.kt`) alongside `LapTimeFormatTest.kt`, covering inclusive
boundaries, no-match laps, null laps, and case-insensitive backend type parsing.

### 3.11 Race Control — `RaceControlScreen.kt`

Tenth analysis tile, complementary to Flags rather than a duplicate of it: Flags only
surfaces FLAG/SAFETY-CAR category messages (collapsed into periods); this screen wraps
`GET /api/racecontrol/{year}/{round}?session=R`, which returns the **complete**
chronological race-control log — DRS enabled/disabled, car events, and "Other" stewards'
messages (investigations, penalties, reprimands) that Flags never shows, e.g. "TURN 3
INCIDENT INVOLVING CARS 16 AND 55 UNDER INVESTIGATION" or "CAR 44 (HAM) - 5 SECOND TIME
PENALTY".

| Element | Detail |
|---|---|
| DTOs | `RaceControlResponseDto` / `RaceControlMessageDto` in `AnalysisDtos.kt`, mirroring `FlagsResponseDto`/`FlagEventDto`. `category` (`Flag`/`SafetyCar`/`Drs`/`CarEvent`/`Other`) is parsed into a `RaceControlCategory` enum via `RaceControlMessageDto.categoryType` |
| List | `LazyColumn` of `RaceControlMessageRow`: category icon (tinted), lap chip, driver-code chip when present, message text — same list-of-cards shape as `FlagEventRow`/Retirements/Strategy |
| Filter | Chips row (`FilterChip`, same primitive as the Lap Times driver picker): All / Flags / Safety Car / DRS / Incidents, where Incidents groups `CarEvent` + `Other` — five buckets read better than five-plus separate category chips |
| Icons | `Icons.Filled.Flag` (Flag), `Icons.Filled.DirectionsCar` (SafetyCar, matching `FlagStyle`), `Icons.Filled.Bolt` (Drs), `Icons.Filled.ReportProblem` (CarEvent), `Icons.Filled.Article` (Other) |
| Colour | Flag/SafetyCar rows reuse `FlagStyle.color(FlagPeriodType)` — the raw `flag` text is normalized (`"DOUBLE YELLOW"` → `DOUBLE_YELLOW`) and passed through the existing `FlagPeriodType.from` parser — for visual consistency with the Flags screen; DRS/CarEvent/Other rows use a neutral secondary tint since they aren't part of the flag vocabulary |
| Empty state | `EmptyState` (`Icons.Filled.Article`) — no messages at all, and a second lighter-weight empty message when a filter matches nothing |
| Tile | `Icons.Filled.Article`, `RcTheme.colors.info` tint — deliberately not `Icons.Filled.Flag` again, to stay visually distinct from the Flags tile beside it |

Data: `GET /api/racecontrol/{year}/{round}?session=R`

### 3.9 Settings — `SettingsView.swift`

| Section | iOS | Android |
|---|---|---|
| Backend Server | URL text field + "Test Connection" with status icon | Same; `OutlinedTextField`, `KeyboardType.Uri` |
| Security | App Attest status row | Explanatory text describing Play Integrity (see §7); no live status row — `GET /playintegrity/status` exists on the backend for a future one |
| Admin Token | Secure field, stored in Keychain | `OutlinedTextField` with visibility toggle, EncryptedSharedPreferences |
| Reset to default | Button | Same |
| Notifications | Master toggle | Same + `POST_NOTIFICATIONS` runtime permission (API 33+) and exact-alarm handling |
| Remind me | Day before / 1 hour / 15 min | Same |
| For these sessions | Practice / Qualifying / Sprint / Race | Same |
| About | Data source, app version | Same |
| Save/Cancel | Nav bar buttons | Android saves **immediately on change** (platform convention); no Save button |

Connection test: probes `GET /api/health` (unauthenticated) then `GET /api/seasons` with the
token, so a bad token is surfaced here rather than as errors across the app.

---

## 4. Shared components

| iOS | Android |
|---|---|
| `SeasonPicker` | `SeasonPickerChip` — `AssistChip` + `DropdownMenu` |
| `DriverAvatar` | Coil `AsyncImage`, circular, team-tinted initials fallback |
| `TeamAccentBar` | 4dp rounded `Box` |
| `PositionBadge` | 34dp box, gold gradient for P1–P3 |
| `PointsPill` | `Surface(shape = CircleShape)` |
| `GridDeltaTag` | Icon + text, green up / red down / grey level |
| `TyreBadge` | Circle with compound letter, official colour |
| `Card` | Material 3 `OutlinedCard` |
| `StatCell` | Column, value + uppercase label |
| `FavoriteStar` | `IconToggleButton`, 48dp |
| `LoadableView` | `LoadableContent<T>` composable |
| `LoadingIndicator` / `ErrorState` / `EmptyStateView` | Same three, Material styling |
| `CountryFlag` | Country name → regional-indicator emoji (port the lookup table verbatim) |
| `Haptics` | `HapticFeedbackType.LongPress` / `TextHandleMove` equivalents |

---

## 5. Design system port

The iOS `Theme` enum maps 1:1 to a Compose theme. Colours are **carried over exactly** —
they encode brand and F1 semantics.

```
racingRed      #E10600    background      #0A0A0C     positive  #30D158
racingRedDim   #B00500    surface         #16161A     negative  #FF453A
racingRedText  #FF5A50    surfaceElevated #202027     warning   #FF9F0A
stroke         white 8%   textPrimary     #F2F2F5     info      #64D2FF
                          textSecondary   #A0A0AA
                          textTertiary    #8A8A94

Tyres: soft #F0402C · medium #F5D000 · hard #EBEBEB · inter #40B14B · wet #1E6FE0
```

Mapped onto Material 3 `ColorScheme`: `primary` = racingRed, `background` = #0A0A0C,
`surface` = #16161A, `surfaceContainerHigh` = #202027, `outline` = stroke, `error` = negative.
Semantic extras (tyre colours, positive/warning/info, team colours) live in a custom
`RaceControlColors` exposed via `CompositionLocal`, since M3 has no slot for them.

Spacing (4/8/16/24/32dp) and radii (8/14/20dp + pill) transfer unchanged — both platforms
use an 8pt/8dp grid. **Corner radius**: iOS `md` is 14pt; Material 3 cards default to 12dp.
Keeping 14dp is a deliberate, harmless brand carry-over.

Typography: SF Pro → **Roboto**, mapped to the Material type scale, all sizes in `sp`.
Monospaced digits (used heavily for lap times and positions) via
`FontFeatureSetting("tnum")` on Roboto.

**Dark only**, matching iOS (`preferredColorScheme(.dark)`). No light theme; the palette is
OLED-tuned by design.

---

## 6. Notifications

iOS schedules up to 60 local notifications per launch (under the 64 cap), rebuilding a
rolling window. Android has no such cap but has stricter background rules.

| Aspect | iOS | Android |
|---|---|---|
| Mechanism | `UNCalendarNotificationTrigger` | `AlarmManager.setExactAndAllowWhileIdle` per reminder |
| Cap | 60 pending | ~50 kept, to bound alarm slots |
| Refresh | On app launch | On launch **and** a daily `WorkManager` job — Android users may not open the app for weeks |
| Reboot | N/A (survives) | `BOOT_COMPLETED` receiver re-schedules — Android alarms do **not** survive reboot |
| Permission | Requested on toggle | `POST_NOTIFICATIONS` runtime permission (API 33+); `SCHEDULE_EXACT_ALARM` handling with an inexact fallback |
| Channel | N/A | `NotificationChannel` "Session reminders", default importance |
| Tap action | Opens app | Deep link to the race detail screen |

Reminder types (day before 09:00 / 1 hour / 15 min) and session filters (practice /
qualifying / sprint / race) are identical, including the same defaults: day-before ON,
1-hour OFF, 15-min ON; practice OFF, qualifying/sprint/race ON.

---

## 7. Authentication — Play Integrity (updated; no longer a divergence)

The iOS app uses **Apple App Attest** to prove requests come from a genuine install, with an
admin token as break-glass. Android now has a real equivalent: **Google Play Integrity**.

**Why this changed:** a static admin token shipped in a Play Store APK is not a secret — Android
binaries are trivially decompiled, so any string embedded in the app is effectively public the
moment it ships. That's fine for a token you hand out yourself for local/dev use, but wrong for
the credential a public Play Store build relies on for every user. Play Integrity has nothing
embeddable to extract: Google Play services vouches for the app/device at request time, verified
server-side against Google's attestation service, the same shape of trust App Attest already
gives the iOS build.

- `PlayIntegrityTokenProvider` requests a nonce from the backend
  (`GET /playintegrity/challenge`), asks Play Integrity to vouch for the app tied to that nonce,
  and exchanges the result for a short-lived JWT (`POST /playintegrity/verify`) — the same shape
  of flow as iOS's attest/assert/token cycle, adapted to how Play Integrity actually works (no
  persistent per-device key to renew; a fresh integrity token is requested each time the cached
  JWT expires).
- The JWT is cached encrypted (`PlayIntegrityTokenStore`) so ordinary requests don't pay for a
  fresh Play Integrity round trip, and so the app stays well under Play Integrity's per-app quota.
- `CompositeTokenProvider` prefers a manually-entered Settings token (admin/dev override, or a
  local `./run.sh` backend with no auth at all) and falls back to Play Integrity — the same
  precedence the iOS app already documents (admin token first, attestation otherwise).
- Backend: `backend/playintegrity.py` mirrors `attest.py`'s structure (challenge store, verdict
  checks, JWT issuance with the same shared secret/TTL) and is wired into `main.py` as a third,
  fully independent auth mechanism alongside App Attest and the static token — enabling one never
  requires or affects the others. **No changes to `attest.py`** — the iOS flow is untouched.
- `PLAY_INTEGRITY_ENABLED` defaults to off, exactly like `APP_ATTEST_ENABLED`, so `./run.sh` with
  no env vars set stays fully open for local development.

**Setup this doesn't cover** (external, needs a human with Google Cloud/Play Console access —
see `backend/.env.example` for the full list): a Google Cloud project with the Play Integrity API
enabled, that project linked to the app in Play Console, a service account granted access with a
downloaded JSON key, and (recommended) the app's release signing certificate digest for pinning.
None of that can be done from code.

`AuthInterceptor` mirrors the iOS 401-retry behaviour: on a 401 it invalidates whatever's cached
and asks for one fresh token before giving up, which now actually re-verifies against Play
Integrity rather than being a no-op the way it was with a static-token-only design.

---

## 8. Behaviours the Android build must add

Not iOS features — Android platform requirements that have no iOS counterpart.

1. **Configuration changes** — rotation and split-screen must not drop loaded state
   (`ViewModel` + `SavedStateHandle`).
2. **Process death** — restore selected season, tab, and scroll position via
   `rememberSaveable` / `SavedStateHandle`.
3. **Back handling** — nested graphs, predictive back, no back hijacking.
4. **Edge-to-edge + insets** — required for Android 15+.
5. **Font scaling to 200%** — no fixed-height rows containing text; verified in tests.
6. **TalkBack** — content descriptions on every icon-only control; the replay list
   announces position changes via `liveRegion`.
7. **Reduce-motion** — honour `ANIMATOR_DURATION_SCALE == 0`.
8. **Offline** — OkHttp cache (10 MB, 6h) so the schedule and standings render from cache
   when offline, with a "showing cached data" banner. The iOS app has no cache; this is an
   addition justified by Android's more variable connectivity and the backend's slow
   cold FastF1 loads.
9. **Low-end devices** — telemetry traces can exceed 5,000 points; downsample before
   charting (LTTB) rather than handing everything to the Canvas chart layer.

---

## 9. Explicitly out of scope

- Any change to `RaceControlApp/` or `backend/`.
- Play Integrity / server-side Android attestation.
- Widgets, Wear OS, Android Auto.
- Tablet-optimised list-detail layouts (the app will *work* on tablets and adopt a
  `NavigationRail` at ≥600dp, but two-pane canonical layouts are a later phase).
- Localisation beyond English (strings are externalised to `strings.xml` so it's possible).
