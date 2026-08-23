from types import SimpleNamespace

import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def _session():
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=pd.DataFrame({"Driver": ["HAM", "VER"]}),
        results=pd.DataFrame([
            {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
             "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
             "DriverNumber": "44"},
            {"Abbreviation": "VER", "DriverId": "verstappen", "FullName": "Max Verstappen",
             "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
             "DriverNumber": "1"},
        ]),
    )


def _trace(code, times):
    return {
        "code": code,
        "lapTimeMs": int(times[-1] * 1000),
        "distance": [0.0, 50.0, 100.0],
        "time": times,
        "x": [0.0, 50.0, 100.0],
        "y": [0.0, 20.0, 0.0],
    }


def test_empty_telemetry_returns_available_false(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session())
    monkeypatch.setattr(svc, "_lap_telemetry", lambda *a, **k: None)
    out = analytics.get_minisectors(2024, 1)
    assert out["available"] is False
    assert out["segments"] == []


def test_bins_cover_track_without_gaps_and_choose_fastest_driver(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session())
    traces = {
        "HAM": _trace("HAM", [0.0, 5.0, 11.0]),
        "VER": _trace("VER", [0.0, 6.0, 10.0]),
    }
    monkeypatch.setattr(svc, "_lap_telemetry", lambda _session, code, _which: traces[code])
    out = analytics.get_minisectors(2024, 1, bins=6)

    assert out["available"] is True
    assert len(out["segments"]) == 6
    assert out["segments"][0]["startDistance"] == 0.0
    assert out["segments"][-1]["endDistance"] == 100.0
    for left, right in zip(out["segments"], out["segments"][1:]):
        assert left["endDistance"] == right["startDistance"]
    assert all(len(segment["points"]) == 15 for segment in out["segments"])
    assert {segment["winnerCode"] for segment in out["segments"]} == {"HAM", "VER"}


def test_environment_ceiling_caps_driver_telemetry_loads(monkeypatch):
    monkeypatch.setenv("MINISECTOR_MAX_DRIVERS", "1")
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _session())
    calls = []

    def telemetry(_session, code, _which):
        calls.append(code)
        return _trace(code, [0.0, 5.0, 10.0])

    monkeypatch.setattr(svc, "_lap_telemetry", telemetry)
    out = analytics.get_minisectors(2024, 1, top=10, bins=6)
    assert calls == ["HAM"]
    assert out["driverCount"] == 1
