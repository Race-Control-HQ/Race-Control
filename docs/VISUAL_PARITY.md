# Cross-platform visual parity checklist

## Why this exists

RaceControl has three independent native rendering stacks (SwiftUI/Swift Charts,
Jetpack Compose/Canvas, Next.js/Recharts) over one shared JSON contract. A 2026 audit
found the platforms had drifted from an older parity assessment in *both* directions —
e.g. iOS's reliability view and Android's weather view were both further along than a
supplied source-of-truth doc claimed — which is exactly the failure mode a shared
checklist and a shared fixture are meant to prevent: it's easy for one platform to
quietly gain (or lose) a capability without anyone updating the other two, or the docs.

This is a **manual** checklist, not automated CI screenshot diffing. Wiring up real
cross-platform screenshot automation (iOS Simulator + Android Emulator + a headless
browser, on a CI runner with all three toolchains) is a separate, larger infrastructure
decision — provisioning cost, runner choice, and flake tolerance all need a deliberate
call, not a default. Use this checklist by hand before shipping a visualization-touching
release; it also works as a code-review reference ("does this change need to happen on
the other two platforms too?").

## Shared fixture

Use **one specific year + round** across all three platforms and the backend tests when
comparing a visualization, so differences are never explainable by "different race, of
course it looks different." Recommended starting point: the current season's most
recent completed race with a safety car or red flag period (gives the flag-bands /
race-control-derived visuals something to render) — check `/api/flags/{year}/{round}`
and `/api/weather/{year}/{round}/R` (rainfall) first and swap fixtures if the first
choice turns out to be a clean, eventless race. Record whichever year/round you land on
in your PR description so reviewers use the same one.

## Per-visualization checklist

For each chart/screen below, confirm it exists on all three platforms it's relevant to
(iOS, Android; web where the RaceControlWeb app has an equivalent page), and that all
three agree on:

- [ ] **Loading state** — present, and not indistinguishable from "no data"
- [ ] **Empty state** — a race/season with nothing to show reads as empty, not broken
- [ ] **Error + retry** — a failed request shows a message and a way to retry
- [ ] **Accessibility label** — a screen-reader user gets a description of what the
      chart shows, not silence (iOS: `.accessibilityLabel` on the `Chart`; Android:
      `contentDescription` via `RcCharts.kt`'s `chartSemantics` helper)
- [ ] **Numbers agree** — same driver, same round, same headline number (points, gap,
      lap time) across platforms, since all three read the same backend endpoint

Visualizations currently in scope (see `RaceControlAndroid/docs/FEATURES.md` §3.8 and
the backend audit's endpoint inventory for the full list):

| Visualization | Backend endpoint | iOS | Android | Web |
|---|---|---|---|---|
| Standings evolution (Progress) | `/api/standings-evolution/{year}` | `StandingsEvolutionView.swift` | `StandingsEvolutionView.kt` (`RcLineChart`) | — |
| Reliability breakdown | `/api/reliability/{year}` | `ReliabilityView.swift` (stacked capsule) | `ReliabilityView.kt` (`StackedBar`) | — |
| Weather timeline | `/api/weather/{year}/{round}/{session}` | `WeatherView.swift` | `WeatherScreen.kt` (`RcLineChart` + rain bands) | — |
| WDC calculator | `/api/wdc-calculator/{year}` | `WdcCalculatorView.swift` | `WdcCalculatorView.kt` | — |
| Telemetry traces | `/api/telemetry/{year}/{round}/{driver}` | `TelemetryView.swift` | `PerformanceChartsScreen.kt` | — |
| Lap times | `/api/laptimes/{year}/{round}` | `LapTimesChartView.swift` | `LapTimesScreen.kt` | — |
| Position chart | `/api/race-trace/{year}/{round}` | `PositionChartView.swift` | — | — |
| Pit stops | `/api/pit-stops/{year}/{round}` | `PitStopsView.swift` | — | — |
| Qualifying sectors | `/api/qualifying-sectors/{year}/{round}` | `QualifyingSectorsView.swift` | — | — |

Rows with a bare `—` are a known gap as of this writing (the audit found the platforms
built independently and don't all cover the same visual surface) — treat a blank cell as
a prompt to check whether it's actually missing or just not yet added to this table,
not as confirmed parity either way.

## When to run this

- Before any release that adds or materially changes a chart/visualization on any
  platform.
- When reviewing a PR that touches `core/ui/RcCharts.kt` (Android), any `Features/Analysis`
  or `Features/Standings` SwiftUI view (iOS), or `analytics_service.py` (backend) — a
  changed chart-ready payload shape is exactly the kind of change that can silently
  desync platforms.
