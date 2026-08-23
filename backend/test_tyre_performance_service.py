from types import SimpleNamespace

import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def _td(seconds):
    return pd.Timedelta(seconds=seconds) if seconds is not None else pd.NaT


def _session(rows):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=pd.DataFrame(rows),
        results=pd.DataFrame([{
            "Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
            "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
            "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
            "Position": 1.0,
        }]),
    )


def _lap(number, seconds, *, life=None, status="1", accurate=True,
         pit_in=None, pit_out=None, deleted=False):
    return {
        "Driver": "HAM", "LapNumber": number, "LapTime": _td(seconds),
        "Compound": "MEDIUM", "TyreLife": life if life is not None else number,
        "FreshTyre": number == 1, "Stint": 1, "TrackStatus": status,
        "IsAccurate": accurate, "PitInTime": _td(pit_in),
        "PitOutTime": _td(pit_out), "Deleted": deleted,
    }


def test_empty_session_returns_available_false(monkeypatch):
    monkeypatch.setattr(
        svc, "_load_session",
        lambda *a, **k: SimpleNamespace(event={"EventName": "Empty"}, laps=pd.DataFrame()),
    )
    out = analytics.get_tyre_performance(2024, 1)
    assert out["available"] is False
    assert out["stints"] == []


def test_excludes_neutralised_inaccurate_deleted_and_pit_laps(monkeypatch):
    rows = [
        _lap(1, 90.0),
        _lap(2, 90.5),
        _lap(3, 150.0, status="4"),
        _lap(4, 140.0, accurate=False),
        _lap(5, 130.0, deleted=True),
        _lap(6, 125.0, pit_in=500),
        _lap(7, 124.0, pit_out=510),
    ]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(rows))
    out = analytics.get_tyre_performance(2024, 1)

    assert out["available"] is True
    assert [point["lap"] for point in out["stints"][0]["points"]] == [1, 2]
    assert out["stints"][0]["slopeSecPerLap"] == 0.5


def test_returns_stint_relative_deltas_fit_and_compound_baseline(monkeypatch):
    rows = [_lap(1, 90.0), _lap(2, 90.2), _lap(3, 90.4)]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(rows))
    out = analytics.get_tyre_performance(2024, 1)

    stint = out["stints"][0]
    assert [point["deltaMs"] for point in stint["points"]] == [0, 200, 400]
    assert stint["slopeSecPerLap"] == 0.2
    assert len(stint["fit"]) == 2
    assert out["compoundBaselines"] == [{
        "compound": "MEDIUM", "slopeSecPerLap": 0.2, "stintCount": 1,
    }]
