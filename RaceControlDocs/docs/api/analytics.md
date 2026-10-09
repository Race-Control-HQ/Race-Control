# Derived Analytics

Served by `analytics_service.py` rather than `fastf1_service.py`. These endpoints answer questions the raw endpoints only pose, from columns FastF1 already loads.

## Chart-ready by design

Payloads are deliberately **chart-ready**. Series arrive pre-binned and pre-sorted, with team colours resolved and axis domains supplied.

That is because three clients render them with three different chart stacks. Any arithmetic left to the client is arithmetic that can diverge between platforms.

Each endpoint returns `available: false` with a valid empty body, rather than erroring, when a session's data is partial.

## Endpoints

All are `GET`.

| Path | Description |
|---|---|
| `/api/race-trace/{year}/{round}?mode=median\|leader` | **Race trace.** Cumulative time delta per driver per lap, with safety-car and flag periods and chart domains. |
| `/api/tyre-performance/{year}/{round}` | **Tyre degradation.** Filtered per-stint samples, fitted slopes and field-wide compound baselines. |
| `/api/pit-stops/{year}/{round}` | **Pit-stop ledger.** Real pit-lane transit loss, entry and rejoin positions, rival-window outcome and circuit median. |
| `/api/qualifying-sectors/{year}/{round}` | **Qualifying sector waterfall.** Gap-to-pole sector decomposition, ideal laps and speed traps. |
| `/api/minisectors/{year}/{round}?session=Q&top=10` | **Mini-sector dominance.** Approximately 24 curved track segments coloured by their fastest driver. |
| `/api/title-scenarios/{year}?d1=&d2=&through_round=` | **Title permutations.** Next-race finish-position matrix, projected points margins, and generated clinch and uniform-outcome summaries. |
| `/api/driver-fingerprint/{year}/{driver_id}` | **Driver fingerprint.** Six season percentile axes covering qualifying, race pace, tyres, starts, reliability and wet pace. |

## Race trace

`mode=median` (the default) subtracts a fixed race-wide green-flag median lap from cumulative elapsed time. `mode=leader` reports the gap to the leader on each lap.

The median baseline is fixed for the whole race:

```
elapsed_time − lap_number × green_flag_median_lap
```

This preserves the defining property of a race trace: the vertical distance between two drivers equals their real on-track time gap. Neutralised periods remain visible and are returned explicitly as chart bands.

## Mini-sectors

`session` defaults to `Q` and `top` to `10`.

::: warning Expensive on a cold cache
The first uncached request loads telemetry and is intentionally the expensive path. Expect it to be slow once.
:::

## Title scenarios

`d1` and `d2` pick the two drivers to compare. `through_round` views the permutations as they stood after an earlier round.

Historical snapshots follow the drivers' championship calculator's time machine. No hypothetical race is shown after a season has finished.

## Driver fingerprint

Tyre degradation uses an evenly spaced season sample capped by `FINGERPRINT_TYRE_ROUNDS` (default 6), to keep cold requests within proxy time budgets.
