"""
Offline tests for the tyre-strategy endpoint (`fastf1_service.get_strategy`),
in particular the "retired" / "status" fields added so the strategy view can
flag a driver who didn't finish instead of implying their last stint just...
stopped for no reason.

    python test_strategy_service.py     (or: pytest test_strategy_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def _laps(rows):
    return pd.DataFrame(rows, columns=["Driver", "Stint", "Compound", "LapNumber"])


def _results(rows):
    return pd.DataFrame(rows, columns=[
        "Abbreviation", "DriverId", "TeamName", "TeamId", "TeamColor",
        "DriverNumber", "Status", "ClassifiedPosition",
    ])


def _stub_session(laps, results):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=laps,
        results=results,
    )


def test_finisher_is_not_marked_retired(monkeypatch):
    laps = _laps([
        {"Driver": "HAM", "Stint": 1, "Compound": "MEDIUM", "LapNumber": 1},
        {"Driver": "HAM", "Stint": 1, "Compound": "MEDIUM", "LapNumber": 2},
    ])
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "TeamName": "Mercedes",
         "TeamId": "mercedes", "TeamColor": "27F4D2", "DriverNumber": "44",
         "Status": "Finished", "ClassifiedPosition": "1"},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(laps, results))
    out = svc.get_strategy(2024, 1)
    d = out["drivers"][0]
    assert d["retired"] is False
    assert d["status"] == "Finished"


def test_lapped_but_classified_finisher_is_not_retired(monkeypatch):
    """A driver a lap down has a non-"Finished" Status string but IS a
    finisher, same nuance `get_retirements` already handles via
    ClassifiedPosition being the authoritative signal."""
    laps = _laps([
        {"Driver": "PER", "Stint": 1, "Compound": "HARD", "LapNumber": 1},
    ])
    results = _results([
        {"Abbreviation": "PER", "DriverId": "perez", "TeamName": "Red Bull",
         "TeamId": "red_bull", "TeamColor": "3671C6", "DriverNumber": "11",
         "Status": "+1 Lap", "ClassifiedPosition": "8"},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(laps, results))
    out = svc.get_strategy(2024, 1)
    d = out["drivers"][0]
    assert d["retired"] is False


def test_retired_driver_is_flagged(monkeypatch):
    laps = _laps([
        {"Driver": "VER", "Stint": 1, "Compound": "SOFT", "LapNumber": 1},
        {"Driver": "VER", "Stint": 1, "Compound": "SOFT", "LapNumber": 2},
    ])
    results = _results([
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "TeamName": "Red Bull",
         "TeamId": "red_bull", "TeamColor": "3671C6", "DriverNumber": "1",
         "Status": "Engine", "ClassifiedPosition": "R"},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(laps, results))
    out = svc.get_strategy(2024, 1)
    d = out["drivers"][0]
    assert d["retired"] is True
    assert d["status"] == "Engine"


def test_ambiguous_retired_status_is_normalised(monkeypatch):
    laps = _laps([
        {"Driver": "LEC", "Stint": 1, "Compound": "SOFT", "LapNumber": 1},
    ])
    results = _results([
        {"Abbreviation": "LEC", "DriverId": "leclerc", "TeamName": "Ferrari",
         "TeamId": "ferrari", "TeamColor": "E8002D", "DriverNumber": "16",
         "Status": "Retired", "ClassifiedPosition": "R"},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(laps, results))
    out = svc.get_strategy(2024, 1)
    assert out["drivers"][0]["retired"] is True


def test_missing_results_defaults_to_not_retired(monkeypatch):
    """No results at all shouldn't crash and shouldn't imply every driver
    retired; status/retired should just be absent/False."""
    laps = _laps([
        {"Driver": "HAM", "Stint": 1, "Compound": "MEDIUM", "LapNumber": 1},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(laps, None))
    out = svc.get_strategy(2024, 1)
    d = out["drivers"][0]
    assert d["retired"] is False
    assert d["status"] is None


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
