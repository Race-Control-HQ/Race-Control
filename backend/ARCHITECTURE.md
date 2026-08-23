# Backend architecture notes

## Module boundaries (for the next person who touches these files)

`fastf1_service.py` (~2,300 lines) and `analytics_service.py` (~1,150 lines) each hold
several largely-independent domains. A 2026 audit flagged this as a maintainability
risk — tests are already split by feature (one `test_*.py` file per domain), but the
production code isn't — while explicitly recommending **against** a big-bang split:
that's a large, high-risk mechanical refactor for no immediate feature benefit, and it
would touch every import site at once. Instead, extract a domain into its own module
**opportunistically**, the next time you materially change that area, preserving the
existing public function signatures so `main.py`'s route handlers and the test files
don't need to change.

The domain boundaries, as they exist today:

### `fastf1_service.py`
- **Formatting/cleaning utilities** — `_clean`, `_clean_utc`, `_td_ms`, `_fmt_lap`, `_fmt_gap`, `_row`, `_hex_color`, `_collect_multi`
- **Team logo metadata** — `_team_logo_url` (see the season-rollover note beside `_TEAM_LOGO_SEASON`)
- **Session load/cache** — `configure_cache`, `_session_has_data`, `_load_session`
- **Schedule** — `get_seasons`, `get_schedule`, `_session_identifier`
- **Race results** — `_safe_results`, `get_results`, `_safe_total_laps`
- **Standings/drivers/teams** — `get_driver_standings`, `get_constructor_standings`, `_season_reference_results`, `get_drivers`, `get_driver_detail`, `get_teams`, `get_team_detail`
- **WDC calculator** — `_max_points_remaining`, `_wdc_snapshot_at_round`, `get_wdc_calculator`
- **Circuits/track maps** — `get_circuits`, `_distinct_xy_count`, `_pick_outline_lap`, `get_circuit_map`
- **Replay** — `get_race_replay`, `get_replay_positions`, `_driver_meta`
- **Lap times/strategy** — `get_lap_times`, `get_strategy`
- **Race control/flags/penalties/weather** — `_race_control_rows`, `_session_total_laps`, `get_flags`, `get_race_control`, `_classify_penalty`, `_penalty_reason`, `_penalty_value`, `get_penalties`, `_flag_periods`, `get_weather`
- **Telemetry** — `_genuine_position_samples`, `_lap_telemetry`, `get_telemetry`, `get_telemetry_compare`
- **Retirements/reliability** — `list_race_drivers`, `_is_finish_status`, `_retirement_display_status`, `_laps_completed_by_driver_number`, `get_retirements`, `_classify_status`, `get_reliability`
- **Head-to-head/standings evolution** — `get_compare`, `_points_progression`, `_compute_points_progression`, `get_standings_evolution`

### `analytics_service.py`
- **Race trace** — `_race_start_ms`, `_elapsed_ms_by_driver`, `_green_flag_reference_ms`, `_finish_order`, `_status_by_abbr`, `_int_or_none`, `get_race_trace`
- **Tyre performance** — `_usable_performance_lap`, `_stint_number`, `get_tyre_performance`
- **Pit stops** — `get_pit_stops`
- **Qualifying sectors** — `_valid_qualifying_laps`, `get_qualifying_sectors`
- **Minisectors** — `get_minisectors`
- **Title scenarios** — `get_title_scenarios`
- **Driver fingerprint** — `_percentile`, `_evenly_spaced_rounds`, `get_driver_fingerprint`

## Other standing conventions worth knowing before you change something here

- **Single worker only.** `main.py`'s response cache, `fastf1_service._SESSION_CACHE`,
  and `fastf1_service._progression_cache` are plain in-process dicts with no
  cross-worker sharing. `main.py` refuses to start if `WEB_CONCURRENCY > 1` (see the
  check right after `svc.configure_cache(CACHE_DIR)`). Don't raise `WEB_CONCURRENCY`
  without first externalizing those three caches to a shared store.
- **CORS** denies all browser origins by default (`ALLOWED_ORIGINS` env var, empty
  unless set) — native iOS/Android clients don't send an `Origin` header, and the web
  client proxies through its own Next.js server rather than calling this API from a
  browser, so there is normally nothing to whitelist.
- **The reverse-proxy trust boundary is load-bearing.** The rate limiter trusts the
  leftmost `X-Forwarded-For` entry; that's only safe because Coolify/Traefik is the
  sole ingress and overwrites that header. See `Dockerfile`'s `CMD` and
  `test_main_infra.py::test_dockerfile_declares_the_proxy_trust_boundary`.
- **Dependencies**: `requirements.txt` is the human-edited floor/ceiling spec;
  `requirements-lock.txt` is the reproducible, exact-version install used by CI and
  the Docker image (see its header for how to regenerate it).
