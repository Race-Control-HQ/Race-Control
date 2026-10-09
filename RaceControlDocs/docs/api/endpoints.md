# Core Endpoints

Served by `fastf1_service.py`. All are `GET`. For exact response schemas, use the [generated Swagger docs](/api/index#interactive-documentation).

## Service

| Path | Description |
|---|---|
| `/` | Service banner: name, status and the path to the docs. |
| `/api/health` | Liveness probe. Deliberately unauthenticated so the platform can poll it. |

## Season and schedule

| Path | Description |
|---|---|
| `/api/seasons` | Available seasons (2018 to present). |
| `/api/schedule/{year}` | Race calendar for a season, in round order. |

## Results and standings

| Path | Description |
|---|---|
| `/api/results/{year}/{round}/{session}` | Classification for a session (`R`, `Q`, `S`, `SQ`, `FP1`, `FP2`, `FP3`). |
| `/api/standings/drivers/{year}` | Driver championship standings. |
| `/api/standings/constructors/{year}` | Constructor championship standings. |
| `/api/standings-evolution/{year}` | Round-by-round cumulative championship points per driver. |
| `/api/wdc-calculator/{year}` | Drivers' championship calculator: each driver's points against the maximum still available, and whether the title is decided. |
| `/api/reliability/{year}` | Season DNF breakdown per driver and team. |

`/api/wdc-calculator/{year}` takes an optional `through_round` query parameter to view the championship as it stood after an earlier round. Out-of-range values are clamped rather than rejected.

## Drivers and teams

| Path | Description |
|---|---|
| `/api/drivers/{year}` | Season drivers, with headshots, teams and points. |
| `/api/drivers/{year}/{driverId}` | Driver detail and per-round results. |
| `/api/teams/{year}` | Constructors, with rosters and colours. |
| `/api/teams/{year}/{teamId}` | Team detail. |
| `/api/compare/{year}/{d1}/{d2}` | Two-driver season head-to-head. |
| `/api/racedrivers/{year}/{round}` | Driver list for a race, for pickers. |

## Circuits

| Path | Description |
|---|---|
| `/api/circuits/{year}` | Circuits visited that season. |
| `/api/circuit/{year}/{round}` | Track outline and corners, length and fastest lap. |

## Replay and telemetry

| Path | Description |
|---|---|
| `/api/replay/{year}/{round}` | Lap-by-lap running order for the replay. |
| `/api/replay-positions/{year}/{round}` | Per-driver car X/Y positions sampled across each lap, for animating cars around the track outline during replay. |
| `/api/laptimes/{year}/{round}` | Per-driver lap-time series, for the evolution chart. |
| `/api/strategy/{year}/{round}` | Tyre stints and pit-stop counts per driver. |
| `/api/telemetry/{year}/{round}/{driver}` | Fastest-lap telemetry trace. |
| `/api/telemetry-compare/{year}/{round}?d1=&d2=` | Two-driver telemetry overlay. |

Pair `/api/replay-positions/{year}/{round}` with the track outline from `/api/circuit/{year}/{round}`.

## Race conditions

| Path | Description |
|---|---|
| `/api/weather/{year}/{round}/{session}` | Session weather summary plus the full cached weather timeline. |
| `/api/flags/{year}/{round}?session=R` | Track flags and safety-car history. |
| `/api/racecontrol/{year}/{round}?session=R` | The complete race-control message log, in chronological order. |
| `/api/penalties/{year}/{round}?session=R` | Driver penalties issued during a session. |
| `/api/retirements/{year}/{round}` | Non-finishers, with cause. |

`session` defaults to `R` on all three endpoints that take it.

### Flags

Returns raw race-control events plus collapsed lap-range periods (`YELLOW`, `DOUBLE_YELLOW`, `RED`, `VSC`, `SC`). The periods are inclusive lap ranges, ready for a flags timeline or for banding onto another chart.

### Race control

Where flags returns only flag and safety-car messages, this is everything: flags and safety car, but also DRS enabled and disabled, car events, and investigations, penalties and reprimands. Each message has a `category` of `Flag`, `SafetyCar`, `Drs`, `CarEvent` or `Other`.

### Penalties

Time penalties, drive-throughs, stop-and-gos, grid penalties, reprimands and disqualifications, with the stewards' stated reasoning attached.

There is no structured penalty field in the source data. These are race-control messages classified by their wording, the same way a viewer reading the live timing feed would.
