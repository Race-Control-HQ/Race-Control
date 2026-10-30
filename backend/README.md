# RaceControl Backend

A FastAPI service that runs [FastF1](https://docs.fastf1.dev) and serves clean JSON to the
iOS, Android and web apps.

## Quick start

Requires Python 3.10+.

```bash
./run.sh            # creates a venv, installs deps, starts the API
```

The API starts on http://localhost:8000, with interactive docs at
http://localhost:8000/docs. With no environment variables set it is fully open, which is
what you want for local development.

The first request for a given race downloads and caches its data, so it is slow once and
fast thereafter.

## Public historical data fallback

OpenF1 is enabled by default for completed sessions from 2023 onwards. When
FastF1 cannot load a requested feed, the backend matches the event's scheduled
UTC date and session type to an OpenF1 session, then fills missing data through
the public API. Existing FastF1 feeds and Jolpica driver/team identifiers are
preserved. No account, subscription or new dependency is required. Set
`OPENF1_ENABLED=false` to disable the fallback.

The adapter supplies lap/sector times, tyre stints, running positions,
race-control messages (including the existing flags/penalties classification),
weather, and missing session classifications. Car telemetry and location are
fetched on demand for a driver and time window, enabling telemetry, circuit
outlines and replay positions where OpenF1 has those feeds. Telemetry distance
is calculated by integrating speed; OpenF1 lap boundaries and coordinates are
approximate. Circuit corner/marshal metadata and exact pit entry/exit timestamps
are not supplied by this adapter and are not fabricated.

Date filters use explicit ISO 8601 UTC timestamps (`Z`). With Python requests,
comparison parameter names are `date>` and `date<`; the query separator supplies
the equals sign. Including an equals sign in the parameter name produces an
invalid double-equals filter. When DRS is missing/null (as in 2026), telemetry
remains usable, `drsAvailable` is false and the compatibility `drs` array is zero.

The adapter uses only unauthenticated historical endpoints, skipping sessions
that have not ended. Its positive response cache is bounded to 128 entries with
a six-hour TTL. Requests are serialized and spaced to stay below three per
second, with a short failure backoff (at least 30 seconds, respecting numeric
`Retry-After` on 429). Partially restored sessions are refreshed after 30 seconds
so missing optional feeds can recover. Empty and failed raw OpenF1 responses
are not cached; API payloads marked `available: false` also bypass the endpoint
response cache. These OpenF1 caches are in memory and
clear on restart; keep the existing persistent FastF1 cache volume.

Look for `OpenF1 restored session` in the backend logs to confirm fallback use.
An upstream OpenF1 error affects only that feed; the existing API returns its
unavailable shape, while restored feeds remain usable. See the
[OpenF1 documentation](https://openf1.org/docs/) for coverage and endpoint schemas.

## Troubleshooting incomplete session data

FastF1 can finish `Session.load()` with driver results present even when the F1
timing feed failed. `DataNotLoadedError` from `session.laps` or
`session.race_control_messages` means that feed did not load; the earlier FastF1
warnings contain the initial failure. Check those warnings before the endpoint
traceback.

The backend caches a session only when its requested feeds loaded. Missing
telemetry returns `available: false`; a failed penalties feed also includes
`available: false` alongside the empty penalties list. These unavailable responses
are not cached, so subsequent requests can recover. A loaded, empty penalties log
is still a valid result.

F1's timing archive now requires authentication; this project does not use
subscription access. FastF1 401/403 failures can therefore be expected on a cold
cache, and eligible completed sessions fall back to OpenF1. If a round keeps
failing, check the earlier `OpenF1 fallback unavailable` warnings and connectivity
to `api.openf1.org` from the backend container. Compare the event and scheduled
UTC session dates with the current FastF1 schedule. A restart clears existing
in-memory session/response caches; it does not resolve upstream access failures.

## Tests

For memory growth, measured cache costs and deployment mitigation, see the
[backend memory investigation](MEMORY_INVESTIGATION.md).

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -v
```

`requirements.in` is the hand-edited spec and `requirements.txt` is the pip-compile lock.
If you change one, recompile and commit both.

## Documentation

- [Run the backend](https://docs.getracecontrol.com/getting-started/backend)
- [API reference](https://docs.getracecontrol.com/api/)
- [Architecture and module boundaries](https://docs.getracecontrol.com/architecture/backend)
- [Self-hosting](https://docs.getracecontrol.com/self-hosting/backend) and
  [environment variables](https://docs.getracecontrol.com/self-hosting/environment)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
