"""
Offline tests for the replay car-position endpoint
(`fastf1_service.get_replay_positions`).

Unlike the other offline service tests, this one builds real
`fastf1.core.Laps`/`Telemetry` instances (not plain DataFrames or stubs),
because `get_replay_positions` relies on FastF1's own `Laps.get_pos_data()`
(via `.pick_drivers()` slicing `session.pos_data` by a driver's full set of
laps in one call) plus a `merge_asof` lap-bucketing step done locally:
behaviour that's easy to get subtly wrong with a hand-rolled stub. No
network access is used: `pos_data` is a synthetic in-memory `Telemetry`
frame.

    python test_replay_positions_service.py     (or: pytest test_replay_positions_service.py)
"""

from types import SimpleNamespace

import fastf1.core as core
import pandas as pd

import fastf1_service as svc


def _laps_for(drivers_and_laps):
    """drivers_and_laps: list of (abbr, driver_number, lap_number, start_s, end_s)."""
    rows = []
    for abbr, num, lap_no, start_s, end_s in drivers_and_laps:
        rows.append({
            "Driver": abbr,
            "DriverNumber": num,
            "LapNumber": float(lap_no),
            "LapStartTime": pd.Timedelta(seconds=start_s),
            "Time": pd.Timedelta(seconds=end_s),
        })
    return core.Laps(pd.DataFrame(rows))


def _pos_data(num_points, seconds_per_point=10, x_step=1, y_step=2):
    times = pd.timedelta_range(start="0s", periods=num_points, freq=f"{seconds_per_point}s")
    df = pd.DataFrame({
        "SessionTime": times,
        "X": [i * x_step for i in range(num_points)],
        "Y": [i * y_step for i in range(num_points)],
        "Z": [0] * num_points,
        "Status": ["OnTrack"] * num_points,
        "Source": ["pos"] * num_points,
    })
    return core.Telemetry(df)


def _stub_session(laps, pos_data_by_number):
    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=laps,
        results=pd.DataFrame([
            {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
             "TeamName": "Red Bull Racing", "TeamColor": "3671C6", "DriverNumber": "1"},
        ]),
        pos_data=pos_data_by_number,
    )
    laps.session = session
    return session


def test_no_laps_returns_empty(monkeypatch):
    laps = core.Laps(pd.DataFrame(columns=["Driver", "DriverNumber", "LapNumber", "LapStartTime", "Time"]))
    session = _stub_session(laps, {})
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)
    out = svc.get_replay_positions(2024, 1, points_per_lap=4)
    assert out["laps"] == []
    assert out["totalLaps"] == 0
    assert out["eventName"] == "Test Grand Prix"


def test_samples_positions_per_lap(monkeypatch):
    # One driver, two laps, each 90s long, sampled every 9s => ~10 pos points/lap.
    laps = _laps_for([
        ("VER", "1", 1, 0, 90),
        ("VER", "1", 2, 90, 180),
    ])
    pos = _pos_data(num_points=20, seconds_per_point=9)  # 0..171s, covers both laps
    session = _stub_session(laps, {"1": pos})
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_replay_positions(2024, 1, points_per_lap=4)
    assert out["totalLaps"] == 2
    assert [lap_entry["lap"] for lap_entry in out["laps"]] == [1, 2]

    lap1 = out["laps"][0]
    assert "VER" in lap1["positions"]
    pts = lap1["positions"]["VER"]
    assert 1 <= len(pts) <= 10
    for p in pts:
        assert len(p) == 2
        assert isinstance(p[0], (int, float))
        assert isinstance(p[1], (int, float))


def test_positions_bucketed_into_correct_lap(monkeypatch):
    # Two drivers on the same two laps; positions for lap 2 must not leak
    # into lap 1's bucket and vice versa.
    laps = _laps_for([
        ("VER", "1", 1, 0, 90),
        ("VER", "1", 2, 90, 180),
        ("HAM", "44", 1, 0, 92),
        ("HAM", "44", 2, 92, 181),
    ])
    ver_pos = _pos_data(num_points=19, seconds_per_point=10)  # 0..180s
    ham_pos = _pos_data(num_points=19, seconds_per_point=10, x_step=5, y_step=1)
    session = _stub_session(laps, {"1": ver_pos, "44": ham_pos})
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_replay_positions(2024, 1, points_per_lap=100)  # no downsampling, easier to reason about
    by_lap = {entry["lap"]: entry["positions"] for entry in out["laps"]}

    assert set(by_lap.keys()) == {1, 2}
    assert "VER" in by_lap[1] and "VER" in by_lap[2]
    assert "HAM" in by_lap[1] and "HAM" in by_lap[2]

    # Every VER point attributed to lap 1 must have come from a sample at or
    # after t=0 and before t=90 (lap 1 -> lap 2 boundary), i.e. x in [0, 9).
    lap1_xs = [p[0] for p in by_lap[1]["VER"]]
    lap2_xs = [p[0] for p in by_lap[2]["VER"]]
    assert max(lap1_xs) < min(lap2_xs)


def test_driver_meta_included(monkeypatch):
    laps = _laps_for([("VER", "1", 1, 0, 90)])
    pos = _pos_data(num_points=10)
    session = _stub_session(laps, {"1": pos})
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_replay_positions(2024, 1)
    codes = [d["code"] for d in out["drivers"]]
    assert "VER" in codes


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
