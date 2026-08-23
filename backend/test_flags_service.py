"""
Offline tests for the track-flags endpoint (`fastf1_service.get_flags`).

These build a synthetic race-control-messages DataFrame and a stub Session
object rather than hitting the real F1 timing API, so they run without
network access.

    python test_flags_service.py     (or: pytest test_flags_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def _rcm(rows):
    return pd.DataFrame(rows, columns=[
        "Time", "Category", "Message", "Status", "Flag", "Scope", "Sector",
        "RacingNumber", "Lap",
    ])


def _laps(numbers):
    return pd.DataFrame({"LapNumber": numbers})


def _results():
    return pd.DataFrame([
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
         "TeamName": "Red Bull Racing", "TeamColor": "3671C6", "DriverNumber": "1"},
    ])


def _stub_session(rcm, total_laps=20):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        race_control_messages=rcm,
        laps=_laps(list(range(1, total_laps + 1))),
        results=_results(),
    )


def test_no_messages_returns_empty(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(_rcm([])))
    out = svc.get_flags(2024, 1, "R")
    assert out["events"] == []
    assert out["periods"] == []
    assert out["eventName"] == "Test Grand Prix"


def test_yellow_flag_period(monkeypatch):
    now = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": now, "Category": "Flag", "Message": "YELLOW FLAG", "Status": None,
         "Flag": "YELLOW", "Scope": "Track", "Sector": 3, "RacingNumber": None, "Lap": 10},
        {"Time": now + pd.Timedelta(minutes=1), "Category": "Flag", "Message": "CLEAR",
         "Status": None, "Flag": "CLEAR", "Scope": "Track", "Sector": None,
         "RacingNumber": None, "Lap": 11},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_flags(2024, 1, "R")
    assert len(out["events"]) == 2
    assert out["periods"] == [
        {"type": "YELLOW", "startLap": 10, "endLap": 11, "reason": "YELLOW FLAG"},
    ]


def test_double_yellow_and_safety_car(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": t0, "Category": "Flag", "Message": "DOUBLE YELLOW FLAG", "Status": None,
         "Flag": "DOUBLE YELLOW", "Scope": "Track", "Sector": 5, "RacingNumber": None, "Lap": 20},
        {"Time": t0 + pd.Timedelta(seconds=20), "Category": "SafetyCar",
         "Message": "SAFETY CAR DEPLOYED", "Status": "DEPLOYED", "Flag": None,
         "Scope": None, "Sector": None, "RacingNumber": None, "Lap": 20},
        {"Time": t0 + pd.Timedelta(minutes=3), "Category": "SafetyCar",
         "Message": "SAFETY CAR ENDING", "Status": None, "Flag": None,
         "Scope": None, "Sector": None, "RacingNumber": None, "Lap": 23},
        {"Time": t0 + pd.Timedelta(minutes=3, seconds=5), "Category": "Flag",
         "Message": "GREEN FLAG", "Status": None, "Flag": "GREEN", "Scope": "Track",
         "Sector": None, "RacingNumber": None, "Lap": 24},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_flags(2024, 1, "R")
    assert [p["type"] for p in out["periods"]] == ["DOUBLE_YELLOW", "SC"]
    sc_period = out["periods"][1]
    assert sc_period["startLap"] == 20
    assert sc_period["endLap"] == 23


def test_driver_specific_incident_kept_as_event_not_period(monkeypatch):
    now = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": now, "Category": "Flag", "Message": "BLACK AND WHITE FLAG FOR CAR 1 (VER)",
         "Status": None, "Flag": "BLACK AND WHITE", "Scope": "Driver", "Sector": None,
         "RacingNumber": "1", "Lap": 15},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_flags(2024, 1, "R")
    assert len(out["events"]) == 1
    assert out["events"][0]["driverCode"] == "VER"
    # Driver-scoped flags don't open a track-wide banding period.
    assert out["periods"] == []


def test_unclosed_period_closes_at_total_laps(monkeypatch):
    now = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        {"Time": now, "Category": "Flag", "Message": "RED FLAG", "Status": None,
         "Flag": "RED", "Scope": "Track", "Sector": None, "RacingNumber": None, "Lap": 18},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm, total_laps=20))
    out = svc.get_flags(2024, 1, "R")
    assert out["periods"] == [
        {"type": "RED", "startLap": 18, "endLap": 20, "reason": "RED FLAG"},
    ]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
