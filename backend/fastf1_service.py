"""
fastf1_service.py
-----------------
Data-access layer that wraps the FastF1 Python library (https://docs.fastf1.dev)
and the Ergast/Jolpica backend, converting pandas structures into clean,
JSON-serialisable dictionaries for the RaceControl iOS app.

Everything here is defensive: FastF1 returns pandas DataFrames full of
Timedelta / Timestamp / NaN values that do not serialise to JSON on their own,
so every value passes through a normaliser before leaving this module.
"""

from __future__ import annotations

import logging
import math
import os
import re
import time
from datetime import datetime, timedelta
from functools import lru_cache
from typing import Any, Optional

import fastf1
import numpy as np
import pandas as pd
from fastf1.ergast import Ergast

log = logging.getLogger("racecontrol")

# FastF1 has full timing/telemetry support from 2018 onwards.
MIN_YEAR = 2018

_ergast = Ergast()


def configure_cache(cache_dir: str) -> None:
    """Enable FastF1's on-disk cache. Must be called once at startup."""
    fastf1.Cache.enable_cache(cache_dir)
    fastf1.set_log_level("WARNING")


# --------------------------------------------------------------------------- #
#  Normalisation helpers
# --------------------------------------------------------------------------- #
def _clean(value: Any) -> Any:
    """Turn any pandas / numpy / datetime scalar into a JSON-safe value."""
    if value is None:
        return None
    # pandas NaT / NaN
    try:
        if value is pd.NaT or (isinstance(value, float) and math.isnan(value)):
            return None
    except (TypeError, ValueError):
        pass
    if isinstance(value, (pd.Timedelta, timedelta)):
        return None  # timedeltas are exposed explicitly as *_ms fields instead
    if isinstance(value, (pd.Timestamp, datetime)):
        return value.isoformat()
    # numpy scalar -> python scalar
    if hasattr(value, "item"):
        try:
            value = value.item()
        except (ValueError, AttributeError):
            value = str(value)
    if isinstance(value, float) and (math.isinf(value) or math.isnan(value)):
        return None
    # Some upstream columns (e.g. a qualifying entry with no recorded driver
    # number) arrive pre-stringified as the literal text "nan"/"NaT" rather
    # than an actual float NaN/NaT; the numeric checks above never see
    # those cases since the value is already a str. Left as-is, this string
    # flows straight into the JSON response and collides with every other
    # row that has the same non-value, e.g. producing duplicate React keys
    # of "nan" on the client. `_hex_color` already special-cases this same
    # artifact; treat it the same way here for every field.
    if isinstance(value, str) and value.strip().lower() in ("nan", "nat", "none", "<na>"):
        return None
    # An empty/whitespace-only string is a non-value too, and must become null
    # for the same reason: clients fall back with `?? other` / `?: other`,
    # which only triggers on null, so an empty string sails straight through and
    # renders as a blank cell. FastF1 leaves `ClassifiedPosition` blank for
    # non-race sessions, which is exactly how the qualifying "Pos" column
    # ended up empty despite `position` being populated.
    if isinstance(value, str) and not value.strip():
        return None
    return value


def _clean_utc(value: Any) -> Optional[str]:
    """Like `_clean`, but for timestamps that are known to represent UTC.

    FastF1's race-control message timestamps come from the raw feed's "Utc"
    field, but `fastf1.utils.to_datetime` parses them into a *naive* Python
    datetime with no tzinfo attached. `_clean`'s plain `.isoformat()` on a
    naive value therefore omits any 'Z'/offset (e.g. "2026-07-26T12:20:00"),
    which browsers interpret as *local* time via `new Date(...)`, silently
    shifting every timestamp by the viewer's UTC offset. Attach the UTC
    tzinfo explicitly before formatting so the ISO string is unambiguous.
    """
    if value is None or value is pd.NaT:
        return None
    if isinstance(value, (pd.Timestamp, datetime)):
        ts = value if isinstance(value, pd.Timestamp) else pd.Timestamp(value)
        aware = ts.tz_localize("UTC") if ts.tzinfo is None else ts.tz_convert("UTC")
        return aware.isoformat()
    return _clean(value)


def _td_ms(value: Any) -> Optional[int]:
    """Timedelta -> whole milliseconds, or None."""
    if value is None or value is pd.NaT:
        return None
    if isinstance(value, (pd.Timedelta, timedelta)):
        try:
            return int(value.total_seconds() * 1000)
        except (ValueError, OverflowError):
            return None
    if isinstance(value, float) and math.isnan(value):
        return None
    return None


def _fmt_lap_ms(ms: Optional[int]) -> Optional[str]:
    """Milliseconds -> 'm:ss.mmm' lap-time string, or None."""
    if ms is None or ms <= 0:
        return None
    minutes, rem = divmod(ms, 60_000)
    seconds, millis = divmod(rem, 1000)
    return f"{minutes}:{seconds:02d}.{millis:03d}"


def _fmt_lap(value: Any) -> Optional[str]:
    """Timedelta -> 'm:ss.mmm' lap-time string, or None."""
    return _fmt_lap_ms(_td_ms(value))


def _fmt_gap(ms: Optional[int], best_ms: Optional[int]) -> Optional[str]:
    """Gap to the fastest time set in the same column, e.g. "+0.234"; None
    for whoever set that fastest time (nothing to show relative to itself)
    and for anyone without a time in that column at all."""
    if ms is None or ms <= 0 or best_ms is None:
        return None
    diff = ms - best_ms
    if diff <= 0:
        return None
    return f"+{diff / 1000:.3f}"


def _row(series: pd.Series, mapping: dict[str, str]) -> dict[str, Any]:
    """Pull mapped columns out of a pandas row, cleaning each value."""
    out: dict[str, Any] = {}
    for out_key, col in mapping.items():
        out[out_key] = _clean(series.get(col)) if col in series else None
    return out


def _hex_color(raw: Any) -> Optional[str]:
    if raw is None:
        return None
    s = str(raw).strip()
    if not s or s.lower() in ("nan", "none"):
        return None
    return f"#{s}" if not s.startswith("#") else s


# Neither FastF1 nor Ergast/Jolpica expose team logos, so these are hardcoded
# against F1.com's own media CDN (found by inspecting formula1.com/en/teams).
# Our teamId values come from Ergast's constructorId and don't match F1.com's
# URL slugs (e.g. "red_bull" vs "redbullracing"), hence the explicit mapping.
# This needs manual upkeep each season for renames/new entrants/rebrands.
_TEAM_LOGO_SLUGS: dict[str, str] = {
    "red_bull": "redbullracing",
    "ferrari": "ferrari",
    "mercedes": "mercedes",
    "mclaren": "mclaren",
    "alpine": "alpine",
    "williams": "williams",
    "aston_martin": "astonmartin",
    "rb": "racingbulls",
    "haas": "haasf1team",
    "audi": "audi",
    "cadillac": "cadillac",
}
# F1.com's CDN path embeds the current season twice; bump this one constant
# each season rollover instead of editing the template string.
_TEAM_LOGO_SEASON = 2026
_TEAM_LOGO_URL_TEMPLATE = (
    "https://media.formula1.com/image/upload/c_lfill,w_128/q_auto/"
    "v1740000001/common/f1/{season}/{slug}/{season}{slug}logowhite.webp"
)


def _team_logo_url(team_id: Any) -> Optional[str]:
    slug = _TEAM_LOGO_SLUGS.get(str(team_id)) if team_id else None
    if not slug:
        return None
    return _TEAM_LOGO_URL_TEMPLATE.format(season=_TEAM_LOGO_SEASON, slug=slug)


def _collect_multi(resp, max_pages: int = 25):
    """Walk every page of an Ergast/Jolpica multi-response.

    Jolpica (the Ergast successor FastF1 now uses) caps ``limit`` at 100 rows
    per page, so a full season of race/qualifying results spans several pages.
    This gathers all per-race content frames plus a concatenated description
    frame (round / raceName metadata) so nothing is silently truncated.
    """
    contents = list(resp.content)
    descs = [resp.description]
    pages = 0
    while not resp.is_complete and pages < max_pages:
        try:
            resp = resp.get_next_result_page()
        except Exception as exc:  # noqa: BLE001
            log.warning("pagination stopped early: %s", exc)
            break
        contents.extend(resp.content)
        descs.append(resp.description)
        pages += 1
    description = pd.concat(descs, ignore_index=True) if descs else pd.DataFrame()
    return contents, description


# --------------------------------------------------------------------------- #
#  Session loading (cached in-memory)
# --------------------------------------------------------------------------- #
# NB: FastF1's load() swallows internal failures (`@soft_exceptions`), so a
# transient F1/Jolpica hiccup can return a session with no results/laps. We must
# NOT cache such a broken session, otherwise every later request re-serves the
# failure. This manual cache only stores loads that actually produced data.
#
# Per-process, in-memory, not shared across workers — see the WEB_CONCURRENCY
# guard in main.py, which fails startup rather than let multiple workers each
# hold a divergent copy of this cache.
_SESSION_CACHE: dict = {}
_SESSION_ORDER: list = []
# Lower this on a memory-constrained container: a loaded race session with
# telemetry can be tens of megabytes.
_SESSION_CACHE_MAX = int(os.environ.get("SESSION_CACHE_MAX", 48))


def _session_has_data(session, laps_requested: bool) -> bool:
    try:
        if session.results is not None and len(session.results):
            return True
    except Exception:  # noqa: BLE001
        pass
    if laps_requested:
        try:
            if session.laps is not None and len(session.laps):
                return True
        except Exception:  # noqa: BLE001
            pass
    return False


def _load_session(year: int, rnd: int, identifier: str, with_laps: bool,
                  with_telemetry: bool = False, with_messages: bool = False,
                  with_weather: bool = False,
                  force: bool = False):
    key = (year, rnd, identifier, with_laps, with_telemetry, with_messages, with_weather)
    if not force and key in _SESSION_CACHE:
        return _SESSION_CACHE[key]

    session = fastf1.get_session(year, rnd, identifier)
    # Position/telemetry traces (used for the circuit map) are only available
    # when telemetry is loaded; loading it also implies loading laps.
    load_laps = with_laps or with_telemetry
    session.load(
        laps=load_laps,
        telemetry=with_telemetry,
        weather=with_weather,
        messages=with_messages,
    )

    # Only cache a load that actually produced usable data.
    if _session_has_data(session, load_laps):
        _SESSION_CACHE[key] = session
        _SESSION_ORDER.append(key)
        if len(_SESSION_ORDER) > _SESSION_CACHE_MAX:
            old = _SESSION_ORDER.pop(0)
            _SESSION_CACHE.pop(old, None)
    return session


# --------------------------------------------------------------------------- #
#  Seasons & schedule
# --------------------------------------------------------------------------- #
def get_seasons() -> list[int]:
    current = datetime.now().year
    return list(range(current, MIN_YEAR - 1, -1))


def get_schedule(year: int) -> list[dict[str, Any]]:
    schedule = fastf1.get_event_schedule(year, include_testing=False)
    now = datetime.now()
    events: list[dict[str, Any]] = []
    for _, ev in schedule.iterrows():
        sessions = []
        for i in range(1, 6):
            name = ev.get(f"Session{i}")
            date = ev.get(f"Session{i}DateUtc")
            if name and str(name) != "None" and not pd.isna(name):
                sessions.append(
                    {
                        "name": _clean(name),
                        "date": _clean_utc(date),
                        "identifier": _session_identifier(str(name)),
                    }
                )
        event_date = ev.get("EventDate")
        completed = False
        if isinstance(event_date, (pd.Timestamp, datetime)) and not pd.isna(event_date):
            completed = event_date.to_pydatetime().replace(tzinfo=None) < now
        events.append(
            {
                "round": int(ev.get("RoundNumber", 0)),
                "name": _clean(ev.get("EventName")),
                "officialName": _clean(ev.get("OfficialEventName")),
                "country": _clean(ev.get("Country")),
                "location": _clean(ev.get("Location")),
                "date": _clean_utc(event_date),
                "format": _clean(ev.get("EventFormat")),
                "sessions": sessions,
                "completed": completed,
                "year": year,
            }
        )
    return events


def _session_identifier(name: str) -> str:
    lookup = {
        "Practice 1": "FP1",
        "Practice 2": "FP2",
        "Practice 3": "FP3",
        "Qualifying": "Q",
        "Sprint": "S",
        "Sprint Qualifying": "SQ",
        "Sprint Shootout": "SS",
        "Race": "R",
    }
    return lookup.get(name, name)


# --------------------------------------------------------------------------- #
#  Session results
# --------------------------------------------------------------------------- #
def _safe_results(session):
    """Return the results DataFrame, or None if it never loaded."""
    try:
        res = session.results
        return res if res is not None and len(res) else None
    except Exception:  # noqa: BLE001
        return None


# Sessions whose classification FastF1 can't hand over ready-made.
#
# Practice has no finishing order at all — `session.results` comes back in car
# number order with no times on it — so its timesheet is rebuilt from the laps.
# Sprint qualifying arrives with its Q1/Q2/Q3 columns empty, but FastF1 will
# work the whole classification out itself from lap times *if* the session is
# loaded with laps and race control messages (it needs the messages to know
# which laps were deleted; without them it gives up and leaves the result
# blank). Either way the fix starts with loading more than the results.
PRACTICE_SESSIONS = {"FP1", "FP2", "FP3"}
SPRINT_QUALIFYING_SESSIONS = {"SQ", "SS"}


def _session_best_laps(session) -> dict[str, tuple[Optional[int], int]]:
    """Best lap (ms) and laps completed, per driver number."""
    try:
        laps = session.laps
    except Exception:  # noqa: BLE001
        return {}
    if laps is None or len(laps) == 0 or "DriverNumber" not in laps:
        return {}
    out: dict[str, tuple[Optional[int], int]] = {}
    for number, group in laps.groupby("DriverNumber"):
        # A lap the stewards deleted never happened as far as the timesheet
        # is concerned; `Deleted` is only populated when race control
        # messages were loaded, hence loading them below.
        timed = group[group["Deleted"] != True] if "Deleted" in group else group  # noqa: E712
        times = [ms for ms in (_td_ms(t) for t in timed["LapTime"]) if ms is not None and ms > 0]
        out[str(number)] = (min(times) if times else None, int(len(group)))
    return out


def _classify_practice(session, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """
    Give a practice session a real classification.

    FastF1 has no finishing order for practice, so `session.results` comes back
    in *car number* order — and the `position` fallback above then numbers that
    1, 2, 3…, which reads as a timesheet while actually saying nothing more
    than who has the lowest race number. The laps are the only record of what
    happened, so order the field by each driver's best lap and show that time,
    its gap to the fastest, and how many laps they ran.
    """
    by_driver = _session_best_laps(session)
    for r in rows:
        best, laps = by_driver.get(str(r.get("driverNumber")), (None, 0))
        r["_best_ms"] = best
        r["lapsCompleted"] = laps or None
    # Stable, so drivers who never set a time (a car in the garage all
    # session) stay at the back in the order they arrived in.
    rows.sort(key=lambda r: (r["_best_ms"] is None, r["_best_ms"] or 0))
    fastest = rows[0]["_best_ms"] if rows else None
    for idx, r in enumerate(rows):
        best = r.pop("_best_ms")
        r["position"] = idx + 1
        r["classifiedPosition"] = None
        r["bestLap"] = _fmt_lap_ms(best)
        r["bestLapGap"] = _fmt_gap(best, fastest)
    return rows


def get_results(year: int, rnd: int, identifier: str = "R") -> dict[str, Any]:
    # Practice and sprint qualifying carry no timing in their results, so
    # they're loaded with the laps (and the race control messages that say
    # which of those laps were deleted) that the timing has to come from.
    practice = identifier in PRACTICE_SESSIONS
    from_laps = practice or identifier in SPRINT_QUALIFYING_SESSIONS
    session = _load_session(year, rnd, identifier, with_laps=from_laps, with_messages=from_laps)
    results = _safe_results(session)

    # If that first load came back empty (e.g. a transient
    # F1/Jolpica failure that FastF1 swallowed), force a fresh load *with* laps.
    # That both retries the sources and enables FastF1's lap-time fallback that
    # reconstructs finishing order when API results are unavailable.
    if results is None:
        session = _load_session(year, rnd, identifier, with_laps=True, force=True)
        results = _safe_results(session)

    result_rows = list(results.iterrows()) if results is not None else []
    # Session-best per qualifying segment, so each row can show its gap to
    # pole in that segment rather than just its raw time: computed as a
    # separate pass since a row can't know the field-wide best on its own.
    q1_ms_all = [_td_ms(r.get("Q1")) for _, r in result_rows]
    q2_ms_all = [_td_ms(r.get("Q2")) for _, r in result_rows]
    q3_ms_all = [_td_ms(r.get("Q3")) for _, r in result_rows]
    best_q1 = min((v for v in q1_ms_all if v is not None and v > 0), default=None)
    best_q2 = min((v for v in q2_ms_all if v is not None and v > 0), default=None)
    best_q3 = min((v for v in q3_ms_all if v is not None and v > 0), default=None)

    rows: list[dict[str, Any]] = []
    for idx, ((_, r), q1_ms, q2_ms, q3_ms) in enumerate(zip(result_rows, q1_ms_all, q2_ms_all, q3_ms_all)):
        time_ms = _td_ms(r.get("Time"))
        # Qualifying (and sprint-qualifying) results don't reliably carry a
        # "Position" value the way race results do; FastF1 already returns
        # the rows in classification order, so the row's own place in that
        # order is a safe fallback rather than leaving the column blank.
        position = _clean(r.get("Position"))
        if position is None:
            position = idx + 1
        rows.append(
            {
                "position": position,
                "classifiedPosition": _clean(r.get("ClassifiedPosition")),
                "driverNumber": _clean(r.get("DriverNumber")),
                "abbreviation": _clean(r.get("Abbreviation")),
                "driverId": _clean(r.get("DriverId")),
                "firstName": _clean(r.get("FirstName")),
                "lastName": _clean(r.get("LastName")),
                "fullName": _clean(r.get("FullName")),
                "headshotUrl": _clean(r.get("HeadshotUrl")),
                "countryCode": _clean(r.get("CountryCode")),
                "teamName": _clean(r.get("TeamName")),
                "teamId": _clean(r.get("TeamId")),
                "teamLogoUrl": _team_logo_url(r.get("TeamId")),
                "teamColor": _hex_color(r.get("TeamColor")),
                "gridPosition": _clean(r.get("GridPosition")),
                "status": _clean(r.get("Status")),
                "points": _clean(r.get("Points")),
                "timeMs": time_ms,
                "q1": _fmt_lap(r.get("Q1")),
                "q2": _fmt_lap(r.get("Q2")),
                "q3": _fmt_lap(r.get("Q3")),
                "q1Gap": _fmt_gap(q1_ms, best_q1),
                "q2Gap": _fmt_gap(q2_ms, best_q2),
                "q3Gap": _fmt_gap(q3_ms, best_q3),
                "bestLap": None,
                "bestLapGap": None,
                "lapsCompleted": None,
            }
        )

    if practice:
        rows = _classify_practice(session, rows)

    return {
        "year": year,
        "round": rnd,
        "session": identifier,
        "sessionName": _clean(getattr(session, "name", identifier)),
        "eventName": _clean(session.event.get("EventName")),
        "totalLaps": _safe_total_laps(session),
        "results": rows,
    }


def _safe_total_laps(session):
    """`session.total_laps` raises if laps weren't loaded; return None instead."""
    try:
        return _clean(session.total_laps)
    except Exception:  # noqa: BLE001
        return None


# --------------------------------------------------------------------------- #
#  Standings (Ergast / Jolpica)
# --------------------------------------------------------------------------- #
def get_driver_standings(year: int) -> list[dict[str, Any]]:
    resp = _ergast.get_driver_standings(season=year, limit=100)
    if not resp.content:
        return []
    df = resp.content[0]
    out = []
    for _, r in df.iterrows():
        constructors = r.get("constructorNames")
        team = constructors[0] if isinstance(constructors, list) and constructors else None
        cids = r.get("constructorIds")
        team_id = cids[0] if isinstance(cids, list) and cids else None
        out.append(
            {
                "position": _clean(r.get("position")),
                "points": _clean(r.get("points")),
                "wins": _clean(r.get("wins")),
                "driverId": _clean(r.get("driverId")),
                "driverNumber": _clean(r.get("driverNumber")),
                "driverCode": _clean(r.get("driverCode")),
                "givenName": _clean(r.get("givenName")),
                "familyName": _clean(r.get("familyName")),
                "nationality": _clean(r.get("driverNationality")),
                "dateOfBirth": _clean(r.get("dateOfBirth")),
                "teamName": _clean(team),
                "teamId": _clean(team_id),
                "teamLogoUrl": _team_logo_url(team_id),
            }
        )
    return out


def get_constructor_standings(year: int) -> list[dict[str, Any]]:
    resp = _ergast.get_constructor_standings(season=year, limit=100)
    if not resp.content:
        return []
    df = resp.content[0]
    out = []
    for _, r in df.iterrows():
        team_id = r.get("constructorId")
        out.append(
            {
                "position": _clean(r.get("position")),
                "points": _clean(r.get("points")),
                "wins": _clean(r.get("wins")),
                "teamId": _clean(team_id),
                "teamName": _clean(r.get("constructorName")),
                "nationality": _clean(r.get("constructorNationality")),
                "teamLogoUrl": _team_logo_url(team_id),
            }
        )
    return out


# --------------------------------------------------------------------------- #
#  Drivers & teams (enriched from the season's first completed race)
# --------------------------------------------------------------------------- #
def _season_reference_results(year: int) -> Optional[pd.DataFrame]:
    """Grab a race's results for headshots / team colours (metadata Ergast lacks)."""
    try:
        schedule = fastf1.get_event_schedule(year, include_testing=False)
    except Exception as exc:  # noqa: BLE001
        log.warning("schedule lookup failed for %s: %s", year, exc)
        return None
    now = datetime.now()
    completed_rounds = [
        int(ev["RoundNumber"])
        for _, ev in schedule.iterrows()
        if isinstance(ev.get("EventDate"), (pd.Timestamp, datetime))
        and ev["EventDate"].to_pydatetime().replace(tzinfo=None) < now
        and int(ev["RoundNumber"]) > 0
    ]
    for rnd in reversed(completed_rounds):  # newest completed race = latest lineup
        try:
            session = _load_session(year, rnd, "R", with_laps=False)
            if session.results is not None and len(session.results):
                return session.results
        except Exception as exc:  # noqa: BLE001
            log.warning("results lookup failed %s r%s: %s", year, rnd, exc)
            continue
    return None


def get_drivers(year: int) -> list[dict[str, Any]]:
    standings = {d["driverId"]: d for d in get_driver_standings(year)}
    ref = _season_reference_results(year)
    meta: dict[str, dict[str, Any]] = {}
    if ref is not None:
        for _, r in ref.iterrows():
            did = _clean(r.get("DriverId"))
            if did:
                meta[did] = {
                    "headshotUrl": _clean(r.get("HeadshotUrl")),
                    "teamColor": _hex_color(r.get("TeamColor")),
                    "teamId": _clean(r.get("TeamId")),
                    "abbreviation": _clean(r.get("Abbreviation")),
                    "driverNumber": _clean(r.get("DriverNumber")),
                    "countryCode": _clean(r.get("CountryCode")),
                }

    drivers = []
    for did, s in standings.items():
        m = meta.get(did, {})
        # Prefer FastF1's own TeamId (from the season's own results) over
        # Ergast/Jolpica's constructorId. Ergast's constructor ids lag for
        # brand-new or renamed entrants (e.g. a new 2026 team, or one that
        # rebranded mid-history), so they don't reliably match the slugs in
        # _TEAM_LOGO_SLUGS the way FastF1's ids do; every other endpoint in
        # this file (results, teams, etc.) already sources its team id from
        # FastF1 for exactly this reason. Falling back to Ergast's id only
        # matters early in a season before any race has completed, when
        # `meta` is still empty.
        tid = m.get("teamId") or s.get("teamId")
        drivers.append(
            {
                "driverId": did,
                "givenName": s.get("givenName"),
                "familyName": s.get("familyName"),
                "code": s.get("driverCode") or m.get("abbreviation"),
                "number": s.get("driverNumber") or m.get("driverNumber"),
                "nationality": s.get("nationality"),
                "dateOfBirth": s.get("dateOfBirth"),
                "teamName": s.get("teamName"),
                "teamId": tid,
                "teamLogoUrl": _team_logo_url(tid),
                "teamColor": m.get("teamColor"),
                "headshotUrl": m.get("headshotUrl"),
                "countryCode": m.get("countryCode"),
                "position": s.get("position"),
                "points": s.get("points"),
                "wins": s.get("wins"),
            }
        )
    # Any driver in the reference lineup missing from standings (rare) still appears.
    return drivers


# --------------------------------------------------------------------------- #
#  WDC title-decider calculator
# --------------------------------------------------------------------------- #
# Points on offer for winning everything left on the calendar: race win +
# fastest-lap bonus, plus a sprint win on sprint weekends. Mirrors FastF1's
# own "who can still win the WDC" example (see
# https://docs.fastf1.dev/gen_modules/examples_gallery/standings/plot_who_can_still_win_wdc.html),
# deliberately simplified the same way that example is: it doesn't resolve
# a tie on countback (equal points => both shown as still in it), and it
# assumes a flat 8-point sprint win across every season, when the actual
# sprint points scale has varied year to year (2021's top-3-only 3/2/1 vs.
# 2022+'s top-8 8..1). Precise historical sprint scoring isn't the point of a
# forward-looking "can they still win" calculator, so the FastF1 convention
# is used as-is rather than re-deriving it per season.
_SPRINT_POINTS_TOTAL = 8 + 25 + 1  # sprint win + race win + fastest lap
_CONVENTIONAL_POINTS_TOTAL = 25 + 1  # race win + fastest lap

# Surfaced in the API response (and from there, in the client UI) so the two
# approximations above are visible to whoever's reading the calculator, not
# just to whoever reads this source file.
_WDC_CALCULATOR_NOTES = [
    "Ties on equal points are not resolved by countback; tied drivers are "
    "both shown as still able to win.",
    "Sprint scoring assumes the current 8-1 scale for every remaining "
    "sprint, even in seasons that used a different scale (e.g. 2021's "
    "top-3-only 3-2-1).",
]


def _max_points_remaining(year: int, after_round: Optional[int] = None) -> tuple[int, int, int]:
    """Returns (rounds_remaining, max_points_remaining, sprint_rounds_remaining).

    With `after_round=None`, "remaining" means events not yet completed
    (live/current view). With `after_round=N`, it means every event later
    than round N, regardless of whether it's actually happened yet: this is
    what powers the WDC calculator's historical "as of round N" time-machine
    view, where "remaining" means "remaining from that point in the season".
    """
    try:
        events = get_schedule(year)
    except Exception as exc:  # noqa: BLE001
        log.warning("schedule unavailable for wdc calculator %s: %s", year, exc)
        return 0, 0, 0

    if after_round is None:
        remaining = [e for e in events if e.get("round", 0) > 0 and not e.get("completed")]
    else:
        remaining = [e for e in events if e.get("round", 0) > after_round]
    sprint_remaining = sum(1 for e in remaining if str(e.get("format") or "").startswith("sprint"))
    conventional_remaining = len(remaining) - sprint_remaining
    max_points = sprint_remaining * _SPRINT_POINTS_TOTAL + conventional_remaining * _CONVENTIONAL_POINTS_TOTAL
    return len(remaining), max_points, sprint_remaining


def _wdc_snapshot_at_round(year: int, through_round: int) -> list[dict[str, Any]]:
    """Every driver's identity + cumulative points as of the end of `through_round`.

    Sourced from `_points_progression`'s per-round series rather than live
    standings, so a driver who has since retired mid-season, or who a team
    swapped out for a reserve, still shows exactly what they had at that
    point in time: the whole point of a "time machine" view.
    """
    progression = _points_progression(year)
    extras = {d["driverId"]: d for d in get_drivers(year)}  # logos/headshots/teamId, best-effort

    snapshot = []
    for did, d in progression["drivers"].items():
        points = 0.0
        for point in d["series"]:
            if point["round"] > through_round:
                break
            points = point["points"]
        extra = extras.get(did, {})
        snapshot.append(
            {
                "driverId": did,
                "code": d.get("code") or extra.get("code"),
                "givenName": d.get("givenName") or extra.get("givenName"),
                "familyName": d.get("familyName") or extra.get("familyName"),
                "teamName": d.get("teamName") or extra.get("teamName"),
                "teamId": extra.get("teamId"),
                "teamLogoUrl": extra.get("teamLogoUrl"),
                "teamColor": d.get("teamColor") or extra.get("teamColor"),
                "headshotUrl": extra.get("headshotUrl"),
                "points": points,
            }
        )
    snapshot.sort(key=lambda d: -d["points"])
    for i, d in enumerate(snapshot):
        d["position"] = i + 1
    return snapshot


def get_wdc_calculator(year: int, through_round: Optional[int] = None) -> dict[str, Any]:
    try:
        rounds_in_season = max((e.get("round") or 0) for e in get_schedule(year))
    except Exception:  # noqa: BLE001
        rounds_in_season = 0

    if through_round is not None and rounds_in_season > 0:
        # Clamp to a valid, already-run round rather than erroring on a bad
        # query param: the picker driving this should only ever send values
        # in range, but a stale/hand-typed one shouldn't 500.
        through_round = max(1, min(int(through_round), rounds_in_season))
        drivers = _wdc_snapshot_at_round(year, through_round)
        rounds_remaining, max_remaining, sprint_remaining = _max_points_remaining(year, after_round=through_round)
    else:
        through_round = None
        drivers = sorted(get_drivers(year), key=lambda d: (d.get("position") is None, d.get("position")))
        rounds_remaining, max_remaining, sprint_remaining = _max_points_remaining(year)

    if not drivers:
        return {
            "year": year,
            "throughRound": through_round,
            "roundsInSeason": rounds_in_season,
            "roundsRemaining": 0,
            "maxRemainingPoints": 0,
            "leaderPoints": 0,
            "decided": True,
            "drivers": [],
            "notes": _WDC_CALCULATOR_NOTES,
        }

    leader_points = float(drivers[0].get("points") or 0)

    out_drivers = []
    can_win_count = 0
    for d in drivers:
        points = float(d.get("points") or 0)
        max_points = points + max_remaining
        can_win = max_points >= leader_points
        if can_win:
            can_win_count += 1
        out_drivers.append(
            {
                "position": d.get("position"),
                "driverId": d.get("driverId"),
                "driverCode": d.get("code"),
                "givenName": d.get("givenName"),
                "familyName": d.get("familyName"),
                "teamName": d.get("teamName"),
                "teamId": d.get("teamId"),
                "teamLogoUrl": d.get("teamLogoUrl"),
                "teamColor": d.get("teamColor"),
                "headshotUrl": d.get("headshotUrl"),
                "points": points,
                "maxPoints": max_points,
                "pointsBehindLeader": round(leader_points - points, 1),
                "canWin": can_win,
            }
        )

    return {
        "year": year,
        "throughRound": through_round,
        "roundsInSeason": rounds_in_season,
        "roundsRemaining": rounds_remaining,
        "sprintRoundsRemaining": sprint_remaining,
        "maxRemainingPoints": max_remaining,
        "leaderPoints": leader_points,
        # The title is mathematically settled once nobody but the (trivially
        # "can-win") leader has a path left, or there's nothing left to race.
        "decided": max_remaining == 0 or can_win_count <= 1,
        "drivers": out_drivers,
        "notes": _WDC_CALCULATOR_NOTES,
    }


def get_driver_detail(year: int, driver_id: str) -> dict[str, Any]:
    drivers = {d["driverId"]: d for d in get_drivers(year)}
    base = drivers.get(driver_id, {"driverId": driver_id})

    # Per-round results for this driver across the season.
    # NB: race metadata (round, raceName) lives in `.description`, while the
    # per-driver result rows live in the matching `.content` DataFrame.
    results_resp = _ergast.get_race_results(season=year, limit=100)
    contents, description = _collect_multi(results_resp)
    season_rows: list[dict[str, Any]] = []
    for idx, race_df in enumerate(contents):
        if race_df is None or race_df.empty or "driverId" not in race_df:
            continue
        meta = description.iloc[idx] if idx < len(description) else None
        rnd = int(meta["round"]) if meta is not None and "round" in meta else None
        race_name = _clean(meta.get("raceName")) if meta is not None and "raceName" in meta else None
        match = race_df[race_df["driverId"] == driver_id]
        if match.empty:
            continue
        r = match.iloc[0]
        season_rows.append(
            {
                "round": rnd,
                "raceName": race_name,
                "position": _clean(r.get("position")),
                "points": _clean(r.get("points")),
                "grid": _clean(r.get("grid")),
                "status": _clean(r.get("status")),
            }
        )
    season_rows.sort(key=lambda x: x["round"] if x["round"] is not None else 0)
    base["seasonResults"] = season_rows
    return base


def get_teams(year: int) -> list[dict[str, Any]]:
    standings = get_constructor_standings(year)
    drivers = get_drivers(year)
    colors: dict[str, Optional[str]] = {}
    roster: dict[str, list[dict[str, Any]]] = {}
    for d in drivers:
        tid = d.get("teamId")
        if not tid:
            continue
        colors.setdefault(tid, d.get("teamColor"))
        roster.setdefault(tid, []).append(
            {
                "driverId": d["driverId"],
                "name": f'{d.get("givenName","")} {d.get("familyName","")}'.strip(),
                "code": d.get("code"),
                "number": d.get("number"),
                "headshotUrl": d.get("headshotUrl"),
                "points": d.get("points"),
            }
        )
    teams = []
    for t in standings:
        tid = t["teamId"]
        # Highest individual points contribution first, so the roster reads
        # as a breakdown of who's carried the team rather than grid order.
        drivers = sorted(roster.get(tid, []), key=lambda d: d.get("points") or 0, reverse=True)
        teams.append(
            {
                **t,
                "teamColor": colors.get(tid),
                "drivers": drivers,
            }
        )
    return teams


def get_team_detail(year: int, team_id: str) -> dict[str, Any]:
    for t in get_teams(year):
        if t["teamId"] == team_id:
            return t
    return {"teamId": team_id, "drivers": []}


# --------------------------------------------------------------------------- #
#  Circuits & circuit map
# --------------------------------------------------------------------------- #
def get_circuits(year: int) -> list[dict[str, Any]]:
    resp = _ergast.get_circuits(season=year, limit=100)
    df = resp
    out = []
    for _, r in df.iterrows():
        out.append(
            {
                "circuitId": _clean(r.get("circuitId")),
                "name": _clean(r.get("circuitName")),
                "locality": _clean(r.get("locality")),
                "country": _clean(r.get("country")),
                "lat": _clean(r.get("lat")),
                "long": _clean(r.get("long")),
            }
        )
    return out


def _distinct_xy_count(tel: Any) -> int:
    """How many genuinely distinct (X, Y) samples a telemetry frame contains.

    `get_telemetry()` merges the low-frequency position channel onto the
    high-frequency car-data channel, holding/interpolating position between
    real updates, so row count says nothing about real positional detail.
    Counting distinct rounded coordinate pairs does.
    """
    try:
        if tel is None or len(tel) == 0 or "X" not in tel or "Y" not in tel:
            return 0
        xy = np.column_stack([
            np.round(tel["X"].to_numpy(dtype=float), 1),
            np.round(tel["Y"].to_numpy(dtype=float), 1),
        ])
        return int(len(np.unique(xy, axis=0)))
    except Exception:  # noqa: BLE001
        return 0


# Below this many distinct position samples, a lap's trace is too coarse to
# draw a recognisable track outline (a ~5km lap needs hundreds; a degraded
# trace can be as low as ~20, which renders as a polygon).
_MIN_OUTLINE_SAMPLES = 200
# Cap how many candidate laps we pull telemetry for: each is an expensive
# merge, and in practice a good lap turns up almost immediately.
_MAX_OUTLINE_CANDIDATES = 8


def _pick_outline_lap(laps: Any) -> tuple[Any, Any, int]:
    """Choose a lap whose position trace is detailed enough to draw the track.

    Tries the fastest lap first (best racing line), then falls back to other
    laps, keeping whichever has the most distinct position samples. Returns
    (lap, telemetry, distinct_sample_count).
    """
    candidates = []
    try:
        fastest = laps.pick_fastest()
        if fastest is not None:
            candidates.append(fastest)
    except Exception as exc:  # noqa: BLE001
        log.warning("pick_fastest failed: %s", exc)

    best_lap = best_tel = None
    best_count = 0
    try:
        for _, other in laps.iterrows():
            if len(candidates) >= _MAX_OUTLINE_CANDIDATES:
                break
            candidates.append(other)
    except Exception as exc:  # noqa: BLE001
        log.warning("lap iteration failed: %s", exc)

    for candidate in candidates:
        try:
            tel = candidate.get_telemetry()
        except Exception as exc:  # noqa: BLE001
            log.warning("telemetry unavailable for candidate lap: %s", exc)
            continue
        count = _distinct_xy_count(tel)
        if count > best_count:
            best_lap, best_tel, best_count = candidate, tel, count
        if count >= _MIN_OUTLINE_SAMPLES:
            break  # good enough; don't pay for more merges

    if best_count and best_count < _MIN_OUTLINE_SAMPLES:
        log.warning(
            "circuit outline built from a sparse position trace (%s distinct samples); "
            "corners will look angular", best_count,
        )
    return best_lap, best_tel, best_count


def get_circuit_map(year: int, rnd: int) -> dict[str, Any]:
    """Track outline + corner markers derived from a fast lap's position trace.

    Returns an empty outline/corners list (rather than erroring) when the session
    has no data yet, e.g. a future or very recently finished race.
    """
    session = _load_session(year, rnd, "R", with_laps=True, with_telemetry=True)
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "eventName": _clean(session.event.get("EventName")),
        "location": _clean(session.event.get("Location")),
        "country": _clean(session.event.get("Country")),
        "outline": [],
        "points": [],
        "corners": [],
        "marshalLights": [],
        "marshalSectors": [],
        "rotation": 0,
        "lengthMeters": None,
        "minElevation": None,
        "maxElevation": None,
        "fastestLap": None,
        # Diagnostic: how many genuinely distinct position samples the chosen
        # lap's trace contained. A low number here (vs. hundreds) means the
        # source telemetry was too coarse to draw smooth corners, which is a
        # data-availability issue rather than a rendering one.
        "outlineSamples": 0,
    }
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return payload  # session not available / no timing data

        # Merged telemetry gives X/Y/Z position aligned with speed, DRS and the
        # cumulative distance along the lap: everything we need to draw a
        # speed-coloured racing line with DRS zones and an elevation profile.
        #
        # The fastest lap is the natural choice, but its *position* channel is
        # sometimes badly degraded (only a couple of dozen genuine samples for
        # the whole lap, with everything between them held constant). That
        # renders as a hard-edged polygon: no resampling or curve smoothing
        # downstream can recover a corner shape that was never in the source.
        # So verify the chosen lap actually has a well-populated position
        # trace, and fall back to the richest lap available if it doesn't.
        lap, tel, distinct_samples = _pick_outline_lap(laps)
        if lap is None or tel is None:
            return payload
        payload["outlineSamples"] = distinct_samples

        xs_full = tel["X"].to_numpy(dtype=float)
        ys_full = tel["Y"].to_numpy(dtype=float)
        zs_full = tel["Z"].to_numpy(dtype=float) if "Z" in tel else np.zeros(len(xs_full))
        speeds_full = tel["Speed"].to_numpy(dtype=float)
        drs_full = tel["DRS"].to_numpy(dtype=float) if "DRS" in tel else np.zeros(len(xs_full))
        dist_full = (
            tel["Distance"].to_numpy(dtype=float)
            if "Distance" in tel
            else np.arange(len(xs_full), dtype=float)
        )

        # `get_telemetry()` merges the low-frequency (~3.7Hz) position channel
        # onto the much higher-frequency car-data channel, which holds each
        # position sample across many rows until the next real update. Taking
        # every Nth row (uniform in *time*) over-samples those held stretches
        # and under-samples the transitions between them; this is what was
        # drawing straight polygon edges through corners instead of a smooth
        # curve (worse on tracks with more low/medium-speed corners, where
        # the car dwells at nearly the same position for longer). Resampling
        # at fixed steps of *distance* along the lap fixes this everywhere,
        # regardless of how the source data happened to be time-sampled.
        #
        # 350 points over a ~4-5km lap is only one sample every ~12-15m,
        # which is too coarse for a tight, short-radius corner (e.g. a
        # hairpin with a ~20m arc through the turn), that corner ends up
        # captured by only 1-2 samples, so no amount of curve smoothing on
        # the frontend can round it out; there's simply nothing to smooth
        # between. Raising the target spacing to ~4m gives several samples
        # through even the tightest corners.
        if len(dist_full) > 1 and dist_full[-1] > dist_full[0]:
            target_points = max(350, min(1600, round((dist_full[-1] - dist_full[0]) / 4)))
            order = np.argsort(dist_full, kind="stable")
            d_sorted = dist_full[order]
            targets = np.linspace(d_sorted[0], d_sorted[-1], target_points)
            xs = np.interp(targets, d_sorted, xs_full[order])
            ys = np.interp(targets, d_sorted, ys_full[order])
            zs = np.interp(targets, d_sorted, zs_full[order])
            speeds = np.interp(targets, d_sorted, speeds_full[order])
            drs = np.round(np.interp(targets, d_sorted, drs_full[order]))
            dist = targets
        else:
            xs, ys, zs, speeds, drs, dist = xs_full, ys_full, zs_full, speeds_full, drs_full, dist_full

        points = []
        for x, y, z, sp, dr, ds in zip(xs, ys, zs, speeds, drs, dist):
            points.append({
                "x": float(x),
                "y": float(y),
                "z": float(z),
                "speed": round(float(sp), 1),
                "drs": int(dr),               # 10/12/14 => DRS open
                "distance": round(float(ds), 1),
            })
        payload["points"] = points
        payload["outline"] = [{"x": p["x"], "y": p["y"]} for p in points]  # back-compat

        if len(dist):
            payload["lengthMeters"] = round(float(max(dist)))
        if len(zs):
            payload["minElevation"] = round(float(min(zs)), 1)
            payload["maxElevation"] = round(float(max(zs)), 1)

        drv = session.get_driver(lap["Driver"]) if lap.get("Driver") else None
        fastest_lap_team_id = _clean(drv.get("TeamId")) if drv is not None else None
        payload["fastestLap"] = {
            "driver": _clean(lap.get("Driver")),
            "driverName": _clean(drv["FullName"]) if drv is not None else None,
            "team": _clean(drv["TeamName"]) if drv is not None else None,
            "teamLogoUrl": _team_logo_url(fastest_lap_team_id),
            "teamColor": _hex_color(drv["TeamColor"]) if drv is not None else None,
            "time": _fmt_lap(lap.get("LapTime")),
            "compound": _clean(lap.get("Compound")),
        }
    except Exception as exc:  # noqa: BLE001
        log.warning("pos data unavailable %s r%s: %s", year, rnd, exc)
    try:
        info = session.get_circuit_info()
        payload["rotation"] = float(info.rotation)

        # Corner markers double as hover targets on the frontend, so pull
        # everything FastF1 gives us per corner (not just position): `Angle`
        # is the corner's turn angle in degrees, `Distance` is how far along
        # the lap it sits (metres); both let us cross-reference the fastest
        # lap's speed trace to show an approximate cornering speed.
        points_for_lookup = payload.get("points") or []
        for _, c in info.corners.iterrows():
            distance_raw = c.get("Distance")
            distance_val = (
                float(distance_raw) if distance_raw is not None and not pd.isna(distance_raw) else None
            )
            angle_raw = c.get("Angle")
            angle_val = float(angle_raw) if angle_raw is not None and not pd.isna(angle_raw) else None
            speed_val = None
            if distance_val is not None and points_for_lookup:
                nearest = min(points_for_lookup, key=lambda p: abs(p["distance"] - distance_val))
                speed_val = nearest["speed"]
            payload["corners"].append(
                {
                    "number": int(c["Number"]),
                    "letter": _clean(c.get("Letter")) or "",
                    "x": float(c["X"]),
                    "y": float(c["Y"]),
                    "angle": angle_val,
                    "distanceMeters": distance_val,
                    "speed": speed_val,
                }
            )

        # Marshal posts (light panels) and marshal sectors (the boundaries
        # stewards use to divide the circuit for flag/incident reporting):
        # same coordinate space as the track outline and corners.
        for _, m in info.marshal_lights.iterrows():
            num_raw = m.get("Number")
            payload["marshalLights"].append(
                {
                    "number": int(num_raw) if num_raw is not None and not pd.isna(num_raw) else 0,
                    "x": float(m["X"]),
                    "y": float(m["Y"]),
                }
            )
        for _, s in info.marshal_sectors.iterrows():
            num_raw = s.get("Number")
            payload["marshalSectors"].append(
                {
                    "number": int(num_raw) if num_raw is not None and not pd.isna(num_raw) else 0,
                    "x": float(s["X"]),
                    "y": float(s["Y"]),
                }
            )
    except Exception as exc:  # noqa: BLE001
        log.warning("circuit info unavailable %s r%s: %s", year, rnd, exc)
    return payload


# --------------------------------------------------------------------------- #
#  Race replay (lap-by-lap running order)
# --------------------------------------------------------------------------- #
def get_race_replay(year: int, rnd: int) -> dict[str, Any]:
    """
    Build a lap-by-lap running order for the race so the app can 'replay' it:
    for every completed lap we emit the ordered field with gaps, tyre and lap time.
    """
    session = _load_session(year, rnd, "R", with_laps=True)
    empty = {
        "year": year, "round": rnd,
        "eventName": _clean(session.event.get("EventName")),
        "totalLaps": 0, "drivers": [], "frames": [],
    }
    try:
        laps = session.laps
        results = session.results
        if laps is None or not len(laps):
            return empty  # session not available / no timing data yet
    except Exception as exc:  # noqa: BLE001
        log.warning("replay data unavailable %s r%s: %s", year, rnd, exc)
        return empty

    # Driver metadata for colouring / labels.
    meta: dict[str, dict[str, Any]] = {}
    for _, r in results.iterrows():
        abbr = _clean(r.get("Abbreviation"))
        if abbr:
            tid = _clean(r.get("TeamId"))
            meta[abbr] = {
                "driverId": _clean(r.get("DriverId")),
                "fullName": _clean(r.get("FullName")),
                "teamName": _clean(r.get("TeamName")),
                "teamId": tid,
                "teamLogoUrl": _team_logo_url(tid),
                "teamColor": _hex_color(r.get("TeamColor")),
                "number": _clean(r.get("DriverNumber")),
            }

    total_laps = int(laps["LapNumber"].max()) if len(laps) else 0
    frames: list[dict[str, Any]] = []

    for lap_no in range(1, total_laps + 1):
        lap_slice = laps[laps["LapNumber"] == lap_no]
        entries = []
        for _, lp in lap_slice.iterrows():
            pos = lp.get("Position")
            if pd.isna(pos):
                continue
            abbr = _clean(lp.get("Driver"))
            m = meta.get(abbr, {})
            entries.append(
                {
                    "position": int(pos),
                    "driver": abbr,
                    "driverId": m.get("driverId"),
                    "teamColor": m.get("teamColor"),
                    "teamName": m.get("teamName"),
                    "teamLogoUrl": m.get("teamLogoUrl"),
                    "lapTimeMs": _td_ms(lp.get("LapTime")),
                    "lapTime": _fmt_lap(lp.get("LapTime")),
                    "compound": _clean(lp.get("Compound")),
                    "tyreLife": _clean(lp.get("TyreLife")),
                    # Cumulative session time at this lap's timing line. It is
                    # removed below after deriving the broadcast gap.
                    "_completionMs": _td_ms(lp.get("Time")),
                }
            )
        entries.sort(key=lambda e: e["position"])
        if entries:
            leader = next((entry for entry in entries if entry["position"] == 1), entries[0])
            leader_ms = leader.get("_completionMs")
            for entry in entries:
                completion_ms = entry.pop("_completionMs", None)
                gap_ms = (
                    max(completion_ms - leader_ms, 0)
                    if completion_ms is not None and leader_ms is not None
                    else None
                )
                entry["gapMs"] = gap_ms
                entry["gap"] = (
                    "LEADER"
                    if entry["position"] == 1
                    else f"+{gap_ms / 1000:.3f}" if gap_ms is not None else None
                )
            frames.append({"lap": lap_no, "order": entries})

    return {
        "year": year,
        "round": rnd,
        "eventName": _clean(session.event.get("EventName")),
        "totalLaps": total_laps,
        "drivers": [
            {"code": k, **v} for k, v in meta.items()
        ],
        "frames": frames,
    }


def get_replay_positions(year: int, rnd: int, points_per_lap: int = 6) -> dict[str, Any]:
    """Per-driver car X/Y positions, sampled at a handful of points across
    every lap, for animating cars moving around the track outline in the
    race replay (paired with `/api/circuit/{year}/{round}` for the track
    shape). Uses the lightweight position-only channel (`Laps.get_pos_data`)
    rather than full merged car telemetry, since only X/Y is needed here.

    Fetches each driver's position trace with a *single* call covering their
    whole race, then buckets samples into laps locally with `merge_asof`,
    rather than slicing lap-by-lap (~20 drivers x ~60 laps of individual
    FastF1 calls): the latter is a couple of orders of magnitude slower and
    was liable to blow past reverse-proxy request timeouts on a full race.
    """
    session = _load_session(year, rnd, "R", with_laps=True, with_telemetry=True)
    meta = _driver_meta(session)
    payload: dict[str, Any] = {
        "year": year, "round": rnd,
        "eventName": _clean(session.event.get("EventName")),
        "totalLaps": 0,
        "drivers": [{"code": k, **v} for k, v in meta.items()],
        "laps": [],
    }
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return payload
    except Exception as exc:  # noqa: BLE001
        log.warning("replay positions unavailable %s r%s: %s", year, rnd, exc)
        return payload

    total_laps = int(laps["LapNumber"].max()) if len(laps) else 0
    payload["totalLaps"] = total_laps

    by_lap: dict[int, dict[str, list]] = {}
    abbrs = sorted(set(laps["Driver"].dropna()))
    for abbr in abbrs:
        d_laps = laps.pick_drivers(abbr).sort_values("LapNumber")
        if not len(d_laps):
            continue
        try:
            pos = d_laps.get_pos_data()
        except Exception as exc:  # noqa: BLE001
            log.warning("pos data unavailable for %s in %s r%s: %s", abbr, year, rnd, exc)
            continue
        if pos is None or not len(pos) or "X" not in pos or "Y" not in pos or "SessionTime" not in pos:
            continue

        lap_bounds = (
            d_laps[["LapNumber", "LapStartTime"]]
            .dropna()
            .rename(columns={"LapStartTime": "SessionTime"})
            .sort_values("SessionTime")
        )
        if not len(lap_bounds):
            continue

        try:
            merged = pd.merge_asof(
                pos[["SessionTime", "X", "Y"]].sort_values("SessionTime"),
                lap_bounds,
                on="SessionTime",
                direction="backward",
            )
        except Exception as exc:  # noqa: BLE001
            log.warning("lap bucketing failed for %s in %s r%s: %s", abbr, year, rnd, exc)
            continue
        merged = merged.dropna(subset=["LapNumber", "X", "Y"])

        for lap_no, group in merged.groupby("LapNumber"):
            lap_no_int = int(lap_no)
            step = max(1, len(group) // points_per_lap)
            sampled = group.iloc[::step]
            pts = [
                [round(float(x)), round(float(y))]
                for x, y in zip(sampled["X"], sampled["Y"])
                if not (math.isnan(x) or math.isnan(y))
            ]
            if pts:
                by_lap.setdefault(lap_no_int, {})[abbr] = pts

    payload["laps"] = [{"lap": n, "positions": by_lap[n]} for n in sorted(by_lap.keys())]
    return payload


# --------------------------------------------------------------------------- #
#  Driver metadata helper (colours / names) from a session's results
# --------------------------------------------------------------------------- #
def _driver_meta(session) -> dict[str, dict[str, Any]]:
    meta: dict[str, dict[str, Any]] = {}
    try:
        for _, r in session.results.iterrows():
            abbr = _clean(r.get("Abbreviation"))
            if abbr:
                tid = _clean(r.get("TeamId"))
                meta[abbr] = {
                    "driverId": _clean(r.get("DriverId")),
                    "fullName": _clean(r.get("FullName")),
                    "teamName": _clean(r.get("TeamName")),
                    "teamId": tid,
                    "teamLogoUrl": _team_logo_url(tid),
                    "teamColor": _hex_color(r.get("TeamColor")),
                    "number": _clean(r.get("DriverNumber")),
                }
    except Exception as exc:  # noqa: BLE001
        log.warning("driver meta unavailable: %s", exc)
    return meta


# --------------------------------------------------------------------------- #
#  Lap-time evolution
# --------------------------------------------------------------------------- #
def get_lap_times(year: int, rnd: int) -> dict[str, Any]:
    """Per-driver lap-time series for the race, for a lap-time evolution chart."""
    session = _load_session(year, rnd, "R", with_laps=True)
    empty = {"year": year, "round": rnd,
             "eventName": _clean(session.event.get("EventName")),
             "totalLaps": 0, "drivers": []}
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return empty
    except Exception as exc:  # noqa: BLE001
        log.warning("laptimes unavailable %s r%s: %s", year, rnd, exc)
        return empty

    meta = _driver_meta(session)
    out: dict[str, dict[str, Any]] = {}
    for _, lp in laps.iterrows():
        abbr = _clean(lp.get("Driver"))
        ms = _td_ms(lp.get("LapTime"))
        lap_no = lp.get("LapNumber")
        if abbr is None or ms is None or pd.isna(lap_no):
            continue
        m = meta.get(abbr, {})
        entry = out.setdefault(abbr, {
            "code": abbr,
            "driverId": m.get("driverId"),
            "teamName": m.get("teamName"),
            "teamColor": m.get("teamColor"),
            "laps": [],
        })
        entry["laps"].append({
            "lap": int(lap_no),
            "timeMs": ms,
            "compound": _clean(lp.get("Compound")),
        })

    total_laps = int(laps["LapNumber"].max()) if len(laps) else 0
    drivers = sorted(out.values(), key=lambda d: d["code"])
    return {"year": year, "round": rnd,
            "eventName": _clean(session.event.get("EventName")),
            "totalLaps": total_laps, "drivers": drivers}


# --------------------------------------------------------------------------- #
#  Tyre strategy & pit stops
# --------------------------------------------------------------------------- #
def get_strategy(year: int, rnd: int) -> dict[str, Any]:
    """Per-driver stint timeline (compound + lap range) and pit-stop count."""
    session = _load_session(year, rnd, "R", with_laps=True)
    empty = {"year": year, "round": rnd,
             "eventName": _clean(session.event.get("EventName")),
             "totalLaps": 0, "drivers": []}
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return empty
    except Exception as exc:  # noqa: BLE001
        log.warning("strategy unavailable %s r%s: %s", year, rnd, exc)
        return empty

    meta = _driver_meta(session)
    total_laps = int(laps["LapNumber"].max()) if len(laps) else 0
    drivers = []
    # Order drivers by finishing position where possible.
    order = list(session.results["Abbreviation"]) if session.results is not None else []
    abbrs = [a for a in order if a in set(laps["Driver"])] or sorted(set(laps["Driver"]))

    # Same classification `get_retirements` uses, so "retired" here means the
    # same thing it means there, including the ClassifiedPosition nuance
    # (a driver a lap down is NOT a retirement even though their raw Status
    # text isn't a clean "Finished").
    status_by_abbr: dict[str, dict[str, Any]] = {}
    try:
        for _, r in session.results.iterrows():
            abbr_r = _clean(r.get("Abbreviation"))
            if not abbr_r:
                continue
            status = _clean(r.get("Status")) or ""
            classified_position = _clean(r.get("ClassifiedPosition"))
            status_by_abbr[abbr_r] = {
                "status": _retirement_display_status(status) if status else None,
                "retired": not _is_finish_status(status, classified_position),
            }
    except Exception as exc:  # noqa: BLE001
        log.warning("strategy status unavailable %s r%s: %s", year, rnd, exc)

    for abbr in abbrs:
        d_laps = laps[laps["Driver"] == abbr].sort_values("LapNumber")
        if d_laps.empty:
            continue
        stints = []
        pit_stops = 0
        for stint_no, s_laps in d_laps.groupby("Stint"):
            if s_laps.empty:
                continue
            comp = _clean(s_laps["Compound"].iloc[0])
            start = int(s_laps["LapNumber"].min())
            end = int(s_laps["LapNumber"].max())
            stints.append({
                "stint": int(stint_no) if not pd.isna(stint_no) else 0,
                "compound": comp,
                "startLap": start,
                "endLap": end,
                "laps": end - start + 1,
            })
        pit_stops = max(len(stints) - 1, 0)
        m = meta.get(abbr, {})
        st = status_by_abbr.get(abbr, {})
        drivers.append({
            "code": abbr,
            "driverId": m.get("driverId"),
            "teamName": m.get("teamName"),
            "teamColor": m.get("teamColor"),
            "pitStops": pit_stops,
            "stints": sorted(stints, key=lambda s: s["startLap"]),
            "status": st.get("status"),
            "retired": st.get("retired", False),
        })

    return {"year": year, "round": rnd,
            "eventName": _clean(session.event.get("EventName")),
            "totalLaps": total_laps, "drivers": drivers}


# --------------------------------------------------------------------------- #
#  Track flags & race control
# --------------------------------------------------------------------------- #
# Flag values a "Track"-scoped race-control message can carry.
_CLOSING_FLAGS = {"GREEN", "CLEAR", "CHEQUERED"}
_INCIDENT_FLAGS = {"YELLOW", "DOUBLE YELLOW", "RED"}


def _race_control_rows(session) -> list[dict[str, Any]]:
    """Every race-control message for a session, cleaned and chronologically
    sorted, regardless of category (Flag, SafetyCar, Drs, CarEvent, Other)."""
    rcm = session.race_control_messages
    if rcm is None or not len(rcm):
        return []
    meta = _driver_meta(session)
    number_to_abbr = {v.get("number"): k for k, v in meta.items() if v.get("number")}

    rows: list[dict[str, Any]] = []
    for _, row in rcm.sort_values("Time").iterrows():
        lap_raw = row.get("Lap")
        lap_no = int(lap_raw) if lap_raw not in (None,) and not pd.isna(lap_raw) and int(lap_raw) > 0 else None
        driver_number = row.get("RacingNumber")
        rows.append({
            "time": _clean_utc(row.get("Time")),
            "lap": lap_no,
            "category": _clean(row.get("Category")),
            "flag": _clean(row.get("Flag")),
            "status": _clean(row.get("Status")),
            "scope": _clean(row.get("Scope")),
            "sector": _clean(row.get("Sector")),
            "driverNumber": _clean(driver_number),
            "driverCode": number_to_abbr.get(driver_number),
            "message": _clean(row.get("Message")),
        })
    return rows


def _session_total_laps(session) -> int:
    try:
        return (
            int(session.laps["LapNumber"].max())
            if session.laps is not None and len(session.laps) else 0
        )
    except Exception:  # noqa: BLE001
        return 0


def get_flags(year: int, rnd: int, identifier: str = "R") -> dict[str, Any]:
    """Every flag / safety-car race-control message for a session, plus the
    contiguous lap-range periods they cover (yellow, double-yellow, red, VSC,
    safety car) so a client can show a flags timeline and band them onto a
    lap-based chart such as the lap-time evolution or telemetry views."""
    session = _load_session(year, rnd, identifier, with_laps=True, with_messages=True)
    empty = {"year": year, "round": rnd, "session": identifier,
             "eventName": _clean(session.event.get("EventName")),
             "totalLaps": 0, "events": [], "periods": []}
    try:
        rows = _race_control_rows(session)
        if not rows:
            return empty
    except Exception as exc:  # noqa: BLE001
        log.warning("flags unavailable %s r%s %s: %s", year, rnd, identifier, exc)
        return empty

    total_laps = _session_total_laps(session)

    # Keep flag / safety-car messages plus anything else that carries a flag
    # value; drop generic chatter (DRS enabled, car events, etc.); that full
    # log is available separately via get_race_control().
    events = [r for r in rows if r["category"] in ("Flag", "SafetyCar") or r["flag"]]
    periods = _flag_periods(events, total_laps)

    return {"year": year, "round": rnd, "session": identifier,
            "eventName": _clean(session.event.get("EventName")),
            "totalLaps": total_laps, "events": events, "periods": periods}


def get_race_control(year: int, rnd: int, identifier: str = "R") -> dict[str, Any]:
    """The complete, unfiltered race-control message log for a session:
    flags and safety-car periods, but also DRS enable/disable, car events,
    and "Other" messages such as investigations, penalties and reprimands,
    in chronological order."""
    session = _load_session(year, rnd, identifier, with_laps=True, with_messages=True)
    empty = {"year": year, "round": rnd, "session": identifier,
             "eventName": _clean(session.event.get("EventName")),
             "totalLaps": 0, "messages": []}
    try:
        rows = _race_control_rows(session)
    except Exception as exc:  # noqa: BLE001
        log.warning("race control unavailable %s r%s %s: %s", year, rnd, identifier, exc)
        return empty

    return {"year": year, "round": rnd, "session": identifier,
            "eventName": _clean(session.event.get("EventName")),
            "totalLaps": _session_total_laps(session), "messages": rows}


# Ordered so more specific phrasing is checked before generic "penalty" text:
# e.g. "STOP AND GO" and "TIME PENALTY" can both appear in the same message,
# and stewards' wording isn't perfectly consistent year to year.
_PENALTY_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bDISQUALIFIED\b", re.I), "Disqualification"),
    (re.compile(r"STOP[\s/-]?AND[\s/-]?GO", re.I), "Stop & Go Penalty"),
    (re.compile(r"DRIVE[\s-]?THROUGH", re.I), "Drive Through Penalty"),
    (re.compile(r"GRID PENALTY|PLACE(?:S)?\s+GRID", re.I), "Grid Penalty"),
    (re.compile(r"TIME PENALTY", re.I), "Time Penalty"),
    (re.compile(r"\bREPRIMAND\b", re.I), "Reprimand"),
]

# Race-control messages name the car under penalty as e.g. "CAR 44 (HAM)";
# when a message involves multiple cars (a collision between two of them),
# the FIRST one mentioned is consistently the penalized driver by FIA
# convention ("... PENALTY FOR CAR 44 (HAM) - CAUSING A COLLISION WITH CAR
# 16 (LEC)"), so taking the first regex match is deliberate, not incidental.
_CAR_RE = re.compile(r"CAR\s+(\d+)\s*\(([A-Z]{2,3})\)")


def _classify_penalty(message: str) -> str | None:
    for pattern, label in _PENALTY_PATTERNS:
        if pattern.search(message):
            return label
    return None


def _penalty_reason(message: str) -> str | None:
    """The stewards' stated reason is everything after the first " - ":
    e.g. "5 SECOND TIME PENALTY FOR CAR 44 (HAM) - CAUSING A COLLISION"
    becomes "Causing a collision". Falls back to the full message if that
    dash-separated convention isn't present."""
    if " - " in message:
        reason = message.split(" - ", 1)[1].strip().rstrip(".")
        return reason[:1].upper() + reason[1:].lower() if reason else None
    return None


_NUMBER_WORDS = {
    "ONE": "1", "TWO": "2", "THREE": "3", "FOUR": "4", "FIVE": "5",
    "SIX": "6", "SEVEN": "7", "EIGHT": "8", "NINE": "9", "TEN": "10",
}
_SECONDS_RE = re.compile(r"(\d+)\s*SECONDS?", re.I)
_PLACES_RE = re.compile(
    r"(\d+|ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)\s*PLACES?", re.I
)


def _penalty_value(message: str, penalty_type: str) -> str | None:
    """The size of the penalty: "5 second" for a time penalty or stop-and-go,
    "3 place" for a grid penalty, pulled from the part of the message the
    reason-extraction above deliberately skips (everything before the first
    " - "). Not every message states one explicitly (a Reprimand or
    Disqualification usually doesn't carry a duration), hence Optional."""
    if penalty_type in ("Time Penalty", "Stop & Go Penalty"):
        m = _SECONDS_RE.search(message)
        if m:
            n = m.group(1)
            return f"{n} second{'' if n == '1' else 's'}"
    elif penalty_type == "Grid Penalty":
        m = _PLACES_RE.search(message)
        if m:
            n = _NUMBER_WORDS.get(m.group(1).upper(), m.group(1))
            return f"{n} place{'' if n == '1' else 's'}"
    return None


def get_penalties(year: int, rnd: int, identifier: str = "R") -> dict[str, Any]:
    """Driver penalties: time penalties, drive-throughs, stop-and-gos, grid
    penalties, reprimands and disqualifications, issued during a session,
    derived from the race-control message log with the stewards' stated
    reasoning attached. There's no structured "penalty" field in the source
    data; these are FIA race-control messages (Category "Other") classified
    by their wording, same as a viewer reading the live timing feed would."""
    session = _load_session(year, rnd, identifier, with_laps=True, with_messages=True)
    empty = {"year": year, "round": rnd, "session": identifier,
             "eventName": _clean(session.event.get("EventName")),
             "penalties": []}
    try:
        rows = _race_control_rows(session)
        if not rows:
            return empty
    except Exception as exc:  # noqa: BLE001
        log.warning("penalties unavailable %s r%s %s: %s", year, rnd, identifier, exc)
        return empty

    meta = _driver_meta(session)
    number_to_abbr = {v.get("number"): k for k, v in meta.items() if v.get("number")}

    penalties: list[dict[str, Any]] = []
    for r in rows:
        if r["category"] != "Other":
            continue
        message = r["message"] or ""
        penalty_type = _classify_penalty(message)
        if not penalty_type:
            continue

        abbr = None
        car_match = _CAR_RE.search(message)
        if car_match:
            abbr = number_to_abbr.get(car_match.group(1)) or car_match.group(2)
        if not abbr:
            abbr = r["driverCode"]
        m = meta.get(abbr, {}) if abbr else {}

        penalties.append({
            "time": r["time"],
            "lap": r["lap"],
            "type": penalty_type,
            "value": _penalty_value(message, penalty_type),
            "reason": _penalty_reason(message),
            "message": message,
            "driverCode": abbr,
            "driverId": m.get("driverId"),
            "driverName": m.get("fullName"),
            "teamName": m.get("teamName"),
            "teamLogoUrl": m.get("teamLogoUrl"),
            "teamColor": m.get("teamColor"),
        })

    return {"year": year, "round": rnd, "session": identifier,
            "eventName": _clean(session.event.get("EventName")),
            "penalties": penalties}


def _flag_periods(events: list[dict[str, Any]], total_laps: int) -> list[dict[str, Any]]:
    """Collapse the raw event stream into contiguous track-status periods,
    each spanning a lap range, e.g. `{"type": "SC", "startLap": 12,
    "endLap": 14, "reason": "SAFETY CAR DEPLOYED"}`."""
    periods: list[dict[str, Any]] = []
    open_period: Optional[dict[str, Any]] = None

    def close(end_lap: Optional[int]) -> None:
        nonlocal open_period
        if open_period is None:
            return
        open_period["endLap"] = end_lap or open_period["startLap"]
        periods.append(open_period)
        open_period = None

    for ev in events:
        category = ev["category"]
        flag = (ev["flag"] or "").upper()
        message = (ev["message"] or "").upper()
        lap = ev["lap"]

        if category == "Flag" and ev["scope"] == "Track":
            if flag in _CLOSING_FLAGS:
                close(lap)
            elif flag in _INCIDENT_FLAGS:
                close(lap)  # a new flag supersedes whatever was already open
                open_period = {
                    "type": "DOUBLE_YELLOW" if flag == "DOUBLE YELLOW" else flag,
                    "startLap": lap or 1,
                    "endLap": None,
                    "reason": ev["message"],
                }
        elif category == "SafetyCar":
            if "DEPLOYED" in message:
                close(lap)
                open_period = {
                    "type": "VSC" if "VIRTUAL" in message else "SC",
                    "startLap": lap or 1,
                    "endLap": None,
                    "reason": ev["message"],
                }
            elif "ENDING" in message:
                close(lap)

    if open_period is not None:
        close(total_laps or open_period["startLap"])

    return periods


# --------------------------------------------------------------------------- #
#  Weather
# --------------------------------------------------------------------------- #
def get_weather(year: int, rnd: int, identifier: str = "R") -> dict[str, Any]:
    """Session weather summary (temps, humidity, wind, rainfall)."""
    session = _load_session(
        year, rnd, identifier, with_laps=False, with_weather=True,
    )
    payload: dict[str, Any] = {
        "year": year, "round": rnd, "session": identifier,
        "eventName": _clean(session.event.get("EventName")),
        "available": False,
    }
    try:
        wx = session.weather_data
        if wx is None or not len(wx):
            return payload
        def stat(col):
            if col not in wx:
                return None
            series = pd.to_numeric(wx[col], errors="coerce").dropna()
            return round(float(series.mean()), 1) if len(series) else None
        rain_any = bool(wx["Rainfall"].any()) if "Rainfall" in wx else False
        payload.update({
            "available": True,
            "airTemp": stat("AirTemp"),
            "trackTemp": stat("TrackTemp"),
            "humidity": stat("Humidity"),
            "pressure": stat("Pressure"),
            "windSpeed": stat("WindSpeed"),
            "rainfall": rain_any,
            "airTempMax": round(float(pd.to_numeric(wx["AirTemp"], errors="coerce").max()), 1) if "AirTemp" in wx else None,
            "trackTempMax": round(float(pd.to_numeric(wx["TrackTemp"], errors="coerce").max()), 1) if "TrackTemp" in wx else None,
            "timeline": [
                {
                    "timeSeconds": round(float(row.get("Time").total_seconds()), 1)
                    if row.get("Time") is not None and not pd.isna(row.get("Time"))
                    and hasattr(row.get("Time"), "total_seconds") else float(index),
                    "airTemp": _clean(row.get("AirTemp")),
                    "trackTemp": _clean(row.get("TrackTemp")),
                    "humidity": _clean(row.get("Humidity")),
                    "pressure": _clean(row.get("Pressure")),
                    "windSpeed": _clean(row.get("WindSpeed")),
                    "rainfall": bool(row.get("Rainfall"))
                    if row.get("Rainfall") is not None and not pd.isna(row.get("Rainfall")) else False,
                }
                for index, (_, row) in enumerate(wx.iterrows())
            ],
        })
    except Exception as exc:  # noqa: BLE001
        log.warning("weather unavailable %s r%s: %s", year, rnd, exc)
    return payload


# --------------------------------------------------------------------------- #
#  Telemetry
# --------------------------------------------------------------------------- #
def _genuine_position_samples(
    distances: np.ndarray, xs: np.ndarray, ys: np.ndarray
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Drop held/duplicate position samples, keeping only genuine updates.

    `get_telemetry()` merges the low-frequency (~3.7Hz) position channel onto
    the much faster car-data channel by holding each X/Y across every row
    until the next real position update. Interpolating X/Y directly against
    distance therefore reproduces those plateaus (a staircase), which draws
    as a hard-edged polygon no matter how finely it's resampled or how much
    the frontend smooths it, because the resampled points already sit on
    straight lines with sharp joints between them.

    Keeping just the rows where the position actually changed (each at the
    distance where that update landed) means the subsequent interpolation
    runs between real samples, so corners curve instead of cornering.
    """
    if len(xs) == 0:
        return distances, xs, ys
    changed = np.ones(len(xs), dtype=bool)
    changed[1:] = (np.round(xs[1:], 1) != np.round(xs[:-1], 1)) | (
        np.round(ys[1:], 1) != np.round(ys[:-1], 1)
    )
    # Fewer than 2 genuine samples can't define a line; fall back to the raw
    # series rather than returning something undrawable.
    if changed.sum() < 2:
        return distances, xs, ys
    return distances[changed], xs[changed], ys[changed]


def _lap_telemetry(session, abbr: str, which: str = "fastest") -> Optional[dict[str, Any]]:
    d_laps = session.laps.pick_drivers(abbr)
    if d_laps is None or not len(d_laps):
        return None
    if which == "fastest":
        lap = d_laps.pick_fastest()
    else:
        try:
            lap = d_laps[d_laps["LapNumber"] == int(which)].iloc[0]
        except Exception:  # noqa: BLE001
            lap = d_laps.pick_fastest()
    if lap is None:
        return None
    tel = lap.get_telemetry()
    if tel is None or not len(tel):
        return None

    # Resample at fixed steps of *distance* along the lap rather than every
    # Nth row (uniform in time): the merged position channel updates at
    # ~3.7Hz while the surrounding car-data channel updates much faster, so a
    # row-uniform downsample over-samples the held/duplicate stretches and
    # under-samples the transitions between them. This is the exact same bug
    # that made the circuit-map outline render as a jagged polygon through
    # corners (see get_circuit_map / _pick_outline_lap); the telemetry
    # mini-map draws this trace's raw x/y too, so it inherited the same
    # jaggedness. Fixed the same way: interpolate every channel onto evenly
    # spaced distance samples.
    dist_full = tel["Distance"].to_numpy(dtype=float)
    if len(dist_full) > 1 and dist_full[-1] > dist_full[0]:
        target_points = max(200, min(800, round((dist_full[-1] - dist_full[0]) / 8)))
        order = np.argsort(dist_full, kind="stable")
        d_sorted = dist_full[order]
        targets = np.linspace(d_sorted[0], d_sorted[-1], target_points)

        def _resample(col: str) -> np.ndarray:
            vals = tel[col].to_numpy(dtype=float)[order]
            return np.interp(targets, d_sorted, vals)


        distance_arr = targets
        # Position gets the extra dedup pass; the other channels genuinely
        # update at the higher car-data rate, so they interpolate directly.
        d_pos, x_pos, y_pos = _genuine_position_samples(
            d_sorted,
            tel["X"].to_numpy(dtype=float)[order],
            tel["Y"].to_numpy(dtype=float)[order],
        )
        x_arr = np.interp(targets, d_pos, x_pos)
        y_arr = np.interp(targets, d_pos, y_pos)
        speed_arr = _resample("Speed")
        throttle_arr = _resample("Throttle")
        brake_arr = _resample("Brake")
        gear_arr = _resample("nGear")
        rpm_arr = _resample("RPM")
        drs_arr = _resample("DRS")
        tsec_full = (tel["Time"] - tel["Time"].iloc[0]).dt.total_seconds().to_numpy(dtype=float)[order]
        time_s = [round(float(t), 3) for t in np.interp(targets, d_sorted, tsec_full)]
    else:
        step = max(1, len(tel) // 500)
        tel = tel.iloc[::step]
        distance_arr = tel["Distance"].to_numpy(dtype=float)
        x_arr = tel["X"].to_numpy(dtype=float)
        y_arr = tel["Y"].to_numpy(dtype=float)
        speed_arr = tel["Speed"].to_numpy(dtype=float)
        throttle_arr = tel["Throttle"].to_numpy(dtype=float)
        brake_arr = tel["Brake"].to_numpy(dtype=float)
        gear_arr = tel["nGear"].to_numpy(dtype=float)
        rpm_arr = tel["RPM"].to_numpy(dtype=float)
        drs_arr = tel["DRS"].to_numpy(dtype=float)
        try:
            tsec = (tel["Time"] - tel["Time"].iloc[0]).dt.total_seconds()
            time_s = [round(float(x), 3) for x in tsec]
        except Exception:  # noqa: BLE001
            time_s = []

    return {
        "code": abbr,
        "lapNumber": int(lap["LapNumber"]) if not pd.isna(lap.get("LapNumber")) else None,
        "lapTime": _fmt_lap(lap.get("LapTime")),
        "lapTimeMs": _td_ms(lap.get("LapTime")),
        "compound": _clean(lap.get("Compound")),
        "distance": [round(float(d), 1) for d in distance_arr],
        "time": time_s,
        "speed": [round(float(s), 1) for s in speed_arr],
        "throttle": [round(float(t)) for t in throttle_arr],
        "brake": [int(round(b)) for b in brake_arr],
        "gear": [int(round(g)) for g in gear_arr],
        "rpm": [int(round(r)) for r in rpm_arr],
        "drs": [int(round(d)) for d in drs_arr],
        "x": [float(v) for v in x_arr],
        "y": [float(v) for v in y_arr],
    }


def get_telemetry(year: int, rnd: int, driver: str, lap: str = "fastest") -> dict[str, Any]:
    session = _load_session(year, rnd, "R", with_laps=True, with_telemetry=True)
    meta = _driver_meta(session)
    trace = _lap_telemetry(session, driver, lap)
    if trace is None:
        return {"year": year, "round": rnd, "available": False, "driver": driver}
    m = meta.get(driver, {})
    trace.update({"driverName": m.get("fullName"), "teamName": m.get("teamName"),
                  "teamColor": m.get("teamColor")})
    return {"year": year, "round": rnd, "available": True,
            "eventName": _clean(session.event.get("EventName")), "trace": trace}


def get_telemetry_compare(year: int, rnd: int, d1: str, d2: str) -> dict[str, Any]:
    session = _load_session(year, rnd, "R", with_laps=True, with_telemetry=True)
    meta = _driver_meta(session)
    traces = []
    for code in (d1, d2):
        t = _lap_telemetry(session, code, "fastest")
        if t is not None:
            m = meta.get(code, {})
            t.update({"driverName": m.get("fullName"), "teamName": m.get("teamName"),
                      "teamColor": m.get("teamColor")})
            traces.append(t)
    return {"year": year, "round": rnd,
            "eventName": _clean(session.event.get("EventName")),
            "available": len(traces) == 2, "traces": traces}


def list_race_drivers(year: int, rnd: int) -> list[dict[str, Any]]:
    """Lightweight driver list for a race (for telemetry/lap pickers)."""
    session = _load_session(year, rnd, "R", with_laps=False)
    out = []
    try:
        for _, r in session.results.iterrows():
            out.append({
                "code": _clean(r.get("Abbreviation")),
                "driverId": _clean(r.get("DriverId")),
                "fullName": _clean(r.get("FullName")),
                "teamName": _clean(r.get("TeamName")),
                "teamColor": _hex_color(r.get("TeamColor")),
                "number": _clean(r.get("DriverNumber")),
            })
    except Exception as exc:  # noqa: BLE001
        log.warning("race drivers unavailable %s r%s: %s", year, rnd, exc)
    return out


# --------------------------------------------------------------------------- #
#  Retirements (single race) & reliability (season, Ergast-based)
# --------------------------------------------------------------------------- #
def _is_finish_status(status: str, classified_position: Optional[str] = None) -> bool:
    # `ClassifiedPosition` is the authoritative signal: a plain integer means
    # the driver was officially classified as a finisher, including drivers
    # who finished a lap or more down, who are NOT retirements even though
    # their free-text `Status` isn't a reliably consistent "Finished"/"+N
    # Lap(s)" string across FastF1's own timing data vs. its Ergast/Jolpica
    # fallback. Only "R" (retired), "D" (disqualified), "E" (excluded),
    # "W" (withdrawn), "F" (failed to qualify) and "N" (not classified) are
    # genuinely not-a-finish.
    if classified_position and classified_position.strip().isdigit():
        return True
    if not status:
        return False
    return status == "Finished" or status.strip().startswith("+")


# `Status` text that describes the driver's on-track state rather than an
# actual DNF reason. FastF1 sometimes leaves a retiree's `Status` at
# "Lapped" (their last recorded track status before they stopped) instead of
# supplying a specific cause like "Collision" or "Engine". Shown verbatim
# that reads like a finishing description, not a retirement, so we relabel it.
_AMBIGUOUS_RETIREMENT_STATUS = {"lapped", ""}


def _retirement_display_status(status: str) -> str:
    if status.strip().lower() in _AMBIGUOUS_RETIREMENT_STATUS:
        return "Retired"
    return status


def _laps_completed_by_driver_number(session) -> dict[str, int]:
    """Each driver's last completed lap number, from the timing data itself.

    `SessionResults.Laps` (the count FastF1's own docs point to) is only
    populated when results come via the Ergast fallback; the primary F1
    timing path leaves it empty, so it can't be relied on. `session.laps`
    is authoritative and already loaded for this endpoint; take each
    driver's highest `LapNumber` directly from it instead.
    """
    try:
        laps = session.laps
        if laps is None or not len(laps) or "LapNumber" not in laps or "DriverNumber" not in laps:
            return {}
        maxima = laps.groupby("DriverNumber")["LapNumber"].max()
        return {str(num): int(lap) for num, lap in maxima.items() if pd.notna(lap)}
    except Exception as exc:  # noqa: BLE001
        log.warning("laps-completed lookup failed: %s", exc)
        return {}


def get_retirements(year: int, rnd: int) -> dict[str, Any]:
    session = _load_session(year, rnd, "R", with_laps=True)
    laps_completed = _laps_completed_by_driver_number(session)
    retirements = []
    try:
        for _, r in session.results.iterrows():
            status = _clean(r.get("Status")) or ""
            classified_position = _clean(r.get("ClassifiedPosition"))
            if not _is_finish_status(status, classified_position):
                driver_number = _clean(r.get("DriverNumber"))
                team_id = _clean(r.get("TeamId"))
                retirements.append({
                    "driver": _clean(r.get("Abbreviation")),
                    "fullName": _clean(r.get("FullName")),
                    "driverId": _clean(r.get("DriverId")),
                    "teamName": _clean(r.get("TeamName")),
                    "teamId": team_id,
                    "teamLogoUrl": _team_logo_url(team_id),
                    "teamColor": _hex_color(r.get("TeamColor")),
                    "status": _retirement_display_status(status),
                    "classifiedPosition": classified_position,
                    "lapsCompleted": laps_completed.get(driver_number),
                })
    except Exception as exc:  # noqa: BLE001
        log.warning("retirements unavailable %s r%s: %s", year, rnd, exc)
    return {"year": year, "round": rnd,
            "eventName": _clean(session.event.get("EventName")),
            "retirements": retirements}


# Group raw Ergast status strings into human categories.
_MECHANICAL = {
    "Engine", "Power Unit", "Gearbox", "Hydraulics", "Transmission", "Electrical",
    "Turbo", "Brakes", "Suspension", "Clutch", "Fuel system", "Fuel pump",
    "Cooling", "Oil leak", "Water leak", "Exhaust", "Driveshaft", "Wheel",
    "Overheating", "Battery", "ERS", "Radiator", "Vibrations", "Throttle",
    "Differential", "Mechanical", "Steering", "Oil pressure", "Fuel pressure",
}
_COLLISION = {"Accident", "Collision", "Collision damage", "Spun off", "Damage", "Puncture"}


def _classify_status(status: str) -> str:
    if _is_finish_status(status):
        return "Finished"
    if status in _COLLISION:
        return "Accident"
    if status in _MECHANICAL:
        return "Mechanical"
    if status == "Disqualified":
        return "Disqualified"
    if status in ("Retired", "Withdrew", "Did not start", "Did not qualify"):
        return "Other"
    return "Mechanical" if status else "Other"


def get_reliability(year: int) -> dict[str, Any]:
    """Season DNF breakdown per driver and per team, derived from Ergast results."""
    resp = _ergast.get_race_results(season=year, limit=100)
    contents, _ = _collect_multi(resp)
    drivers: dict[str, dict[str, Any]] = {}
    teams: dict[str, dict[str, Any]] = {}
    races = 0

    for race_df in contents:
        if race_df is None or race_df.empty or "status" not in race_df:
            continue
        races += 1
        for _, r in race_df.iterrows():
            status = _clean(r.get("status")) or ""
            cat = _classify_status(status)
            did = _clean(r.get("driverId"))
            tid = _clean(r.get("constructorId"))
            name = f'{_clean(r.get("givenName")) or ""} {_clean(r.get("familyName")) or ""}'.strip()
            if did:
                d = drivers.setdefault(did, {
                    "driverId": did, "name": name, "teamId": tid,
                    "finished": 0, "mechanical": 0, "accident": 0,
                    "disqualified": 0, "other": 0, "dnf": 0, "starts": 0,
                })
                d["starts"] += 1
                key = {"Finished": "finished", "Mechanical": "mechanical",
                       "Accident": "accident", "Disqualified": "disqualified",
                       "Other": "other"}[cat]
                d[key] += 1
                if cat != "Finished":
                    d["dnf"] += 1
            if tid:
                t = teams.setdefault(tid, {
                    "teamId": tid, "teamName": _clean(r.get("constructorName")),
                    "finished": 0, "mechanical": 0, "accident": 0,
                    "disqualified": 0, "other": 0, "dnf": 0, "starts": 0,
                })
                t["starts"] += 1
                key = {"Finished": "finished", "Mechanical": "mechanical",
                       "Accident": "accident", "Disqualified": "disqualified",
                       "Other": "other"}[cat]
                t[key] += 1
                if cat != "Finished":
                    t["dnf"] += 1

    def finish_rate(x):
        return round(100 * x["finished"] / x["starts"], 1) if x["starts"] else 0.0
    driver_list = sorted(drivers.values(), key=lambda x: (-x["dnf"], x["name"]))
    for d in driver_list:
        d["finishRate"] = finish_rate(d)
    team_list = sorted(teams.values(), key=lambda x: (-x["dnf"], x["teamName"] or ""))
    for t in team_list:
        t["finishRate"] = finish_rate(t)
        t["teamLogoUrl"] = _team_logo_url(t.get("teamId"))

    return {"year": year, "races": races, "drivers": driver_list, "teams": team_list}


# --------------------------------------------------------------------------- #
#  Head-to-head driver comparison (season, Ergast-based)
# --------------------------------------------------------------------------- #
def get_compare(year: int, d1: str, d2: str) -> dict[str, Any]:
    race_resp = _ergast.get_race_results(season=year, limit=100)
    race_contents, desc = _collect_multi(race_resp)
    qual_resp = _ergast.get_qualifying_results(season=year, limit=100)
    qual_contents, _ = _collect_multi(qual_resp)

    def blank(did):
        return {"driverId": did, "name": did, "teamName": None, "teamId": None,
                "points": 0.0, "wins": 0, "podiums": 0, "poles": 0,
                "bestFinish": None, "dnf": 0, "raceWins_h2h": 0, "qualWins_h2h": 0,
                "rounds": []}

    agg = {d1: blank(d1), d2: blank(d2)}

    # Race results
    for idx, race_df in enumerate(race_contents):
        if race_df is None or race_df.empty or "driverId" not in race_df:
            continue
        rnd = int(desc.iloc[idx]["round"]) if idx < len(desc) and "round" in desc else None
        race_name = _clean(desc.iloc[idx].get("raceName")) if idx < len(desc) else None
        pos = {}
        for did in (d1, d2):
            row = race_df[race_df["driverId"] == did]
            if row.empty:
                continue
            r = row.iloc[0]
            a = agg[did]
            p = r.get("position")
            pi = int(p) if p is not None and not pd.isna(p) else None
            pts = float(r.get("points")) if r.get("points") is not None and not pd.isna(r.get("points")) else 0.0
            status = _clean(r.get("status")) or ""
            a["points"] += pts
            a["name"] = f'{_clean(r.get("givenName")) or ""} {_clean(r.get("familyName")) or ""}'.strip() or did
            a["teamName"] = _clean(r.get("constructorName")) or a["teamName"]
            a["teamId"] = _clean(r.get("constructorId")) or a["teamId"]
            if pi == 1:
                a["wins"] += 1
            if pi is not None and pi <= 3:
                a["podiums"] += 1
            if pi is not None and (a["bestFinish"] is None or pi < a["bestFinish"]):
                a["bestFinish"] = pi
            if not _is_finish_status(status):
                a["dnf"] += 1
            if pi is not None:
                pos[did] = pi
            a["rounds"].append({"round": rnd, "raceName": race_name, "position": pi})
        if d1 in pos and d2 in pos:
            if pos[d1] < pos[d2]:
                agg[d1]["raceWins_h2h"] += 1
            elif pos[d2] < pos[d1]:
                agg[d2]["raceWins_h2h"] += 1

    # Qualifying head-to-head + poles
    try:
        for q_df in qual_contents:
            if q_df is None or q_df.empty or "driverId" not in q_df:
                continue
            qpos = {}
            for did in (d1, d2):
                row = q_df[q_df["driverId"] == did]
                if row.empty:
                    continue
                p = row.iloc[0].get("position")
                pi = int(p) if p is not None and not pd.isna(p) else None
                if pi == 1:
                    agg[did]["poles"] += 1
                if pi is not None:
                    qpos[did] = pi
            if d1 in qpos and d2 in qpos:
                if qpos[d1] < qpos[d2]:
                    agg[d1]["qualWins_h2h"] += 1
                elif qpos[d2] < qpos[d1]:
                    agg[d2]["qualWins_h2h"] += 1
    except Exception as exc:  # noqa: BLE001
        log.warning("qualifying compare unavailable %s: %s", year, exc)

    for a in agg.values():
        a["points"] = round(a["points"], 1)
        a["teamLogoUrl"] = _team_logo_url(a.get("teamId"))
    return {"year": year, "drivers": [agg[d1], agg[d2]]}


# --------------------------------------------------------------------------- #
#  Standings evolution (cumulative points per round)
# --------------------------------------------------------------------------- #
# In-process memo cache for `_points_progression`. Without this, scrubbing
# the WDC calculator's time-machine slider across N different rounds meant N
# independent full re-fetches of the season's race + sprint results from
# Ergast/Jolpica (each one paginated, each one slow): every round was a
# fresh cache miss even though they all need the exact same underlying data.
# The per-endpoint response cache in main.py doesn't help here because its
# cache key includes `through_round`, so each round gets its own entry; this
# cache sits one layer lower, keyed only by year, so the expensive fetch
# happens once and every round after that is a cheap in-memory scan. TTL
# matches main.py's default endpoint-cache TTL.
#
# Per-process, in-memory, not shared across workers — see the WEB_CONCURRENCY
# guard in main.py, which fails startup rather than let multiple workers each
# hold a divergent copy of this cache.
_PROGRESSION_CACHE_TTL_SECONDS = 60 * 60 * 6
_progression_cache: dict[int, tuple[float, dict[str, Any]]] = {}


def _points_progression(year: int) -> dict[str, Any]:
    """Shared round-by-round cumulative-points computation (memoized per year).

    Combines race and sprint points from Ergast/Jolpica so the progression
    matches the official championship. Powers both `get_standings_evolution`
    (the progress chart) and the WDC calculator's "as of round N" historical
    snapshots, so both features agree on exactly how many points each driver
    had at any given point in the season.

    Returns {"rounds": [...], "drivers": {driverId: {givenName, familyName,
    code, teamName, teamColor, series: [{round, points}]}}}. `series` is
    cumulative and has one entry per round the driver was present for.
    """
    now = time.time()
    hit = _progression_cache.get(year)
    if hit and (now - hit[0]) < _PROGRESSION_CACHE_TTL_SECONDS:
        return hit[1]
    value = _compute_points_progression(year)
    _progression_cache[year] = (now, value)
    return value


def _compute_points_progression(year: int) -> dict[str, Any]:
    race_resp = _ergast.get_race_results(season=year, limit=100)
    race_contents, race_desc = _collect_multi(race_resp)

    # Sprint points keyed by (round, driverId).
    sprint_points: dict[tuple[int, str], float] = {}
    try:
        sr = _ergast.get_sprint_results(season=year, limit=100)
        s_contents, s_desc = _collect_multi(sr)
        for idx, df in enumerate(s_contents):
            if df is None or df.empty or "driverId" not in df:
                continue
            rnd = int(s_desc.iloc[idx]["round"]) if idx < len(s_desc) and "round" in s_desc else None
            if rnd is None:
                continue
            for _, r in df.iterrows():
                did = _clean(r.get("driverId"))
                pts = r.get("points")
                if did and pts is not None and not pd.isna(pts):
                    sprint_points[(rnd, did)] = sprint_points.get((rnd, did), 0.0) + float(pts)
    except Exception as exc:  # noqa: BLE001
        log.warning("sprint results unavailable %s: %s", year, exc)

    # Colours from the season driver list.
    colors = {d["driverId"]: d.get("teamColor") for d in get_drivers(year)}

    running: dict[str, float] = {}
    series: dict[str, list[dict[str, Any]]] = {}
    meta: dict[str, dict[str, Any]] = {}
    rounds: list[int] = []

    for idx, df in enumerate(race_contents):
        if df is None or df.empty or "driverId" not in df:
            continue
        rnd = int(race_desc.iloc[idx]["round"]) if idx < len(race_desc) and "round" in race_desc else None
        if rnd is None:
            continue
        rounds.append(rnd)

        for _, r in df.iterrows():
            did = _clean(r.get("driverId"))
            if not did:
                continue
            pts = r.get("points")
            gained = float(pts) if pts is not None and not pd.isna(pts) else 0.0
            gained += sprint_points.get((rnd, did), 0.0)
            running[did] = running.get(did, 0.0) + gained
            meta[did] = {
                "driverId": did,
                "givenName": _clean(r.get("givenName")),
                "familyName": _clean(r.get("familyName")),
                "code": _clean(r.get("driverCode")),
                "teamName": _clean(r.get("constructorName")),
                "teamColor": colors.get(did),
            }

        # Snapshot cumulative points for every driver seen so far.
        for did in meta:
            series.setdefault(did, []).append({"round": rnd, "points": round(running.get(did, 0.0), 1)})

    drivers = {did: {**m, "series": series.get(did, [])} for did, m in meta.items()}
    return {"rounds": rounds, "drivers": drivers}


def get_standings_evolution(year: int) -> dict[str, Any]:
    """Round-by-round cumulative championship points per driver."""
    progression = _points_progression(year)

    drivers = []
    for did, d in progression["drivers"].items():
        name = f'{d.get("givenName") or ""} {d.get("familyName") or ""}'.strip()
        points = d["series"][-1]["points"] if d["series"] else 0.0
        drivers.append(
            {
                "driverId": did,
                "name": name,
                "code": d.get("code"),
                "teamName": d.get("teamName"),
                "teamColor": d.get("teamColor"),
                "points": points,
                "series": d["series"],
            }
        )
    drivers.sort(key=lambda d: -d["points"])

    return {"year": year, "rounds": progression["rounds"], "drivers": drivers}
