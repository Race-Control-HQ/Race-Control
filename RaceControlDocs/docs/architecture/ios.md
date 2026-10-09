# iOS App

A native SwiftUI app in `RaceControlApp/`, targeting iOS 17 and later.

## Pattern

MVVM. Each feature has a `@MainActor` view model exposing a `Loadable<T>` state with four cases: idle, loading, loaded and failed. Views switch on that state, so loading, error-with-retry and empty states are consistent on every screen.

| Concern | Approach |
|---|---|
| UI | SwiftUI |
| Async | Swift Concurrency (`async`/`await`, `actor`) |
| Networking | `URLSession` in an `actor APIClient` |
| Dependency injection | Singletons (`.shared`) |
| Navigation | `TabView` with five tabs, each wrapping its own `NavigationStack` |
| Images | `AsyncImage` |
| Charts | Swift Charts |
| Global state | `AppState: ObservableObject` (selected season) |
| Preferences | `UserDefaults` / `@AppStorage` |
| Secrets | Keychain |
| Notifications | `UNUserNotificationCenter` |
| Auth | Apple App Attest, plus an optional admin token |

## Networking

`APIClient` is an `actor`. The base URL is `AppConfig.apiBaseURL` in `RaceControlApp/RaceControl/Networking/APIClient.swift`.

A permissive `JSONValue` type absorbs the fact that FastF1 emits numbers where Ergast emits strings for the same fields.

On a 401, the client invalidates its cached token and asks for one fresh token before giving up.

## Notifications

The app schedules up to 60 local notifications per launch, under the system's cap of 64 pending, and rebuilds a rolling window each time it starts.

Reminder types are the day before at 09:00, one hour before and 15 minutes before. Defaults: day-before on, one-hour off, 15-minute on; practice off, qualifying, sprint and race on.

## Authentication

App Attest is wired through `RaceControl.entitlements` (`com.apple.developer.devicecheck.appattest-environment`, referenced by `CODE_SIGN_ENTITLEMENTS`). The app token is cached in the Keychain.

App Attest does not work in the Simulator. There the app falls back to the admin token from Settings, or to an open local server.

See [Device Attestation](/architecture/authentication).

## Project file

The project uses Xcode 16's synchronized file groups, so new Swift files are picked up without editing the project. An XcodeGen spec is provided for rebuilding it from scratch. See [Run the iOS App](/getting-started/ios#regenerating-the-xcode-project).

## Design

Built against Apple's Human Interface Guidelines: a dark-first OLED-tuned palette, 44pt or larger touch targets, Dynamic Type, semantic SF Symbols and tab-bar navigation. See the [Design System](/architecture/design-system).
