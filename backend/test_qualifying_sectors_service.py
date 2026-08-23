from types import SimpleNamespace

import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def _td(seconds):
    return pd.Timedelta(seconds=seconds) if seconds is not None else pd.NaT


def _row(code, lap, sectors, *, deleted=False, speed=320):
    return {
        "Driver": code,
        "LapNumber": lap,
        "LapTime": _td(sum(sectors)),
        "Sector1Time": _td(sectors[0]),
        "Sector2Time": _td(sectors[1]),
        "Sector3Time": _td(sectors[2]),
        "Deleted": deleted,
        "SpeedI1": speed - 20,
        "SpeedI2": speed - 10,
        "SpeedFL": speed - 5,
        "SpeedST": speed,
    }


def _session(rows):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=pd.DataFrame(rows),
        results=pd.DataFrame([
            {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
             "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
             "DriverNumber": "44"},
            {"Abbreviation": "VER", "DriverId": "verstappen", "FullName": "Max Verstappen",
             "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
             "DriverNumber": "1"},
        ]),
    )


def test_empty_session_returns_available_false(monkeypatch):
    monkeypatch.setattr(
        svc, "_load_session",
        lambda *a, **k: SimpleNamespace(event={"EventName": "Empty"}, laps=pd.DataFrame()),
    )
    out = analytics.get_qualifying_sectors(2024, 1)
    assert out["available"] is False
    assert out["drivers"] == []


def test_sector_deltas_sum_to_driver_gap(monkeypatch):
    rows = [
        _row("HAM", 1, [30.0, 30.0, 30.0]),
        _row("VER", 1, [29.8, 30.5, 30.2]),
    ]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(rows))
    out = analytics.get_qualifying_sectors(2024, 1)

    ham = next(driver for driver in out["drivers"] if driver["code"] == "HAM")
    assert out["poleCode"] == "HAM"
    assert ham["sectorDeltaMs"] == [0, 0, 0]
    ver = next(driver for driver in out["drivers"] if driver["code"] == "VER")
    assert ver["sectorDeltaMs"] == [-200, 500, 200]
    assert sum(ver["sectorDeltaMs"]) == ver["gapToPoleMs"] == 500


def test_ideal_lap_uses_best_sector_from_all_valid_laps(monkeypatch):
    rows = [
        _row("HAM", 1, [30.0, 30.0, 30.0]),
        _row("VER", 1, [30.2, 30.2, 30.4]),
        _row("VER", 2, [30.0, 30.5, 30.1]),
        _row("VER", 3, [29.0, 29.0, 29.0], deleted=True),
    ]
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session(rows))
    out = analytics.get_qualifying_sectors(2024, 1)

    ver = next(driver for driver in out["drivers"] if driver["code"] == "VER")
    assert ver["idealSectorMs"] == [30_000, 30_200, 30_100]
    assert ver["idealLapMs"] == 90_300
    assert ver["idealGainMs"] == 300
    assert ver["speedST"] == 320
