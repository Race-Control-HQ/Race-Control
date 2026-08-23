import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def test_missing_driver_returns_available_false(monkeypatch):
    monkeypatch.setattr(svc._ergast, "get_race_results", lambda **_kwargs: "race")
    monkeypatch.setattr(svc._ergast, "get_qualifying_results", lambda **_kwargs: "qual")
    monkeypatch.setattr(svc, "_collect_multi", lambda _response: ([], pd.DataFrame()))
    monkeypatch.setattr(svc, "get_drivers", lambda _year: [])
    out = analytics.get_driver_fingerprint(2026, "missing")
    assert out["available"] is False


def test_fingerprint_returns_six_percentile_axes(monkeypatch):
    race = pd.DataFrame([
        {"driverId": "norris", "position": 1, "grid": 2, "points": 25},
        {"driverId": "piastri", "position": 3, "grid": 1, "points": 15},
    ])
    qualifying = pd.DataFrame([
        {"driverId": "norris", "position": 2},
        {"driverId": "piastri", "position": 1},
    ])
    description = pd.DataFrame([{"round": 1}])
    monkeypatch.setattr(svc._ergast, "get_race_results", lambda **_kwargs: "race")
    monkeypatch.setattr(svc._ergast, "get_qualifying_results", lambda **_kwargs: "qual")
    monkeypatch.setattr(
        svc,
        "_collect_multi",
        lambda response: ([race], description) if response == "race" else ([qualifying], description),
    )
    monkeypatch.setattr(
        svc,
        "get_drivers",
        lambda _year: [
            {"driverId": "norris", "code": "NOR"},
            {"driverId": "piastri", "code": "PIA"},
        ],
    )
    monkeypatch.setattr(svc, "get_weather", lambda *_args: {"rainfall": True})
    monkeypatch.setattr(
        svc,
        "get_reliability",
        lambda _year: {
            "drivers": [
                {"driverId": "norris", "finishRate": 100},
                {"driverId": "piastri", "finishRate": 90},
            ]
        },
    )
    monkeypatch.setattr(
        analytics,
        "get_tyre_performance",
        lambda *_args: {
            "stints": [
                {"driverCode": "NOR", "slopeSecPerLap": 0.05},
                {"driverCode": "PIA", "slopeSecPerLap": 0.10},
            ]
        },
    )
    out = analytics.get_driver_fingerprint(2026, "norris")
    assert out["available"] is True
    assert len(out["axes"]) == 6
    assert {axis["key"] for axis in out["axes"]} == {
        "qualifyingPace", "racePace", "tyreManagement",
        "startPerformance", "reliability", "wetWeatherPace",
    }
    assert all(0 <= axis["percentile"] <= 100 for axis in out["axes"])


def test_tyre_rounds_are_sampled_across_the_season():
    assert analytics._evenly_spaced_rounds(list(range(1, 11)), 3) == [1, 5, 10]
