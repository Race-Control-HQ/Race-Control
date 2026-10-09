# Android App

A native Kotlin and Jetpack Compose app in `RaceControlAndroid/`, built against Material 3 with `minSdk` 26 (Android 8).

It is a counterpart to the iOS app, not a port. Same backend, same data, same five sections, with Android conventions where they differ from Apple's.

## Pattern

```
Compose UI ── StateFlow ── ViewModel ── RaceControlRepository ── Retrofit ── FastAPI
   Material 3      Hilt                    Result<T>            OkHttp cache
```

MVVM with Hilt dependency injection. Each feature has a `ViewModel` exposing a `UiState<T>` `StateFlow`, a sealed interface with the same four cases as the iOS `Loadable<T>`.

| Concern | Approach |
|---|---|
| UI | Jetpack Compose and Material 3 |
| Async | Coroutines and Flow |
| Networking | Retrofit, OkHttp and `kotlinx.serialization` |
| Dependency injection | Hilt |
| Navigation | `NavigationBar` and Navigation-Compose with type-safe routes |
| Images | Coil 3 `AsyncImage` |
| Charts | In-house Compose `Canvas` layer (`core/ui/RcCharts.kt`) |
| Global state | `AppStateViewModel` scoped to the activity, exposed through a `CompositionLocal` |
| Preferences | DataStore (Preferences) |
| Secrets | EncryptedSharedPreferences |
| Notifications | `AlarmManager` and `NotificationManagerCompat`, with a WorkManager refresh |
| Auth | Google Play Integrity, plus an optional admin token |

## Package layout

```
com.owlmedia.racecontrol
├── core/design/     theme, palette, type, dimens, tyre + flag helpers
├── core/ui/         UiState, loading/error/empty, shared components, charts
├── core/util/       date + lap-time formatting, LTTB downsampling
├── data/remote/     Retrofit API, DTOs, interceptors, JsonValue
├── data/local/      DataStore settings, encrypted token, favourites
├── data/repository/ RaceControlRepository
├── notifications/   scheduler, alarm receiver, boot receiver, refresh worker
└── feature/         schedule · racedetail · replay · drivers · teams ·
                     standings · circuits · analysis · settings
```

`JsonValue` mirrors the iOS `JSONValue`. FastF1 emits numbers where Ergast/Jolpica emits the same field as a string, and a permissive scalar type absorbs that so the UI can never crash on a type mismatch.

## Charts

Charts are drawn on Compose `Canvas` through an in-house layer, `core/ui/RcCharts.kt`: `RcLineChart` for line and bar charts, plus track maps, stint timelines and sparklines. The reasoning is in that file's doc comment.

Telemetry traces can exceed 5,000 points, so they are downsampled with LTTB before charting.

## Authentication

`PlayIntegrityTokenProvider` fetches a one-time nonce from the backend, asks Play Integrity to vouch for the app tied to that nonce, and exchanges the result for a short-lived JWT. The JWT is cached encrypted in `PlayIntegrityTokenStore`, so ordinary requests do not pay for a fresh round trip.

`CompositeTokenProvider` prefers a manually entered Settings token first, then falls back to Play Integrity. That is the same precedence the iOS app uses: admin token first, attestation otherwise.

`AuthInterceptor` mirrors the iOS 401-retry behaviour. On a 401 it invalidates whatever is cached and asks for one fresh token before giving up.

See [Device Attestation](/architecture/authentication).

## Platform requirements with no iOS counterpart

1. **Configuration changes.** Rotation and split-screen must not drop loaded state (`ViewModel` and `SavedStateHandle`).
2. **Process death.** Selected season, tab and scroll position are restored through `rememberSaveable` and `SavedStateHandle`.
3. **Back handling.** Nested graphs, predictive back and no back hijacking.
4. **Edge-to-edge and insets.** Required from Android 15.
5. **Font scaling to 200%.** No fixed-height rows containing text.
6. **TalkBack.** Content descriptions on every icon-only control. The replay list announces position changes through a live region.
7. **Reduce motion.** Honours `ANIMATOR_DURATION_SCALE == 0`.
8. **Offline.** A 10 MB, six-hour OkHttp cache lets the schedule and standings render from cache when offline, with a "showing cached data" banner.
9. **Low-end devices.** Large telemetry traces are downsampled before charting.

## Notifications

Each reminder is an `AlarmManager.setExactAndAllowWhileIdle` alarm, with about 50 kept to bound alarm slots.

Android alarms do not survive a reboot, so a `BOOT_COMPLETED` receiver reschedules them. A daily `WorkManager` job keeps them fresh, because users may not open the app between races. Tapping a reminder deep-links to the race detail screen (`racecontrol://race/{year}/{round}`).

## More detail

- [Platform Differences](/features/platforms) summarises what differs from iOS and why.
- The [Android Feature Inventory](/architecture/android-feature-inventory) is the full screen-by-screen contract.
- [Android Status](/contributing/android-status) records what has been verified on a device.
