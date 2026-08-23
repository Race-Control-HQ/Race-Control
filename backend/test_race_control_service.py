"""
Offline tests for the full race-control log endpoint
(`fastf1_service.get_race_control`), which returns every race-control
message regardless of category (unlike `get_flags`, which filters down to
flag/safety-car events only).

    python test_race_control_service.py     (or: pytest test_race_control_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def _rcm(rows):
    return pd.DataFrame(rows, columns=[
        "Time", "Category", "Message", "Status", "Flag", "Scope", "Sector",
        "RacingNumber", "Lap",
    ])


def _results():
    return pd.DataFrame([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamColor": "27F4D2", "DriverNumber": "44"},
    ])


def _stub_session(rcm, total_laps=10):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        race_control_messages=rcm,
        laps=pd.DataFrame({"LapNumber": list(range(1, total_laps + 1))}),
        results=_results(),
    )


def test_empty_log(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(_rcm([])))
    out = svc.get_race_control(2024, 1, "R")
    assert out["messages"] == []
    assert out["eventName"] == "Test Grand Prix"


def test_includes_non_flag_categories(monkeypatch):
    """get_flags would drop these; get_race_control must keep everything."""
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": t0, "Category": "Drs", "Message": "DRS ENABLED", "Status": "ENABLED",
         "Flag": None, "Scope": None, "Sector": None, "RacingNumber": None, "Lap": 3},
        {"Time": t0 + pd.Timedelta(minutes=1), "Category": "Other",
         "Message": "TURN 3 INCIDENT INVOLVING CARS 44 (HAM) UNDER INVESTIGATION",
         "Status": None, "Flag": None, "Scope": None, "Sector": None,
         "RacingNumber": "44", "Lap": 5},
        {"Time": t0 + pd.Timedelta(minutes=5), "Category": "Other",
         "Message": "CAR 44 (HAM) NOTED - TRACK LIMITS", "Status": None, "Flag": None,
         "Scope": "Driver", "Sector": None, "RacingNumber": "44", "Lap": 6},
        {"Time": t0 + pd.Timedelta(minutes=10), "Category": "Flag", "Message": "GREEN FLAG",
         "Status": None, "Flag": "GREEN", "Scope": "Track", "Sector": None,
         "RacingNumber": None, "Lap": 7},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))

    full = svc.get_race_control(2024, 1, "R")
    assert len(full["messages"]) == 4
    assert full["messages"][0]["category"] == "Drs"
    assert full["messages"][1]["driverCode"] == "HAM"

    # get_flags on the same data should keep only the GREEN flag message.
    flags_only = svc.get_flags(2024, 1, "R")
    assert len(flags_only["events"]) == 1
    assert flags_only["events"][0]["category"] == "Flag"


def test_time_is_serialised_as_unambiguous_utc(monkeypatch):
    # FastF1's own `to_datetime` helper parses race-control timestamps into
    # *naive* datetimes (no tzinfo) that represent UTC wall-clock time; this
    # is what `session.race_control_messages["Time"]` actually looks like in
    # production, not a tz-aware Timestamp. A naive value serialised with a
    # plain `.isoformat()` produces a string with no 'Z'/offset, which
    # browsers then parse as *local* time, silently shifting every
    # timestamp by the viewer's UTC offset. The output must carry an
    # explicit UTC marker so that can't happen.
    naive = pd.Timestamp("2026-07-26T12:20:00")  # no tzinfo, mirrors FastF1's real output
    assert naive.tzinfo is None
    rcm = _rcm([
        {"Time": naive, "Category": "Flag", "Message": "GREEN LIGHT", "Status": None,
         "Flag": "GREEN", "Scope": "Track", "Sector": None, "RacingNumber": None, "Lap": 1},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))

    out = svc.get_race_control(2024, 1, "R")
    time_str = out["messages"][0]["time"]
    assert time_str is not None
    assert time_str.endswith("+00:00") or time_str.endswith("Z"), (
        f"expected an explicit UTC offset, got {time_str!r}"
    )
    # And it must round-trip to the exact instant intended, not a shifted one.
    assert pd.Timestamp(time_str) == naive.tz_localize("UTC")


def test_messages_are_chronological(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": t0 + pd.Timedelta(minutes=5), "Category": "Other", "Message": "SECOND",
         "Status": None, "Flag": None, "Scope": None, "Sector": None,
         "RacingNumber": None, "Lap": 2},
        {"Time": t0, "Category": "Other", "Message": "FIRST", "Status": None, "Flag": None,
         "Scope": None, "Sector": None, "RacingNumber": None, "Lap": 1},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_race_control(2024, 1, "R")
    assert [m["message"] for m in out["messages"]] == ["FIRST", "SECOND"]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
