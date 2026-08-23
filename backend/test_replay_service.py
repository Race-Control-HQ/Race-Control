from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def test_replay_includes_gap_to_leader(monkeypatch):
    laps = pd.DataFrame([
        {
            "LapNumber": 1.0,
            "Position": 1.0,
            "Driver": "VER",
            "Time": pd.Timedelta("90s"),
            "LapTime": pd.Timedelta("90s"),
            "Compound": "MEDIUM",
            "TyreLife": 1.0,
        },
        {
            "LapNumber": 1.0,
            "Position": 2.0,
            "Driver": "NOR",
            "Time": pd.Timedelta("91.234s"),
            "LapTime": pd.Timedelta("91.234s"),
            "Compound": "MEDIUM",
            "TyreLife": 1.0,
        },
    ])
    results = pd.DataFrame([
        {"Abbreviation": "VER", "TeamColor": "3671C6"},
        {"Abbreviation": "NOR", "TeamColor": "FF8000"},
    ])
    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=laps,
        results=results,
    )
    monkeypatch.setattr(svc, "_load_session", lambda *args, **kwargs: session)

    replay = svc.get_race_replay(2024, 1)
    leader, second = replay["frames"][0]["order"]

    assert leader["gap"] == "LEADER"
    assert leader["gapMs"] == 0
    assert second["gap"] == "+1.234"
    assert second["gapMs"] == 1234
    assert "_completionMs" not in leader
