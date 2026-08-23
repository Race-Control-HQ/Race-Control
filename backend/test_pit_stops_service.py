from types import SimpleNamespace

import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def _td(seconds):
    return pd.Timedelta(seconds=seconds) if seconds is not None else pd.NaT


def _session(laps):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=pd.DataFrame(laps),
        results=pd.DataFrame([
            {
                "Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
                "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
                "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
                "Position": 1,
            },
            {
                "Abbreviation": "VER", "DriverId": "verstappen", "FullName": "Max Verstappen",
                "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
                "DriverNumber": "1", "Status": "Finished", "ClassifiedPosition": "2",
                "Position": 2,
            },
        ]),
    )


def test_empty_session_returns_available_false(monkeypatch):
    monkeypatch.setattr(
        svc, "_load_session",
        lambda *a, **k: SimpleNamespace(event={"EventName": "Empty"}, laps=pd.DataFrame()),
    )
    out = analytics.get_pit_stops(2024, 1)
    assert out["available"] is False
    assert out["stops"] == []


def test_real_pit_transit_loss_and_position_outcome(monkeypatch):
    laps = [
        {"Driver": "HAM", "LapNumber": 10, "Position": 4, "Compound": "MEDIUM",
         "PitInTime": _td(900), "PitOutTime": pd.NaT},
        {"Driver": "HAM", "LapNumber": 11, "Position": 2, "Compound": "HARD",
         "PitInTime": pd.NaT, "PitOutTime": _td(924.5)},
        {"Driver": "VER", "LapNumber": 11, "Position": 3, "Compound": "MEDIUM",
         "PitInTime": pd.NaT, "PitOutTime": pd.NaT},
    ]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(laps))
    out = analytics.get_pit_stops(2024, 1)

    stop = out["stops"][0]
    assert stop["lossMs"] == 24_500
    assert stop["entryPosition"] == 4
    assert stop["rejoinPosition"] == 2
    assert stop["positionsGained"] == 2
    assert stop["outcome"] == "UNDERCUT"
    assert stop["rivals"] == ["VER"]
    assert out["circuitMedianLossMs"] == 24_500


def test_circuit_median_and_delta_are_returned(monkeypatch):
    laps = [
        {"Driver": "HAM", "LapNumber": 10, "Position": 1, "Compound": "MEDIUM",
         "PitInTime": _td(900), "PitOutTime": pd.NaT},
        {"Driver": "HAM", "LapNumber": 11, "Position": 1, "Compound": "HARD",
         "PitInTime": pd.NaT, "PitOutTime": _td(920)},
        {"Driver": "VER", "LapNumber": 12, "Position": 2, "Compound": "MEDIUM",
         "PitInTime": _td(1080), "PitOutTime": pd.NaT},
        {"Driver": "VER", "LapNumber": 13, "Position": 2, "Compound": "HARD",
         "PitInTime": pd.NaT, "PitOutTime": _td(1104)},
    ]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(laps))
    out = analytics.get_pit_stops(2024, 1)

    assert out["circuitMedianLossMs"] == 22_000
    assert [stop["deltaToMedianMs"] for stop in out["stops"]] == [-2000, 2000]
