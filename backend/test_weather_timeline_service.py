from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def test_weather_uses_shared_loader_and_returns_timeline(monkeypatch):
    calls = []
    weather = pd.DataFrame([
        {
            "Time": pd.Timedelta(seconds=0), "AirTemp": 20.0, "TrackTemp": 30.0,
            "Humidity": 60.0, "Pressure": 1000.0, "WindSpeed": 2.0, "Rainfall": False,
        },
        {
            "Time": pd.Timedelta(seconds=60), "AirTemp": 19.5, "TrackTemp": 28.0,
            "Humidity": 65.0, "Pressure": 1000.0, "WindSpeed": 3.0, "Rainfall": True,
        },
    ])

    def load(*args, **kwargs):
        calls.append((args, kwargs))
        return SimpleNamespace(event={"EventName": "Wet GP"}, weather_data=weather)

    monkeypatch.setattr(svc, "_load_session", load)
    out = svc.get_weather(2024, 1, "R")

    assert calls[0][1]["with_weather"] is True
    assert out["available"] is True
    assert out["rainfall"] is True
    assert [point["timeSeconds"] for point in out["timeline"]] == [0.0, 60.0]
    assert out["timeline"][1]["trackTemp"] == 28.0
