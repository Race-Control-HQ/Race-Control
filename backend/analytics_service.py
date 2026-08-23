"""
analytics_service.py
--------------------
Derived-analysis endpoints layered on top of `fastf1_service`.

Everything in here answers a question the raw endpoints only pose: not "what
were the lap times" but "who was actually quicker, and where did the race turn".
The inputs are columns FastF1 already loads for the existing endpoints
(`Time`, `TrackStatus`, `LapStartTime`, sector times, pit in/out), so a new
visualisation costs a computation rather than another upstream fetch.

Two conventions hold throughout, because there are three client apps (iOS,
Android, Web) rendering these with three different chart stacks:

1. **Payloads are chart-ready.** Series arrive pre-binned, pre-sorted, with team
   colours resolved and axis domains supplied. A client maps a prepared series
   onto a primitive it already has; it does not re-derive anything. Any
   arithmetic done on the client is arithmetic that can diverge between
   platforms.
2. **Missing data is `available: false`, never an exception.** FastF1 sessions
   are routinely partial for older or interrupted races, so every payload has a
   valid empty shape and the clients reuse their existing empty states.

Helpers are called through the `f1` module reference rather than imported by
name so the existing test convention (monkeypatching `fastf1_service._load_session`)
reaches this module too.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Optional

import numpy as np
import pandas as pd

import fastf1_service as f1

log = logging.getLogger("racecontrol")

# FastF1 encodes a lap's track status as the concatenation of every status seen
# during it, so a lap run wholly under green is exactly "1" — anything else
# ("14", "2", "6") saw a yellow, safety car, VSC or red at some point.
_ALL_CLEAR = "1"


# --------------------------------------------------------------------------- #
#  Shared lap-timing derivation
# --------------------------------------------------------------------------- #
def _race_start_ms(laps: Any) -> Optional[int]:
    """Session time at which the race started, in milliseconds.

    `Laps.Time` is the session clock at the end of each lap, not time since the
    start, so it needs a common origin subtracted before laps can be compared
    across drivers. Every car starts together, so the earliest lap-1
    `LapStartTime` in the field is that origin.
    """
    try:
        lap_one = laps[laps["LapNumber"] == 1]
        if not len(lap_one) or "LapStartTime" not in lap_one:
            return None
        starts = [f1._td_ms(v) for v in lap_one["LapStartTime"]]
        starts = [s for s in starts if s is not None]
        return min(starts) if starts else None
    except Exception as exc:  # noqa: BLE001
        log.warning("race start time unavailable: %s", exc)
        return None


def _elapsed_ms_by_driver(laps: Any) -> dict[str, dict[int, int]]:
    """`{driver_code: {lap_number: ms elapsed since the race start}}`.

    Prefers the session clock (`Time`), which is authoritative and already
    accounts for anything that happened mid-lap. Falls back to accumulating
    `LapTime` per driver when the clock is missing, which is the case for some
    older seasons; that fallback cannot represent time lost outside a timed lap,
    so it is second choice rather than the default.
    """
    out: dict[str, dict[int, int]] = {}
    t0 = _race_start_ms(laps)

    if t0 is not None and "Time" in laps:
        for _, lp in laps.iterrows():
            abbr = f1._clean(lp.get("Driver"))
            lap_no = lp.get("LapNumber")
            absolute = f1._td_ms(lp.get("Time"))
            if abbr is None or absolute is None or pd.isna(lap_no):
                continue
            elapsed = absolute - t0
            # A negative elapsed time means the clock and the lap origin
            # disagree; dropping the lap is better than drawing a car that
            # finished before the start.
            if elapsed >= 0:
                out.setdefault(abbr, {})[int(lap_no)] = elapsed
        if out:
            return out

    # Fallback: cumulative lap times.
    for abbr, d_laps in laps.groupby("Driver"):
        abbr = f1._clean(abbr)
        if abbr is None:
            continue
        running = 0
        for _, lp in d_laps.sort_values("LapNumber").iterrows():
            lap_no = lp.get("LapNumber")
            ms = f1._td_ms(lp.get("LapTime"))
            if ms is None or pd.isna(lap_no):
                continue
            running += ms
            out.setdefault(abbr, {})[int(lap_no)] = running
    return out


def _green_flag_reference_ms(laps: Any) -> Optional[int]:
    """Median green-flag lap time, the baseline a race trace is drawn against.

    Restricted to laps run wholly under green and marked accurate: including
    safety-car and pit laps would drag the median well off the field's true pace
    and tilt every line on the chart.
    """
    try:
        candidates: list[int] = []
        has_status = "TrackStatus" in laps
        has_accurate = "IsAccurate" in laps
        for _, lp in laps.iterrows():
            ms = f1._td_ms(lp.get("LapTime"))
            if ms is None or ms <= 0:
                continue
            if has_status:
                status = f1._clean(lp.get("TrackStatus"))
                if status is not None and str(status) != _ALL_CLEAR:
                    continue
            if has_accurate:
                accurate = lp.get("IsAccurate")
                if accurate is not None and not pd.isna(accurate) and not bool(accurate):
                    continue
            candidates.append(ms)

        if not candidates:
            # No green-flag laps identified (or no TrackStatus column at all):
            # fall back to the median of every timed lap rather than giving up,
            # since a slightly skewed baseline still produces a readable chart.
            candidates = [
                ms for ms in (f1._td_ms(v) for v in laps.get("LapTime", []))
                if ms is not None and ms > 0
            ]
        if not candidates:
            return None
        return int(np.median(candidates))
    except Exception as exc:  # noqa: BLE001
        log.warning("green-flag reference lap unavailable: %s", exc)
        return None


def _finish_order(session, laps: Any) -> list[str]:
    """Driver codes in finishing order, falling back to alphabetical.

    Same approach `get_strategy` uses, so the two screens list drivers in the
    same order for the same race.
    """
    try:
        present = set(laps["Driver"].dropna())
        order = [
            a for a in (f1._clean(x) for x in session.results["Abbreviation"])
            if a in present
        ]
        if order:
            return order
    except Exception as exc:  # noqa: BLE001
        log.warning("finish order unavailable: %s", exc)
    try:
        return sorted(set(laps["Driver"].dropna()))
    except Exception:  # noqa: BLE001
        return []


def _status_by_abbr(session) -> dict[str, dict[str, Any]]:
    """Retirement status per driver, using the same classification as
    `get_retirements`/`get_strategy` so "retired" means one thing app-wide."""
    out: dict[str, dict[str, Any]] = {}
    try:
        for _, r in session.results.iterrows():
            abbr = f1._clean(r.get("Abbreviation"))
            if not abbr:
                continue
            status = f1._clean(r.get("Status")) or ""
            classified = f1._clean(r.get("ClassifiedPosition"))
            out[abbr] = {
                "status": f1._retirement_display_status(status) if status else None,
                "retired": not f1._is_finish_status(status, classified),
                "finishPosition": _int_or_none(r.get("Position")),
            }
    except Exception as exc:  # noqa: BLE001
        log.warning("status lookup unavailable: %s", exc)
    return out


def _int_or_none(value: Any) -> Optional[int]:
    cleaned = f1._clean(value)
    if cleaned is None:
        return None
    try:
        return int(float(cleaned))
    except (TypeError, ValueError):
        return None


# --------------------------------------------------------------------------- #
#  Race trace
# --------------------------------------------------------------------------- #
def get_race_trace(year: int, rnd: int, mode: str = "median") -> dict[str, Any]:
    """Cumulative time delta per driver per lap — the race's whole story in one
    chart.

    Both modes return `deltaMs` where **a higher value is further ahead**:

    * `median` (default) — cumulative time de-trended by the race-wide median
      green-flag lap: ``lap_number × reference_lap - cumulative_time``.
    * `leader` — gap to whoever completed that lap first, so the leader sits on
      zero and everyone else is negative.

    The reference is shared across drivers, so the vertical distance between any two
    lines at a given lap *is* the gap between those cars in seconds.

    Undercuts, tyre cliffs, safety-car compression and the exact lap a race was
    lost are all directly readable off the result.
    """
    session = f1._load_session(year, rnd, "R", with_laps=True)
    normalised_mode = "leader" if str(mode).lower() == "leader" else "median"
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "session": "R",
        "eventName": f1._clean(session.event.get("EventName")) if session.event is not None else None,
        "available": False,
        "mode": normalised_mode,
        "totalLaps": 0,
        "greenFlagMedianLapMs": None,
        "yDomainMs": None,
        "periods": [],
        "drivers": [],
    }

    try:
        laps = session.laps
        if laps is None or not len(laps):
            return payload
    except Exception as exc:  # noqa: BLE001
        log.warning("race trace unavailable %s r%s: %s", year, rnd, exc)
        return payload

    elapsed = _elapsed_ms_by_driver(laps)
    if not elapsed:
        return payload

    total_laps = int(laps["LapNumber"].max()) if len(laps) else 0
    meta = f1._driver_meta(session)
    statuses = _status_by_abbr(session)

    green_flag_median_ms = _green_flag_reference_ms(laps)

    # Leader mode still needs a per-lap elapsed-time baseline.
    elapsed_by_lap: dict[int, list[int]] = {}
    for lap_map in elapsed.values():
        for lap_no, ms in lap_map.items():
            elapsed_by_lap.setdefault(lap_no, []).append(ms)

    baseline = {
        lap_no: min(values)
        for lap_no, values in elapsed_by_lap.items() if values
    }
    if normalised_mode == "median" and green_flag_median_ms is None:
        return payload

    # Per-lap compound, so a client can mark stints on the trace without also
    # fetching /api/strategy.
    compounds: dict[str, dict[int, Optional[str]]] = {}
    lap_times: dict[str, dict[int, Optional[int]]] = {}
    try:
        for _, lp in laps.iterrows():
            abbr = f1._clean(lp.get("Driver"))
            lap_no = lp.get("LapNumber")
            if abbr is None or pd.isna(lap_no):
                continue
            compounds.setdefault(abbr, {})[int(lap_no)] = f1._clean(lp.get("Compound"))
            lap_times.setdefault(abbr, {})[int(lap_no)] = f1._td_ms(lp.get("LapTime"))
    except Exception as exc:  # noqa: BLE001
        log.warning("race trace lap detail unavailable %s r%s: %s", year, rnd, exc)

    drivers: list[dict[str, Any]] = []
    all_deltas: list[int] = []
    for abbr in _finish_order(session, laps):
        lap_map = elapsed.get(abbr)
        if not lap_map:
            continue
        series: list[dict[str, Any]] = []
        for lap_no in sorted(lap_map):
            cumulative = lap_map[lap_no]
            if normalised_mode == "median":
                delta = int(round(lap_no * green_flag_median_ms - cumulative))
            else:
                reference = baseline.get(lap_no)
                if reference is None:
                    continue
                delta = int(round(reference - cumulative))
            all_deltas.append(delta)
            series.append({
                "lap": lap_no,
                "deltaMs": delta,
                "cumulativeMs": cumulative,
                "lapTimeMs": lap_times.get(abbr, {}).get(lap_no),
                "compound": compounds.get(abbr, {}).get(lap_no),
            })
        if not series:
            continue
        m = meta.get(abbr, {})
        st = statuses.get(abbr, {})
        drivers.append({
            "code": abbr,
            "driverId": m.get("driverId"),
            "fullName": m.get("fullName"),
            "teamName": m.get("teamName"),
            "teamColor": m.get("teamColor"),
            "finishPosition": st.get("finishPosition"),
            "retired": st.get("retired", False),
            "status": st.get("status"),
            "lapsCompleted": max(series, key=lambda s: s["lap"])["lap"],
            "laps": series,
        })

    if not drivers:
        return payload

    # Axis domain computed once here rather than three times on the clients.
    # Padded by 4% so the outermost line isn't drawn on the frame.
    low, high = min(all_deltas), max(all_deltas)
    span = max(high - low, 1)
    pad = int(span * 0.04)

    periods: list[dict[str, Any]] = []
    try:
        flags = f1.get_flags(year, rnd, "R")
        periods = flags.get("periods", []) or []
    except Exception as exc:  # noqa: BLE001
        # Best-effort, exactly as the Android lap-times screen treats it: no
        # bands is a worse chart, not a failed one.
        log.warning("race trace flag periods unavailable %s r%s: %s", year, rnd, exc)

    payload.update({
        "available": True,
        "totalLaps": total_laps,
        "greenFlagMedianLapMs": green_flag_median_ms,
        "yDomainMs": [low - pad, high + pad],
        "periods": periods,
        "drivers": drivers,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Tyre degradation
# --------------------------------------------------------------------------- #
def _usable_performance_lap(lap: Any) -> bool:
    """Whether a lap represents tyre pace rather than traffic control/pit loss."""
    ms = f1._td_ms(lap.get("LapTime"))
    if ms is None or ms <= 0:
        return False

    status = f1._clean(lap.get("TrackStatus"))
    if status is not None and str(status) != _ALL_CLEAR:
        return False

    accurate = lap.get("IsAccurate")
    if accurate is not None and not pd.isna(accurate) and not bool(accurate):
        return False

    deleted = lap.get("Deleted")
    if deleted is not None and not pd.isna(deleted) and bool(deleted):
        return False

    for column in ("PitInTime", "PitOutTime"):
        value = lap.get(column)
        if value is not None and not pd.isna(value):
            return False
    return True


def _stint_number(lap: Any, fallback: int) -> int:
    value = lap.get("Stint")
    if value is None or pd.isna(value):
        return fallback
    try:
        return max(1, int(float(value)))
    except (TypeError, ValueError):
        return fallback


def get_tyre_performance(year: int, rnd: int) -> dict[str, Any]:
    """Clean-lap tyre degradation by stint.

    Each point is a lap-time delta to that stint's best lap, plotted against
    tyre life. Safety-car/yellow, inaccurate, deleted and pit in/out laps are
    excluded before fitting. The returned fit and domains are ready for all
    three clients to render without repeating the derivation.
    """
    session = f1._load_session(year, rnd, "R", with_laps=True)
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "session": "R",
        "eventName": f1._clean(session.event.get("EventName")) if session.event is not None else None,
        "available": False,
        "xDomain": None,
        "yDomainMs": None,
        "compoundBaselines": [],
        "stints": [],
    }
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return payload
    except Exception as exc:  # noqa: BLE001
        log.warning("tyre performance unavailable %s r%s: %s", year, rnd, exc)
        return payload

    meta = f1._driver_meta(session)
    order = {code: index for index, code in enumerate(_finish_order(session, laps))}
    grouped: dict[tuple[str, int], list[dict[str, Any]]] = {}
    fallback_stints: dict[str, int] = {}
    last_compound: dict[str, Optional[str]] = {}

    for _, lap in laps.sort_values(["Driver", "LapNumber"]).iterrows():
        code = f1._clean(lap.get("Driver"))
        lap_number = _int_or_none(lap.get("LapNumber"))
        tyre_life = f1._clean(lap.get("TyreLife"))
        compound = f1._clean(lap.get("Compound"))
        if not code or lap_number is None or tyre_life is None or not _usable_performance_lap(lap):
            continue
        try:
            tyre_life_value = float(tyre_life)
        except (TypeError, ValueError):
            continue
        if tyre_life_value < 0:
            continue

        fallback = fallback_stints.get(code, 1)
        fresh = lap.get("FreshTyre")
        compound_changed = code in last_compound and compound != last_compound[code]
        starts_fresh_set = (
            fresh is not None and not pd.isna(fresh) and bool(fresh)
            and grouped.get((code, fallback))
        )
        if compound_changed or starts_fresh_set:
            fallback += 1
        fallback_stints[code] = fallback
        last_compound[code] = compound
        stint = _stint_number(lap, fallback)
        grouped.setdefault((code, stint), []).append({
            "lap": lap_number,
            "tyreLife": tyre_life_value,
            "lapTimeMs": f1._td_ms(lap.get("LapTime")),
            "compound": compound,
            "freshTyre": None if fresh is None or pd.isna(fresh) else bool(fresh),
        })

    stints: list[dict[str, Any]] = []
    all_x: list[float] = []
    all_y: list[int] = []
    slopes_by_compound: dict[str, list[float]] = {}
    for (code, stint_number), rows in grouped.items():
        rows = [row for row in rows if row["lapTimeMs"] is not None]
        if len(rows) < 2:
            continue
        rows.sort(key=lambda row: (row["tyreLife"], row["lap"]))
        best = min(row["lapTimeMs"] for row in rows)
        points = [
            {
                "lap": row["lap"],
                "tyreLife": round(row["tyreLife"], 2),
                "lapTimeMs": row["lapTimeMs"],
                "deltaMs": int(row["lapTimeMs"] - best),
            }
            for row in rows
        ]
        x = np.array([point["tyreLife"] for point in points], dtype=float)
        y_seconds = np.array([point["deltaMs"] / 1000 for point in points], dtype=float)
        slope, intercept = np.polyfit(x, y_seconds, 1)
        compound = next((row["compound"] for row in rows if row["compound"]), None)
        if compound:
            slopes_by_compound.setdefault(compound, []).append(float(slope))
        minimum_x, maximum_x = float(x.min()), float(x.max())
        fit = [
            {"tyreLife": round(minimum_x, 2), "deltaMs": int(round((slope * minimum_x + intercept) * 1000))},
            {"tyreLife": round(maximum_x, 2), "deltaMs": int(round((slope * maximum_x + intercept) * 1000))},
        ]
        all_x.extend(x.tolist())
        all_y.extend(point["deltaMs"] for point in points)
        m = meta.get(code, {})
        stints.append({
            "id": f"{code}-{stint_number}",
            "driverCode": code,
            "driverId": m.get("driverId"),
            "fullName": m.get("fullName"),
            "teamName": m.get("teamName"),
            "teamColor": m.get("teamColor"),
            "stint": stint_number,
            "compound": compound,
            "freshTyre": rows[0]["freshTyre"],
            "startLap": min(row["lap"] for row in rows),
            "endLap": max(row["lap"] for row in rows),
            "bestLapMs": best,
            "slopeSecPerLap": round(float(slope), 3),
            "points": points,
            "fit": fit,
        })

    if not stints:
        return payload

    stints.sort(key=lambda item: (order.get(item["driverCode"], 999), item["stint"]))
    compound_baselines = [
        {
            "compound": compound,
            "slopeSecPerLap": round(float(np.median(slopes)), 3),
            "stintCount": len(slopes),
        }
        for compound, slopes in sorted(slopes_by_compound.items())
    ]
    y_max = max(all_y) if all_y else 0
    y_pad = max(100, int(y_max * 0.05))
    payload.update({
        "available": True,
        "xDomain": [max(0, int(np.floor(min(all_x)))), int(np.ceil(max(all_x)))],
        "yDomainMs": [0, y_max + y_pad],
        "compoundBaselines": compound_baselines,
        "stints": stints,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Pit-stop / undercut ledger
# --------------------------------------------------------------------------- #
def get_pit_stops(year: int, rnd: int) -> dict[str, Any]:
    """Real pit-lane transit loss and the positional outcome of every stop."""
    session = f1._load_session(year, rnd, "R", with_laps=True)
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "session": "R",
        "eventName": f1._clean(session.event.get("EventName")) if session.event is not None else None,
        "available": False,
        "circuitMedianLossMs": None,
        "lossDomainMs": None,
        "stops": [],
    }
    try:
        laps = session.laps
        if laps is None or not len(laps):
            return payload
    except Exception as exc:  # noqa: BLE001
        log.warning("pit stops unavailable %s r%s: %s", year, rnd, exc)
        return payload

    meta = f1._driver_meta(session)
    order = {code: index for index, code in enumerate(_finish_order(session, laps))}
    stops: list[dict[str, Any]] = []
    for raw_code, driver_laps in laps.groupby("Driver"):
        code = f1._clean(raw_code)
        if not code:
            continue
        rows = list(driver_laps.sort_values("LapNumber").iterrows())
        stop_number = 0
        for index, (_, lap) in enumerate(rows):
            pit_in = f1._td_ms(lap.get("PitInTime"))
            if pit_in is None:
                continue
            out_row = None
            pit_out = None
            for _, candidate in rows[index:index + 3]:
                candidate_out = f1._td_ms(candidate.get("PitOutTime"))
                if candidate_out is not None:
                    out_row, pit_out = candidate, candidate_out
                    break
            if out_row is None or pit_out is None or pit_out <= pit_in:
                continue
            stop_number += 1
            lap_no = _int_or_none(lap.get("LapNumber")) or 0
            entry_position = _int_or_none(lap.get("Position"))
            rejoin_position = _int_or_none(out_row.get("Position"))
            gained = (
                entry_position - rejoin_position
                if entry_position is not None and rejoin_position is not None
                else None
            )
            outcome = "HELD"
            if gained is not None and gained > 0:
                outcome = "UNDERCUT"
            elif gained is not None and gained < 0:
                outcome = "OVERCUT"

            rivals: list[str] = []
            if entry_position is not None and rejoin_position is not None and gained:
                low, high = sorted((entry_position, rejoin_position))
                try:
                    frame = laps[laps["LapNumber"] == lap_no + 1]
                    rivals = [
                        f1._clean(row.get("Driver"))
                        for _, row in frame.iterrows()
                        if low <= (_int_or_none(row.get("Position")) or 999) <= high
                        and f1._clean(row.get("Driver")) != code
                    ]
                    rivals = [rival for rival in rivals if rival]
                except Exception:  # noqa: BLE001
                    rivals = []

            m = meta.get(code, {})
            stops.append({
                "id": f"{code}-{stop_number}",
                "driverCode": code,
                "driverId": m.get("driverId"),
                "fullName": m.get("fullName"),
                "teamName": m.get("teamName"),
                "teamColor": m.get("teamColor"),
                "stop": stop_number,
                "lap": lap_no,
                "compoundIn": f1._clean(lap.get("Compound")),
                "compoundOut": f1._clean(out_row.get("Compound")),
                "lossMs": int(pit_out - pit_in),
                "entryPosition": entry_position,
                "rejoinPosition": rejoin_position,
                "positionsGained": gained,
                "outcome": outcome,
                "rivals": rivals,
            })

    if not stops:
        return payload
    stops.sort(key=lambda item: (item["lap"], order.get(item["driverCode"], 999), item["stop"]))
    losses = [stop["lossMs"] for stop in stops]
    median = int(round(float(np.median(losses))))
    for stop in stops:
        stop["deltaToMedianMs"] = stop["lossMs"] - median
    payload.update({
        "available": True,
        "circuitMedianLossMs": median,
        "lossDomainMs": [0, max(losses) + max(500, int(max(losses) * 0.05))],
        "stops": stops,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Qualifying sector waterfall
# --------------------------------------------------------------------------- #
def _valid_qualifying_laps(laps: Any) -> Any:
    """Timed, non-deleted qualifying laps with all three sector splits."""
    required = ["LapTime", "Sector1Time", "Sector2Time", "Sector3Time"]
    if any(column not in laps for column in required):
        return laps.iloc[0:0]
    mask = laps["LapTime"].notna()
    for column in required[1:]:
        mask &= laps[column].notna()
    if "Deleted" in laps:
        mask &= ~laps["Deleted"].fillna(False).astype(bool)
    return laps[mask]


def get_qualifying_sectors(year: int, rnd: int) -> dict[str, Any]:
    """Gap-to-pole decomposed into sector contributions plus ideal laps."""
    session = f1._load_session(year, rnd, "Q", with_laps=True)
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "session": "Q",
        "eventName": f1._clean(session.event.get("EventName")) if session.event is not None else None,
        "available": False,
        "poleCode": None,
        "poleLapMs": None,
        "gapDomainMs": None,
        "drivers": [],
    }
    try:
        laps = _valid_qualifying_laps(session.laps)
        if laps is None or not len(laps):
            return payload
    except Exception as exc:  # noqa: BLE001
        log.warning("qualifying sectors unavailable %s r%s: %s", year, rnd, exc)
        return payload

    timed = laps.copy()
    timed["_lap_ms"] = timed["LapTime"].map(f1._td_ms)
    timed = timed[timed["_lap_ms"].notna()]
    if not len(timed):
        return payload
    pole = timed.loc[timed["_lap_ms"].idxmin()]
    pole_code = f1._clean(pole.get("Driver"))
    pole_lap_ms = int(pole["_lap_ms"])
    pole_sectors = [f1._td_ms(pole.get(f"Sector{i}Time")) for i in range(1, 4)]
    if any(value is None for value in pole_sectors):
        return payload

    meta = f1._driver_meta(session)
    drivers: list[dict[str, Any]] = []
    all_segments: list[int] = []
    for raw_code, driver_laps in timed.groupby("Driver"):
        code = f1._clean(raw_code)
        if not code:
            continue
        fastest = driver_laps.loc[driver_laps["_lap_ms"].idxmin()]
        sectors = [f1._td_ms(fastest.get(f"Sector{i}Time")) for i in range(1, 4)]
        if any(value is None for value in sectors):
            continue
        segment_ms = [int(sectors[i] - pole_sectors[i]) for i in range(3)]
        ideal_sectors = [
            min(
                value for value in (
                    f1._td_ms(row.get(f"Sector{i}Time")) for _, row in driver_laps.iterrows()
                ) if value is not None
            )
            for i in range(1, 4)
        ]
        ideal_ms = sum(ideal_sectors)
        lap_ms = int(fastest["_lap_ms"])
        all_segments.extend(segment_ms)
        m = meta.get(code, {})
        drivers.append({
            "code": code,
            "driverId": m.get("driverId"),
            "fullName": m.get("fullName"),
            "teamName": m.get("teamName"),
            "teamColor": m.get("teamColor"),
            "lapMs": lap_ms,
            "gapToPoleMs": lap_ms - pole_lap_ms,
            "sectorMs": [int(value) for value in sectors],
            "sectorDeltaMs": segment_ms,
            "idealSectorMs": ideal_sectors,
            "idealLapMs": ideal_ms,
            "idealGainMs": lap_ms - ideal_ms,
            "speedI1": f1._clean(fastest.get("SpeedI1")),
            "speedI2": f1._clean(fastest.get("SpeedI2")),
            "speedFL": f1._clean(fastest.get("SpeedFL")),
            "speedST": f1._clean(fastest.get("SpeedST")),
        })

    if not drivers:
        return payload
    drivers.sort(key=lambda driver: driver["lapMs"])
    low = min(all_segments + [0])
    high = max([driver["gapToPoleMs"] for driver in drivers] + [0])
    pad = max(50, int(max(abs(low), abs(high), 1) * 0.05))
    payload.update({
        "available": True,
        "poleCode": pole_code,
        "poleLapMs": pole_lap_ms,
        "gapDomainMs": [low - pad, high + pad],
        "drivers": drivers,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Mini-sector dominance
# --------------------------------------------------------------------------- #
def get_minisectors(
    year: int,
    rnd: int,
    session_name: str = "Q",
    top: int = 10,
    bins: int = 24,
) -> dict[str, Any]:
    """Fastest driver through each distance bin on a shared curved outline."""
    ceiling = max(1, int(os.environ.get("MINISECTOR_MAX_DRIVERS", "10")))
    driver_limit = max(1, min(int(top), ceiling))
    bin_count = max(6, min(int(bins), 60))
    identifier = str(session_name or "Q").upper()
    session = f1._load_session(
        year, rnd, identifier, with_laps=True, with_telemetry=True,
    )
    payload: dict[str, Any] = {
        "year": year,
        "round": rnd,
        "session": identifier,
        "eventName": f1._clean(session.event.get("EventName")) if session.event is not None else None,
        "available": False,
        "driverCount": 0,
        "segmentCount": bin_count,
        "legend": [],
        "segments": [],
    }
    try:
        codes = [
            code for code in (
                f1._clean(value) for value in session.results["Abbreviation"]
            ) if code
        ][:driver_limit]
    except Exception:  # noqa: BLE001
        try:
            codes = [
                code for code in (
                    f1._clean(value) for value in session.laps["Driver"].dropna().unique()
                ) if code
            ][:driver_limit]
        except Exception:  # noqa: BLE001
            return payload

    traces: dict[str, dict[str, Any]] = {}
    for code in codes:
        try:
            trace = f1._lap_telemetry(session, code, "fastest")
            if trace and len(trace.get("distance", [])) > 2 and len(trace.get("time", [])) > 2:
                traces[code] = trace
        except Exception as exc:  # noqa: BLE001
            log.warning("mini-sector telemetry unavailable for %s: %s", code, exc)
    if not traces:
        return payload

    master_code, master = min(
        traces.items(),
        key=lambda item: item[1].get("lapTimeMs") or 10**9,
    )
    maximum_distance = min(
        float(trace["distance"][-1]) for trace in traces.values()
        if trace.get("distance")
    )
    edges = np.linspace(0.0, maximum_distance, bin_count + 1)
    meta = f1._driver_meta(session)
    segments: list[dict[str, Any]] = []
    for index in range(bin_count):
        start, end = float(edges[index]), float(edges[index + 1])
        durations: list[tuple[str, float]] = []
        for code, trace in traces.items():
            distances = np.asarray(trace["distance"], dtype=float)
            times = np.asarray(trace["time"], dtype=float)
            if len(distances) != len(times):
                continue
            duration = float(np.interp(end, distances, times) - np.interp(start, distances, times))
            if duration > 0:
                durations.append((code, duration))
        if not durations:
            continue
        durations.sort(key=lambda item: item[1])
        winner, winner_time = durations[0]
        runner_up = durations[1][1] if len(durations) > 1 else winner_time

        master_distance = np.asarray(master["distance"], dtype=float)
        master_x = np.asarray(master["x"], dtype=float)
        master_y = np.asarray(master["y"], dtype=float)
        sample_distances = np.linspace(start, end, 15)
        points = [
            [
                round(float(np.interp(distance, master_distance, master_x)), 2),
                round(float(np.interp(distance, master_distance, master_y)), 2),
            ]
            for distance in sample_distances
        ]
        segments.append({
            "index": index,
            "startDistance": round(start, 1),
            "endDistance": round(end, 1),
            "points": points,
            "winnerCode": winner,
            "teamColor": meta.get(winner, {}).get("teamColor"),
            "timeMs": int(round(winner_time * 1000)),
            "gapMs": int(round((runner_up - winner_time) * 1000)),
        })

    if not segments:
        return payload
    winners = {segment["winnerCode"] for segment in segments}
    payload.update({
        "available": True,
        "driverCount": len(traces),
        "segmentCount": len(segments),
        "legend": [
            {
                "code": code,
                "teamColor": meta.get(code, {}).get("teamColor"),
                "segmentsWon": sum(segment["winnerCode"] == code for segment in segments),
            }
            for code in codes if code in winners
        ],
        "segments": segments,
        "outlineSourceCode": master_code,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Title permutation matrix
# --------------------------------------------------------------------------- #
_RACE_POINTS = {1: 25, 2: 18, 3: 15, 4: 12, 5: 10, 6: 8, 7: 6, 8: 4, 9: 2, 10: 1}


def get_title_scenarios(
    year: int,
    d1: Optional[str] = None,
    d2: Optional[str] = None,
    through_round: Optional[int] = None,
) -> dict[str, Any]:
    """Next-race finish permutations for two title contenders."""
    calculator = f1.get_wdc_calculator(year, through_round=through_round)
    contenders = calculator.get("drivers", [])
    by_id = {driver.get("driverId"): driver for driver in contenders if driver.get("driverId")}
    first = by_id.get(d1) if d1 else (contenders[0] if contenders else None)
    second = by_id.get(d2) if d2 else (contenders[1] if len(contenders) > 1 else None)
    payload: dict[str, Any] = {
        "year": year,
        "throughRound": calculator.get("throughRound"),
        "available": False,
        "roundsRemaining": calculator.get("roundsRemaining", 0),
        "positions": list(range(1, 11)) + [0],
        "drivers": [],
        "cells": [],
        "clinchText": None,
        "summary": None,
    }
    if not first or not second or int(calculator.get("roundsRemaining", 0)) <= 0:
        return payload

    positions = payload["positions"]
    remaining_after = max(int(calculator.get("roundsRemaining", 0)) - 1, 0)
    maximum_after = remaining_after * 25
    cells: list[dict[str, Any]] = []
    for p1 in positions:
        for p2 in positions:
            points1 = float(first.get("points", 0)) + _RACE_POINTS.get(p1, 0)
            points2 = float(second.get("points", 0)) + _RACE_POINTS.get(p2, 0)
            margin = round(points1 - points2, 1)
            if margin > maximum_after:
                outcome = "D1_CLINCHED"
            elif margin < -maximum_after:
                outcome = "D2_CLINCHED"
            elif margin > 0:
                outcome = "D1_LEADS"
            elif margin < 0:
                outcome = "D2_LEADS"
            else:
                outcome = "TIED"
            cells.append({
                "d1Position": p1,
                "d2Position": p2,
                "d1Points": points1,
                "d2Points": points2,
                "margin": margin,
                "outcome": outcome,
            })

    d1_code = first.get("driverCode") or first.get("driverId")
    clinching = [
        cell for cell in cells
        if cell["outcome"] == "D1_CLINCHED" and cell["d2Position"] == 2
    ]
    clinch_text = None
    if clinching:
        scoring_positions = [cell["d1Position"] for cell in clinching if cell["d1Position"] > 0]
        if scoring_positions:
            best_required = max(scoring_positions)
            clinch_text = f"{d1_code} clinches with P{best_required} or better if the rival finishes P2."
    outcomes = {cell["outcome"] for cell in cells}
    summary = None
    if len(outcomes) == 1:
        only_outcome = next(iter(outcomes))
        second_code = second.get("driverCode") or second.get("driverId")
        summary = {
            "D1_CLINCHED": f"{d1_code} clinches the title in every combination shown.",
            "D2_CLINCHED": f"{second_code} clinches the title in every combination shown.",
            "D1_LEADS": f"{d1_code} remains championship leader in every combination shown.",
            "D2_LEADS": f"{second_code} remains championship leader in every combination shown.",
            "TIED": "Every combination shown leaves the drivers tied.",
        }.get(only_outcome)
    payload.update({
        "available": True,
        "drivers": [
            {
                "driverId": first.get("driverId"),
                "code": d1_code,
                "teamColor": first.get("teamColor"),
                "points": first.get("points", 0),
            },
            {
                "driverId": second.get("driverId"),
                "code": second.get("driverCode") or second.get("driverId"),
                "teamColor": second.get("teamColor"),
                "points": second.get("points", 0),
            },
        ],
        "cells": cells,
        "clinchText": clinch_text,
        "summary": summary,
    })
    return payload


# --------------------------------------------------------------------------- #
#  Driver season fingerprint
# --------------------------------------------------------------------------- #
def _percentile(value: float, population: list[float], higher_is_better: bool = True) -> int:
    if not population:
        return 50
    values = sorted(population)
    rank = sum(item <= value for item in values) / len(values)
    score = rank if higher_is_better else 1 - rank + (1 / len(values))
    return int(round(max(0.0, min(1.0, score)) * 100))


def _evenly_spaced_rounds(rounds: list[int], limit: int) -> list[int]:
    """Select a season-wide sample without biasing the fingerprint to one era."""
    unique = sorted(set(rounds))
    if len(unique) <= limit:
        return unique
    indexes = np.linspace(0, len(unique) - 1, num=limit)
    return list(dict.fromkeys(unique[int(round(index))] for index in indexes))


def get_driver_fingerprint(year: int, driver_id: str) -> dict[str, Any]:
    """Six chart-ready season percentiles from shared race/qualifying results."""
    race_response = f1._ergast.get_race_results(season=year, limit=100)
    race_contents, race_description = f1._collect_multi(race_response)
    qualifying_response = f1._ergast.get_qualifying_results(season=year, limit=100)
    qualifying_contents, _ = f1._collect_multi(qualifying_response)
    drivers = {driver.get("driverId"): driver for driver in f1.get_drivers(year)}
    payload: dict[str, Any] = {
        "year": year,
        "driverId": driver_id,
        "available": False,
        "driver": drivers.get(driver_id),
        "axes": [],
    }

    metrics: dict[str, dict[str, list[float]]] = {}
    completed_rounds: list[int] = []
    wet_rounds: set[int] = set()
    for index, race in enumerate(race_contents):
        if race is None or race.empty or "driverId" not in race:
            continue
        round_number = None
        if index < len(race_description) and "round" in race_description:
            round_number = _int_or_none(race_description.iloc[index].get("round"))
        if round_number is not None:
            completed_rounds.append(round_number)
            try:
                if f1.get_weather(year, round_number, "R").get("rainfall"):
                    wet_rounds.add(round_number)
            except Exception as exc:  # noqa: BLE001
                log.warning("fingerprint weather unavailable %s r%s: %s", year, round_number, exc)
        for _, row in race.iterrows():
            did = f1._clean(row.get("driverId"))
            if not did:
                continue
            entry = metrics.setdefault(
                did,
                {"finish": [], "points": [], "starts": [], "consistency": [], "wetPoints": [], "tyre": []},
            )
            position = _int_or_none(row.get("position"))
            grid = _int_or_none(row.get("grid"))
            points = f1._clean(row.get("points"))
            if position:
                entry["finish"].append(float(position))
                entry["consistency"].append(float(position))
            if position and grid is not None:
                entry["starts"].append(float(grid - position))
            if points is not None:
                entry["points"].append(float(points))
                if round_number in wet_rounds:
                    entry["wetPoints"].append(float(points))

    # V3 is the authoritative tyre-degradation derivation. Reusing it here
    # keeps the season radar consistent with each race's detailed chart.
    # Loading every race's full lap table makes a cold season request exceed
    # common reverse-proxy timeouts, so use an evenly spaced season sample.
    # The limit is configurable for larger deployments and the chosen rounds
    # are returned for observability.
    tyre_round_limit = max(1, int(os.environ.get("FINGERPRINT_TYRE_ROUNDS", "6")))
    tyre_rounds = _evenly_spaced_rounds(completed_rounds, tyre_round_limit)
    for round_number in tyre_rounds:
        try:
            tyre = get_tyre_performance(year, round_number)
            for stint in tyre.get("stints", []):
                code = stint.get("driverCode")
                driver_match = next(
                    (
                        did for did, driver in drivers.items()
                        if driver.get("code") == code
                    ),
                    None,
                )
                if driver_match in metrics:
                    metrics[driver_match]["tyre"].append(float(stint.get("slopeSecPerLap", 0)))
        except Exception as exc:  # noqa: BLE001
            log.warning("fingerprint tyre data unavailable %s r%s: %s", year, round_number, exc)

    qualifying_positions: dict[str, list[float]] = {}
    for qualifying in qualifying_contents:
        if qualifying is None or qualifying.empty or "driverId" not in qualifying:
            continue
        for _, row in qualifying.iterrows():
            did = f1._clean(row.get("driverId"))
            position = _int_or_none(row.get("position"))
            if did and position:
                qualifying_positions.setdefault(did, []).append(float(position))

    reliability = {
        item.get("driverId"): float(item.get("finishRate", 0))
        for item in f1.get_reliability(year).get("drivers", [])
    }
    raw: dict[str, dict[str, float]] = {}
    for did, metric in metrics.items():
        finishes = metric["finish"]
        points = metric["points"]
        consistency = metric["consistency"]
        raw[did] = {
            "qualifyingPace": float(np.mean(qualifying_positions.get(did, [20]))),
            "racePace": float(np.mean(finishes or [20])),
            "tyreManagement": float(np.mean(metric["tyre"] or [np.std(consistency or [20])])),
            "startPerformance": float(np.mean(metric["starts"] or [0])),
            "reliability": reliability.get(did, 0.0),
            "wetWeatherPace": float(np.mean(metric["wetPoints"] or points or [0])),
        }
    if driver_id not in raw:
        return payload

    definitions = [
        ("qualifyingPace", "Qualifying pace", False),
        ("racePace", "Race pace", False),
        ("tyreManagement", "Tyre management", False),
        ("startPerformance", "Starts", True),
        ("reliability", "Reliability", True),
        ("wetWeatherPace", "Wet-weather pace", True),
    ]
    axes = []
    for key, label, higher in definitions:
        value = raw[driver_id][key]
        axes.append({
            "key": key,
            "label": label,
            "percentile": _percentile(value, [item[key] for item in raw.values()], higher),
            "rawValue": round(value, 2),
        })
    payload.update({
        "available": True,
        "axes": axes,
        "tyreRoundsSampled": tyre_rounds,
    })
    return payload
