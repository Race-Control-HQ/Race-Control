"""Unauthenticated historical OpenF1 adapter for the shared FastF1 session layer.

Only missing feeds are replaced. Pandas/FastF1-compatible laps let existing
serializers and analytics consume the fallback without changing public routes.
Telemetry is queried for one driver/time window, never for the whole field.
"""
from __future__ import annotations

import logging
import os
import re
import threading
import time
from collections import OrderedDict
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import requests
from fastf1.core import Lap, Laps
from fastf1.exceptions import DataNotLoadedError

log = logging.getLogger("racecontrol")
_BASE_URL = "https://api.openf1.org/v1"
_TTL = 6 * 60 * 60
_MAX_CACHE = 128
_cache: OrderedDict = OrderedDict()
_failures: OrderedDict = OrderedDict()
_lock = threading.RLock()
_last_request = 0.0


class OpenF1Unavailable(RuntimeError):
    pass


def _get(endpoint: str, **params) -> list[dict]:
    """Bounded, shared cache and single-flight requests; no credentials required.

    Brief failure backoff prevents a circuit outline's candidate laps or
    concurrent requests from hammering a failed/rate-limited upstream.
    """
    global _last_request
    key = (endpoint, tuple(sorted(params.items())))
    failure_key = (endpoint, params.get("session_key"))
    with _lock:
        now = time.monotonic()
        hit = _cache.get(key)
        if hit and now - hit[0] < _TTL:
            _cache.move_to_end(key)
            return hit[1]
        _cache.pop(key, None)
        if _failures.get(failure_key, 0) > now:
            raise OpenF1Unavailable(f"{endpoint}: temporarily unavailable")
        # Keep requests below three per second even on a cold cache.
        delay = 0.4 - (now - _last_request)
        if delay > 0:
            time.sleep(delay)
        _last_request = time.monotonic()
        response = None
        try:
            response = requests.get(f"{_BASE_URL}/{endpoint}", params=params,
                                    timeout=(3.05, 15))
            response.raise_for_status()
            rows = response.json()
            if not isinstance(rows, list) or any(not isinstance(r, dict) for r in rows):
                raise ValueError("expected an array of objects")
        except (requests.RequestException, ValueError) as exc:
            # No subscription/login fallback. 401/403/429/5xx remain unavailable.
            backoff = 30
            if response is not None and response.status_code == 429:
                try:
                    backoff = max(backoff, min(3600, float(response.headers.get("Retry-After", 30))))
                except ValueError:
                    pass
            _failures[failure_key] = time.monotonic() + backoff
            _failures.move_to_end(failure_key)
            while len(_failures) > _MAX_CACHE:
                _failures.popitem(last=False)
            raise OpenF1Unavailable(f"{endpoint}: {exc}") from exc
        # Empty data can be a publication delay; leave it retryable.
        if rows:
            _cache[key] = (time.monotonic(), rows)
            while len(_cache) > _MAX_CACHE:
                _cache.popitem(last=False)
        return rows


def _optional(endpoint: str, **params) -> list[dict] | None:
    try:
        return _get(endpoint, **params)
    except OpenF1Unavailable as exc:
        log.warning("OpenF1 %s", exc)
        return None


def _read(session, name):
    try:
        return getattr(session, name)
    except (AttributeError, DataNotLoadedError):
        return None


def _missing(session, name, allow_empty=False):
    data = _read(session, name)
    return data is None or (not allow_empty and not len(data))


_SESSION_NAMES = {
    "R": {"Race"}, "Q": {"Qualifying"}, "S": {"Sprint"},
    "SQ": {"Sprint Qualifying", "Sprint Shootout"},
    "SS": {"Sprint Qualifying", "Sprint Shootout"},
    "FP1": {"Practice 1"}, "FP2": {"Practice 2"}, "FP3": {"Practice 3"},
}


def _scheduled_start(event, identifier):
    try:
        return pd.to_datetime(event.get_session_date(identifier, utc=True), utc=True)
    except (AttributeError, KeyError, ValueError):
        for i in range(1, 6):
            if event.get(f"Session{i}") in _SESSION_NAMES.get(identifier, set()):
                return pd.to_datetime(event.get(f"Session{i}DateUtc"), utc=True)
    return pd.NaT


def _resolve_session(year, identifier, event):
    scheduled = _scheduled_start(event, identifier)
    if pd.isna(scheduled) or identifier not in _SESSION_NAMES:
        return None
    # The annual session list is small and reusable across a weekend. Match
    # locally, avoiding date-filter inconsistencies in the upstream API.
    rows = _get("sessions", year=year)
    now = pd.Timestamp(datetime.now(timezone.utc))
    matches = []
    for row in rows:
        start = pd.to_datetime(row.get("date_start"), utc=True)
        end = pd.to_datetime(row.get("date_end"), utc=True)
        if (row.get("session_name") in _SESSION_NAMES[identifier]
                and not row.get("is_cancelled")
                and not pd.isna(start) and not pd.isna(end) and end < now
                and abs(start - scheduled) < pd.Timedelta(hours=12)):
            matches.append(row)
    # Never guess a round -> session key, or accidentally use another race.
    return matches[0] if len(matches) == 1 else None


def _number(value):
    return str(int(value)) if value is not None and not pd.isna(value) else None


def _td(value):
    return pd.to_timedelta(value, unit="s") if value is not None else pd.NaT


def _utc_iso(value):
    """OpenF1 timestamps are explicitly UTC, including native naive UTC dates."""
    return pd.to_datetime(value, utc=True).isoformat().replace("+00:00", "Z")


def _results(base, drivers):
    current = _read(base, "results")
    rows = current.to_dict("records") if current is not None else []
    by_number = {_number(row.get("DriverNumber")): row for row in rows}
    for driver in drivers:
        number = _number(driver.get("driver_number"))
        if not number:
            continue
        row = by_number.get(number)
        if row is None:
            row = {"DriverNumber": number}
            rows.append(row)
            by_number[number] = row
        for field, source in (("Abbreviation", "name_acronym"), ("FullName", "full_name"),
                              ("TeamName", "team_name"), ("TeamColor", "team_colour"),
                              ("FirstName", "first_name"), ("LastName", "last_name")):
            if row.get(field) is None or pd.isna(row.get(field)) or row.get(field) == "":
                row[field] = driver.get(source)
        row["DriverNumber"] = number
    frame = pd.DataFrame(rows)
    # Supply absent optional result fields, without inventing a classification
    # or stable Jolpica driver/team identifiers from OpenF1 display names.
    for field in ("Abbreviation", "DriverNumber", "DriverId", "TeamId", "TeamName",
                  "TeamColor", "FullName", "Position", "ClassifiedPosition", "Status",
                  "GridPosition", "Points", "Time", "Q1", "Q2", "Q3"):
        if field not in frame:
            frame[field] = None
    return frame


class OpenF1Lap(Lap):
    def get_telemetry(self, **kwargs):
        return self.session.telemetry_for_lap(self)

    def get_pos_data(self, **kwargs):
        return self.session.positions_for_laps(self)


class OpenF1Laps(Laps):
    @property
    def _constructor_sliced_horizontal(self):
        return OpenF1Lap

    def get_pos_data(self, **kwargs):
        return self.session.positions_for_laps(self)


class OpenF1Session:
    """Overlay missing feeds; leave original session metadata/results usable."""
    def __init__(self, base, info, drivers):
        self._base = base
        self.info = info
        self.session_key = info["session_key"]
        self.t0_date = pd.to_datetime(info["date_start"], utc=True).tz_localize(None)
        self.session_start_time = pd.Timedelta(0)
        self.results = _results(base, drivers)
        self._feeds = {}
        self.expires_at = time.monotonic() + _TTL
        self._results_restored = False

    def __getattr__(self, name):
        if name in self._feeds:
            return self._feeds[name]
        return getattr(self._base, name)

    def get_driver(self, code):
        rows = self.results[(self.results["Abbreviation"] == code)
                            | (self.results["DriverNumber"] == str(code))]
        return rows.iloc[0] if len(rows) else None

    def get_circuit_info(self):
        # FastF1's corner-distance enrichment can read its missing native laps.
        # Do not mix corner geometry from a different telemetry coordinate space.
        raise DataNotLoadedError("OpenF1 does not provide circuit corner metadata")

    def _timed_frame(self, rows, fields):
        frame = pd.DataFrame(rows)
        if frame.empty:
            return frame
        frame["Date"] = pd.to_datetime(frame["date"], utc=True, format="ISO8601").dt.tz_localize(None)
        frame = frame.dropna(subset=["Date"]).drop_duplicates("Date").sort_values("Date")
        frame = frame.rename(columns=fields)
        frame["SessionTime"] = frame["Date"] - self.t0_date
        return frame

    def _lap_bounds(self, laps):
        frame = laps if isinstance(laps, pd.DataFrame) else laps.to_frame().T
        starts = frame["LapStartDate"].dropna()
        ends = frame["Time"].dropna()
        if starts.empty or ends.empty:
            raise DataNotLoadedError("OpenF1 lap bounds unavailable")
        return starts.min(), self.t0_date + ends.max()

    def positions_for_laps(self, laps):
        start, end = self._lap_bounds(laps)
        number = (laps["DriverNumber"].iloc[0] if isinstance(laps, pd.DataFrame)
                  else laps["DriverNumber"])
        # Replay needs one bounded request per driver. A single lap uses the
        # same mechanism with a much smaller date range.
        rows = _get("location", session_key=self.session_key, driver_number=int(number),
                    **{"date>": _utc_iso(start), "date<": _utc_iso(end)})
        frame = self._timed_frame(rows, {"x": "X", "y": "Y", "z": "Z"})
        if frame.empty or any(c not in frame for c in ("X", "Y")):
            raise DataNotLoadedError("OpenF1 location unavailable")
        return frame

    def telemetry_for_lap(self, lap):
        start, end = self._lap_bounds(lap)
        try:
            rows = _get("car_data", session_key=self.session_key,
                        driver_number=int(lap["DriverNumber"]),
                        **{"date>": _utc_iso(start), "date<": _utc_iso(end)})
            car = self._timed_frame(rows, {"speed": "Speed", "throttle": "Throttle",
                                         "brake": "Brake", "n_gear": "nGear",
                                         "rpm": "RPM", "drs": "DRS"})
            pos = self.positions_for_laps(lap)
        except OpenF1Unavailable as exc:
            log.warning("OpenF1 telemetry unavailable session %s driver %s: %s",
                        self.session_key, lap["DriverNumber"], exc)
            raise DataNotLoadedError(str(exc)) from exc
        # DRS is absent in 2026 data. Keep otherwise usable telemetry, with
        # a neutral compatibility value and an explicit availability flag.
        channels = ("Speed", "Throttle", "Brake", "nGear", "RPM")
        if car.empty or any(c not in car for c in channels):
            raise DataNotLoadedError("OpenF1 car data unavailable")
        car = car.dropna(subset=list(channels)).copy()
        drs_available = "DRS" in car and car["DRS"].notna().any()
        car["DRS"] = car["DRS"].fillna(0) if "DRS" in car else 0
        pos = pos.dropna(subset=["X", "Y"])
        if len(car) < 2 or len(pos) < 2:
            raise DataNotLoadedError("OpenF1 telemetry has insufficient samples")
        # Align asynchronous position samples to car samples. Do not extend
        # the trace beyond the time range covered by both feeds.
        car = car[(car["Date"] >= pos["Date"].min()) & (car["Date"] <= pos["Date"].max())].copy()
        if len(car) < 2:
            raise DataNotLoadedError("OpenF1 telemetry streams do not overlap")
        origin = car["Date"].iloc[0]
        seconds = (car["Date"] - origin).dt.total_seconds().to_numpy()
        pseconds = (pos["Date"] - origin).dt.total_seconds().to_numpy()
        for channel in ("X", "Y", "Z"):
            if channel in pos:
                car[channel] = np.interp(seconds, pseconds, pos[channel])
        car["Brake"] = (car["Brake"] > 0).astype(int)
        car["Time"] = car["Date"] - origin
        # Speed is km/h; integrate trapezoidally to a lap-relative distance.
        speed = car["Speed"].to_numpy(dtype=float) / 3.6
        car["Distance"] = np.r_[0, np.cumsum((speed[1:] + speed[:-1]) / 2 * np.diff(seconds))]
        car.attrs["drs_available"] = bool(drs_available)
        return car


def _messages(rows):
    columns = {"date": "Time", "category": "Category", "message": "Message",
               "flag": "Flag", "scope": "Scope", "sector": "Sector",
               "driver_number": "RacingNumber", "lap_number": "Lap"}
    frame = pd.DataFrame(rows).rename(columns=columns).reindex(columns=list(columns.values()))
    frame["Time"] = pd.to_datetime(frame["Time"], utc=True, format="ISO8601").dt.tz_localize(None)
    frame["RacingNumber"] = frame["RacingNumber"].map(_number)
    # OpenF1 encodes status in message text, rather than FastF1's Status field.
    frame["Status"] = None
    return frame


def _laps(session, rows, stints, positions, messages):
    codes = dict(zip(session.results["DriverNumber"], session.results["Abbreviation"]))
    data = []
    for row in rows:
        number = _number(row.get("driver_number"))
        if not codes.get(number) or not row.get("lap_number"):
            continue
        start = pd.to_datetime(row.get("date_start"), utc=True)
        start = start.tz_localize(None) if not pd.isna(start) else pd.NaT
        lap_time = _td(row.get("lap_duration"))
        item = {
            "Driver": codes[number], "DriverNumber": number,
            "LapNumber": row["lap_number"], "LapTime": lap_time,
            "LapStartDate": start, "LapStartTime": start - session.t0_date,
            "Time": start - session.t0_date + lap_time,
            "Sector1Time": _td(row.get("duration_sector_1")),
            "Sector2Time": _td(row.get("duration_sector_2")),
            "Sector3Time": _td(row.get("duration_sector_3")),
            "SpeedI1": row.get("i1_speed"), "SpeedI2": row.get("i2_speed"),
            "SpeedST": row.get("st_speed"), "SpeedFL": None,
            "Compound": None, "Stint": np.nan, "TyreLife": np.nan,
            "Position": np.nan, "PitInTime": pd.NaT, "PitOutTime": pd.NaT,
            "TrackStatus": None, "IsAccurate": None,
            "Deleted": False, "IsPersonalBest": False,
            "IsPitOutLap": bool(row.get("is_pit_out_lap")),
        }
        for stint in stints or []:
            if (_number(stint.get("driver_number")) == number
                    and stint.get("lap_start") is not None and stint.get("lap_end") is not None
                    and stint["lap_start"] <= row["lap_number"] <= stint["lap_end"]):
                item.update(Compound=stint.get("compound"), Stint=stint.get("stint_number"),
                            TyreLife=(stint.get("tyre_age_at_start") or 0)
                            + row["lap_number"] - stint["lap_start"] + 1)
                break
        data.append(item)
    frame = OpenF1Laps(data, session=session)
    if frame.empty:
        return frame
    for column in ("LapTime", "LapStartTime", "Time", "Sector1Time", "Sector2Time",
                   "Sector3Time", "PitInTime", "PitOutTime"):
        frame[column] = (pd.Series(pd.NaT, index=frame.index, dtype="timedelta64[ns]")
                         if frame[column].isna().all()
                         else pd.to_timedelta(frame[column]).astype("timedelta64[ns]"))
    # Position is sampled at each driver's own completion time. Sorting only
    # by lap duration would invent an incorrect running order for lapped cars.
    if positions:
        pos = pd.DataFrame(positions)
        pos["Time"] = (pd.to_datetime(pos["date"], utc=True, format="ISO8601").dt.tz_localize(None)
                       - session.t0_date).astype("timedelta64[ns]")
        for number, group in frame.groupby("DriverNumber"):
            trace = pos[pos["driver_number"].map(_number) == number].dropna(subset=["Time"])
            completions = group.dropna(subset=["Time"]).sort_values("Time")
            if len(trace) and len(completions):
                merged = pd.merge_asof(pd.DataFrame(completions[["Time"]]),
                                       trace[["Time", "position"]].sort_values("Time"),
                                       on="Time", direction="backward")
                frame.loc[completions.index, "Position"] = merged["position"].to_numpy()
    # Deleted/reinstated lap-time messages are applied in chronological order.
    for message in sorted(messages or [], key=lambda r: r.get("date") or ""):
        text = (message.get("message") or "").upper()
        if "LAP TIME" not in text or not ("DELETED" in text or "REINSTATED" in text):
            continue
        number = _number(message.get("driver_number"))
        car = re.search(r"CAR\s+(\d+)", text)
        number = number or (car.group(1) if car else None)
        mask = frame["DriverNumber"] == number
        lap_match = re.search(r"\bLAP\s+(\d+)\b", text)
        time_match = re.search(r"\b(\d+):(\d{2}\.\d{3})\b", text)
        if lap_match:
            mask &= frame["LapNumber"] == int(lap_match.group(1))
        elif time_match:
            duration = int(time_match.group(1)) * 60 + float(time_match.group(2))
            mask &= (frame["LapTime"] - _td(duration)).abs() < pd.Timedelta(milliseconds=1)
        else:
            continue
        frame.loc[mask, "Deleted"] = "DELETED" in text
    valid = frame[frame["LapTime"].notna() & (frame["LapTime"] > pd.Timedelta(0))
                  & ~frame["Deleted"] & ~frame["IsPitOutLap"]]
    if len(valid):
        indices = valid.groupby("DriverNumber")["LapTime"].idxmin()
        frame.loc[indices, "IsPersonalBest"] = True
    return frame


def supplement(base, year, identifier, *, laps=False, telemetry=False,
               messages=False, weather=False):
    """Try public historical feeds; preserve the original session on failure."""
    if year < 2023 or os.environ.get("OPENF1_ENABLED", "true").lower() in ("false", "0", "no"):
        return base
    try:
        info = _resolve_session(year, identifier, base.event)
        if info is None:
            return base
        drivers = _get("drivers", session_key=info["session_key"])
        session = OpenF1Session(base, info, drivers)
        # Race/qualifying classification still normally comes from Jolpica.
        # Sprint qualifying/practice can otherwise contain just a driver list.
        result_columns = (["Q1", "Q2", "Q3"] if identifier in ("Q", "SQ", "SS")
                          else ["Position"])
        if session.results[result_columns].isna().all().all():
            classification = _optional("session_result", session_key=session.session_key)
            if classification:
                for result in classification:
                    mask = session.results["DriverNumber"] == _number(result.get("driver_number"))
                    if not mask.any():
                        continue
                    def fill(field, value):
                        missing = mask & session.results[field].isna()
                        if missing.any():
                            session.results.loc[missing, field] = value
                    fill("Position", result.get("position"))
                    duration = result.get("duration")
                    if isinstance(duration, list):
                        for field, value in zip(("Q1", "Q2", "Q3"), duration):
                            fill(field, _td(value))
                    elif duration is not None:
                        elapsed = duration
                        if identifier in ("R", "S") and result.get("position") != 1:
                            gap = result.get("gap_to_leader")
                            elapsed = gap if isinstance(gap, (int, float)) else None
                        fill("Time", _td(elapsed))
                    session._results_restored = True
            elif classification is None:
                session.expires_at = time.monotonic() + 30
        missing_laps = (laps or telemetry) and _missing(base, "laps")
        need_messages = messages and _missing(base, "race_control_messages", allow_empty=True)
        raw_messages = None
        if missing_laps or need_messages:
            raw_messages = _optional("race_control", session_key=session.session_key)
            if raw_messages is None:
                session.expires_at = time.monotonic() + 30
        if need_messages and raw_messages is not None:
            session._feeds["race_control_messages"] = _messages(raw_messages)
        if missing_laps:
            raw_laps = _get("laps", session_key=session.session_key)
            if raw_laps:
                stints = _optional("stints", session_key=session.session_key)
                positions = _optional("position", session_key=session.session_key)
                if not stints or not positions:
                    session.expires_at = time.monotonic() + 30
                session._feeds["laps"] = _laps(
                    session, raw_laps, stints, positions, raw_messages)
        elif telemetry:
            # Existing native lap timing can be used to select OpenF1 traces.
            native = _read(base, "laps")
            if native is not None:
                session._feeds["laps"] = OpenF1Laps(native.copy(), session=session)
                native_origin = _read(base, "t0_date")
                if native_origin is not None:
                    session.t0_date = pd.to_datetime(native_origin, utc=True).tz_localize(None)
        if weather and _missing(base, "weather_data"):
            rows = _get("weather", session_key=session.session_key)
            if rows:
                wx = session._timed_frame(rows, {
                    "air_temperature": "AirTemp", "track_temperature": "TrackTemp",
                    "humidity": "Humidity", "pressure": "Pressure", "wind_speed": "WindSpeed",
                    "wind_direction": "WindDirection", "rainfall": "Rainfall"})
                wx["Time"] = wx["SessionTime"]
                session._feeds["weather_data"] = wx
        if session._feeds or session._results_restored:
            log.info("OpenF1 restored session %s (%s): %s", session.session_key,
                     info.get("session_name"), ", ".join(session._feeds))
            return session
    except (OpenF1Unavailable, ValueError, KeyError, TypeError) as exc:
        log.warning("OpenF1 fallback unavailable %s %s: %s", year, identifier, exc)
    return base
