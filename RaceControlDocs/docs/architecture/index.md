# Architecture

RaceControl is one backend feeding three independent clients, plus a project site and these docs.

```
 iOS app ─────────────┐  HTTP/JSON (App Attest)
 SwiftUI · MVVM       │
                      │
 Android app ─────────┤  HTTP/JSON (Play Integrity)
 Compose · MVVM+Hilt  │
                      ▼
 Browser ──► Web app (BFF) ──► Backend ──► FastF1 ──► F1 live-timing API
             Next.js           FastAPI     disk       Ergast / Jolpica DB
             (API_TOKEN)       response    cache
                               cache

 Browser ──► Project site      links only, no data calls
 Browser ──► Docs              static, no data calls
```

## Why there is a backend at all

FastF1 is a Python library, not a hosted web service. Something has to run it. The backend runs FastF1 on a server and exposes clean JSON over REST, and all three apps consume it.

## The pieces

**[Backend](/architecture/backend).** A thin serialisation layer (`fastf1_service.py`) converts pandas `Timedelta`, `Timestamp` and `NaN` values into JSON-safe output, wrapped by a small cached FastAPI app (`main.py`). Derived analysis lives in `analytics_service.py`.

**[iOS app](/architecture/ios).** MVVM. Each feature has a `@MainActor` view model exposing a `Loadable` state. `APIClient` is an `actor`.

**[Android app](/architecture/android).** MVVM with Hilt dependency injection. Each feature has a `ViewModel` exposing a `UiState` `StateFlow`. Networking is Retrofit and OkHttp over a `RaceControlRepository` returning `Result<T>`.

**[Web app](/architecture/web).** Next.js App Router. Server Components fetch the backend directly through a server-only helper. Client components go through a same-origin proxy route, so the backend's `API_TOKEN` never reaches the browser.

**Project site.** A stateless Next.js app with no backend credentials. It links out to the live backend's Swagger docs, the deployed web app and these docs rather than calling the API itself.

**Docs.** A static VitePress site served by nginx.

## Shared decisions

**One JSON contract.** The clients are built independently but read the same endpoints. The derived analytics arrive [chart-ready](/api/analytics#chart-ready-by-design) so the three chart stacks cannot disagree on the numbers.

**A permissive scalar type.** FastF1 emits numbers where Ergast/Jolpica emits strings for the same fields. iOS has `JSONValue` and Android has `JsonValue` to absorb that, so the UI can never crash on a type mismatch.

**No accounts.** The data is public F1 history. Access is bound to genuine installs through [device attestation](/architecture/authentication), not to users.

**The same four states everywhere.** Every screen handles idle, loading, loaded and failed, with an error-and-retry and an empty state.

**One palette.** The colours are carried across all three clients exactly, because they encode F1 semantics. See the [Design System](/architecture/design-system).
