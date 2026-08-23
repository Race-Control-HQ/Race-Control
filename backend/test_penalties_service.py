"""
Offline tests for the driver-penalties endpoint (`fastf1_service.get_penalties`),
which classifies "Other"-category race-control messages into penalty types
and extracts the stewards' stated reasoning.

    python test_penalties_service.py     (or: pytest test_penalties_service.py)
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
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2", "DriverNumber": "44"},
        {"Abbreviation": "LEC", "DriverId": "leclerc", "FullName": "Charles Leclerc",
         "TeamName": "Ferrari", "TeamId": "ferrari", "TeamColor": "E8002D", "DriverNumber": "16"},
    ])


def _stub_session(rcm, total_laps=10):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        race_control_messages=rcm,
        laps=pd.DataFrame({"LapNumber": list(range(1, total_laps + 1))}),
        results=_results(),
    )


def _row(msg, t, racing_number=None, lap=5, category="Other"):
    return {
        "Time": t, "Category": category, "Message": msg, "Status": None,
        "Flag": None, "Scope": "Driver", "Sector": None,
        "RacingNumber": racing_number, "Lap": lap,
    }


def test_empty_log(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(_rcm([])))
    out = svc.get_penalties(2024, 1, "R")
    assert out["penalties"] == []
    assert out["eventName"] == "Test Grand Prix"


def test_non_penalty_messages_are_ignored(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row("DRS ENABLED", t0, category="Drs"),
        _row("TURN 3 INCIDENT INVOLVING CARS 44 (HAM) AND 16 (LEC) UNDER INVESTIGATION", t0),
        _row("GREEN FLAG", t0, category="Flag"),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    assert out["penalties"] == []


def test_time_penalty_extracts_driver_type_and_reason(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row(
            "FIA STEWARDS: 5 SECOND TIME PENALTY FOR CAR 44 (HAM) - CAUSING A COLLISION WITH CAR 16 (LEC)",
            t0, racing_number="44", lap=12,
        ),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    assert len(out["penalties"]) == 1
    p = out["penalties"][0]
    assert p["type"] == "Time Penalty"
    assert p["driverCode"] == "HAM"
    assert p["driverId"] == "hamilton"
    assert p["driverName"] == "Lewis Hamilton"
    assert p["teamName"] == "Mercedes"
    assert p["teamLogoUrl"] is not None
    assert p["reason"] == "Causing a collision with car 16 (lec)"
    assert p["lap"] == 12
    assert p["value"] == "5 seconds"


def test_multi_car_message_attributes_to_first_car_named(monkeypatch):
    """FIA convention: the penalized driver is always named first, even when
    another driver's car is mentioned later as part of the reasoning."""
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row(
            "FIA STEWARDS: DRIVE THROUGH PENALTY FOR CAR 16 (LEC) - CAUSING A COLLISION WITH CAR 44 (HAM)",
            t0, racing_number=None,
        ),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    assert len(out["penalties"]) == 1
    assert out["penalties"][0]["driverCode"] == "LEC"
    assert out["penalties"][0]["type"] == "Drive Through Penalty"


def test_extracts_penalty_value(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row("FIA STEWARDS: 10 SECOND STOP AND GO PENALTY FOR CAR 44 (HAM) - SPEEDING IN THE PIT LANE", t0),
        _row("FIA STEWARDS: 1 SECOND TIME PENALTY FOR CAR 44 (HAM) - TRACK LIMITS", t0),
        _row("FIA STEWARDS: THREE PLACE GRID PENALTY FOR CAR 44 (HAM) - IMPEDING ANOTHER DRIVER", t0),
        _row("FIA STEWARDS: 5 PLACE GRID PENALTY FOR CAR 44 (HAM) - CAUSING A COLLISION", t0),
        _row("FIA STEWARDS: REPRIMAND FOR CAR 44 (HAM) - CAUSING A COLLISION", t0),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    values = [p["value"] for p in out["penalties"]]
    assert values == ["10 seconds", "1 second", "3 places", "5 places", None]


def test_recognises_stop_and_go_grid_reprimand_and_disqualification(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row("FIA STEWARDS: 10 SECOND STOP AND GO PENALTY FOR CAR 44 (HAM) - SPEEDING IN THE PIT LANE", t0),
        _row("FIA STEWARDS: THREE PLACE GRID PENALTY FOR CAR 44 (HAM) - IMPEDING ANOTHER DRIVER", t0),
        _row("FIA STEWARDS: REPRIMAND FOR CAR 44 (HAM) - CAUSING A COLLISION", t0),
        _row("FIA STEWARDS: CAR 44 (HAM) DISQUALIFIED FROM THE RACE - TECHNICAL INFRINGEMENT", t0),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    types = [p["type"] for p in out["penalties"]]
    assert types == ["Stop & Go Penalty", "Grid Penalty", "Reprimand", "Disqualification"]


def test_message_without_dash_has_no_extracted_reason(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row("CAR 44 (HAM) TIME PENALTY APPLIED", t0),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    assert out["penalties"][0]["reason"] is None
    assert out["penalties"][0]["message"] == "CAR 44 (HAM) TIME PENALTY APPLIED"


def test_penalties_are_chronological(monkeypatch):
    t0 = pd.Timestamp("2024-03-02T12:00:00Z")
    rcm = _rcm([
        _row("FIA STEWARDS: TIME PENALTY FOR CAR 44 (HAM) - SECOND", t0 + pd.Timedelta(minutes=5)),
        _row("FIA STEWARDS: TIME PENALTY FOR CAR 16 (LEC) - FIRST", t0),
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(rcm))
    out = svc.get_penalties(2024, 1, "R")
    assert [p["driverCode"] for p in out["penalties"]] == ["LEC", "HAM"]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
