"""
Offline tests for the circuit outline endpoint (`fastf1_service.get_circuit_map`).

Regression coverage for a real bug: the track outline was drawn as a jagged
polygon on some circuits (Hungaroring, reported by a user) while others
(Albert Park) looked fine. Root cause: `lap.get_telemetry()` merges the
low-frequency (~3.7Hz) position channel onto the much higher-frequency car
channel, holding each position sample across many rows until the next real
update. The old code downsampled by taking every Nth *row* (uniform in
time), which over-samples those held/duplicate stretches (worse on tracks
with more slow corners, where the car dwells at nearly the same X/Y for
longer) and under-samples the transitions between them, drawing straight
edges through what should be curves.

The fix resamples at fixed steps of *distance* along the lap instead, which
this test verifies produces evenly spaced points regardless of how lopsided
the time-domain sampling was.

    python test_circuit_map_service.py     (or: pytest test_circuit_map_service.py)
"""

from types import SimpleNamespace

import numpy as np
import pandas as pd

import fastf1_service as svc


class _FakeLap:
    def __init__(self, tel: pd.DataFrame, driver="VER"):
        self._tel = tel
        self._driver = driver

    def get_telemetry(self):
        return self._tel

    def get(self, key, default=None):
        return {"Driver": self._driver}.get(key, default)

    def __getitem__(self, key):
        return {"Driver": self._driver}[key]


class _FakeLaps:
    def __init__(self, lap: _FakeLap):
        self._lap = lap

    def __len__(self):
        return 1

    def pick_fastest(self):
        return self._lap


def _staircase_telemetry():
    """A lap that spends many held/duplicate samples in a slow corner (X~0)
    then jumps through a fast, sparsely-time-sampled straight (X 0->1000)."""
    xs, ys, dists = [], [], []
    d = 0.0
    # Slow corner: 200 near-duplicate samples clustered at (0, 0), distance
    # barely advancing between them (this is the "held" stretch).
    for i in range(200):
        xs.append(0.0 + (0.01 if i % 2 else 0.0))
        ys.append(0.0)
        d += 0.05
        dists.append(d)
    # Fast straight: only 5 samples cover a 1000-unit jump.
    for i in range(1, 6):
        xs.append(i * 200.0)
        ys.append(0.0)
        d += 200.0
        dists.append(d)
    n = len(xs)
    return pd.DataFrame({
        "X": xs,
        "Y": ys,
        "Z": [0.0] * n,
        "Speed": [50.0] * 200 + [300.0] * 5,
        "DRS": [0] * n,
        "Distance": dists,
    })


def _stub_session(tel):
    lap = _FakeLap(tel)
    laps = _FakeLaps(lap)
    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix", "Location": "Testville", "Country": "Testland"},
        laps=laps,
        get_driver=lambda abbr: {"FullName": "Max Verstappen", "TeamName": "Red Bull Racing", "TeamColor": "3671C6"},
        get_circuit_info=lambda: SimpleNamespace(rotation=0.0, corners=pd.DataFrame(columns=["Number", "Letter"])),
    )
    return session


def test_outline_resampled_by_distance_not_time_row(monkeypatch):
    tel = _staircase_telemetry()
    session = _stub_session(tel)
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)
    outline = out["outline"]
    assert len(outline) == 350

    xs = np.array([p["x"] for p in outline])
    # The old row-uniform downsample would put the vast majority of the 350
    # points inside the held cluster near x=0 (since 200 of 205 raw rows
    # live there) and almost none across the 0->1000 jump. Distance-uniform
    # resampling should instead spread points across the full x range.
    assert xs.max() > 900  # actually reaches near the far end
    # At least a meaningful fraction of points should lie in the "straight"
    # half of the track (x > 500), which the old algorithm would essentially
    # skip entirely.
    assert (xs > 500).sum() > 50


def _long_lap_telemetry(length_m=4400.0, raw_rows=6000):
    xs = np.linspace(0, length_m, raw_rows)
    return pd.DataFrame({
        "X": xs,
        "Y": np.zeros(raw_rows),
        "Z": np.zeros(raw_rows),
        "Speed": np.full(raw_rows, 200.0),
        "DRS": np.zeros(raw_rows),
        "Distance": xs,
    })


def test_point_density_scales_with_track_length(monkeypatch):
    # A realistic ~4.4km lap should get noticeably more than the old fixed
    # 350-point budget, since 350 points over that distance (~12.5m/sample)
    # is too coarse to resolve a short, tight corner; this is what left
    # corners looking like sharp polygon vertices even after the distance-
    # uniform resampling fix, until the point count itself was raised too.
    session = _stub_session(_long_lap_telemetry())
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)
    assert len(out["outline"]) > 900  # ~4400m / 4m spacing, well above 350
    assert len(out["outline"]) <= 1600  # stays under the sanity cap


def _polygon_telemetry(vertices=20, rows_per_edge=300, length_m=4400.0):
    """A degraded position trace: only `vertices` genuinely distinct samples,
    each held constant across many rows (what a sparse pos-data merge looks
    like). Renders as a hard-edged polygon no matter how it's resampled."""
    xs, ys, dists = [], [], []
    for v in range(vertices):
        angle = 2 * np.pi * v / vertices
        vx, vy = np.cos(angle) * 1000, np.sin(angle) * 1000
        for _ in range(rows_per_edge):
            xs.append(vx)
            ys.append(vy)
    n = len(xs)
    dists = list(np.linspace(0, length_m, n))
    return pd.DataFrame({
        "X": xs, "Y": ys, "Z": [0.0] * n,
        "Speed": [200.0] * n, "DRS": [0] * n, "Distance": dists,
    })


def _circle_telemetry(samples=1200, length_m=4400.0):
    """A healthy trace: hundreds of distinct position samples."""
    angles = np.linspace(0, 2 * np.pi, samples)
    return pd.DataFrame({
        "X": np.cos(angles) * 1000,
        "Y": np.sin(angles) * 1000,
        "Z": np.zeros(samples),
        "Speed": np.full(samples, 200.0),
        "DRS": np.zeros(samples),
        "Distance": np.linspace(0, length_m, samples),
    })


def test_distinct_xy_count_ignores_held_duplicate_rows():
    # 6000 rows but only 20 genuinely distinct positions.
    assert svc._distinct_xy_count(_polygon_telemetry(vertices=20)) == 20
    assert svc._distinct_xy_count(_circle_telemetry(samples=1200)) > 900


def test_falls_back_from_sparse_fastest_lap_to_richer_lap(monkeypatch):
    # The fastest lap has a degraded position trace (20 distinct samples);
    # another lap has a healthy one. The outline must come from the healthy
    # lap, not the fastest, otherwise corners render as polygon vertices.
    sparse = _FakeLap(_polygon_telemetry(vertices=20))
    rich = _FakeLap(_circle_telemetry(samples=1200))

    class _Laps:
        def __len__(self):
            return 2

        def pick_fastest(self):
            return sparse

        def iterrows(self):
            return iter([(0, sparse), (1, rich)])

    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix", "Location": "Testville", "Country": "Testland"},
        laps=_Laps(),
        get_driver=lambda abbr: {"FullName": "Max Verstappen", "TeamName": "Red Bull Racing", "TeamColor": "3671C6"},
        get_circuit_info=lambda: SimpleNamespace(rotation=0.0, corners=pd.DataFrame(columns=["Number", "Letter"])),
    )
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)
    assert out["outlineSamples"] > 900, "should have selected the rich lap"

    # And the resulting outline should trace a genuine circle, not a 20-gon:
    # every point sits close to radius 1000 from the centre.
    xs = np.array([p["x"] for p in out["outline"]])
    ys = np.array([p["y"] for p in out["outline"]])
    radii = np.hypot(xs, ys)
    assert radii.min() > 995, "a polygon's edges would cut well inside the radius"


def test_sparse_trace_is_reported_in_outline_samples(monkeypatch):
    # When *every* lap is degraded there's nothing better to pick, but the
    # payload must still report the low sample count so the cause is visible.
    sparse = _FakeLap(_polygon_telemetry(vertices=20))

    class _Laps:
        def __len__(self):
            return 1

        def pick_fastest(self):
            return sparse

        def iterrows(self):
            return iter([(0, sparse)])

    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix", "Location": "Testville", "Country": "Testland"},
        laps=_Laps(),
        get_driver=lambda abbr: {"FullName": "Max Verstappen", "TeamName": "Red Bull Racing", "TeamColor": "3671C6"},
        get_circuit_info=lambda: SimpleNamespace(rotation=0.0, corners=pd.DataFrame(columns=["Number", "Letter"])),
    )
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)
    assert out["outlineSamples"] == 20


def test_marshal_lights_sectors_and_corner_extras_are_extracted(monkeypatch):
    lap = _FakeLap(_circle_telemetry(samples=1200))

    class _Laps:
        def __len__(self):
            return 1

        def pick_fastest(self):
            return lap

        def iterrows(self):
            return iter([(0, lap)])

    corners = pd.DataFrame({
        "Number": [1, 2],
        "Letter": ["", "A"],
        "X": [1000.0, -1000.0],
        "Y": [0.0, 0.0],
        "Angle": [90.0, -45.0],
        "Distance": [0.0, 2200.0],
    })
    marshal_lights = pd.DataFrame({"Number": [1, 2], "X": [500.0, -500.0], "Y": [10.0, -10.0]})
    marshal_sectors = pd.DataFrame({"Number": [1, 2], "X": [700.0, -700.0], "Y": [20.0, -20.0]})

    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix", "Location": "Testville", "Country": "Testland"},
        laps=_Laps(),
        get_driver=lambda abbr: {"FullName": "Max Verstappen", "TeamName": "Red Bull Racing", "TeamColor": "3671C6"},
        get_circuit_info=lambda: SimpleNamespace(
            rotation=0.0, corners=corners, marshal_lights=marshal_lights, marshal_sectors=marshal_sectors,
        ),
    )
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)

    assert len(out["corners"]) == 2
    c0 = out["corners"][0]
    assert c0["number"] == 1
    assert c0["angle"] == 90.0
    assert c0["distanceMeters"] == 0.0
    assert c0["speed"] is not None  # cross-referenced from the points trace

    assert out["marshalLights"] == [
        {"number": 1, "x": 500.0, "y": 10.0},
        {"number": 2, "x": -500.0, "y": -10.0},
    ]
    assert out["marshalSectors"] == [
        {"number": 1, "x": 700.0, "y": 20.0},
        {"number": 2, "x": -700.0, "y": -20.0},
    ]


def test_empty_session_returns_empty_outline(monkeypatch):
    laps = SimpleNamespace(__len__=lambda self=None: 0)

    class _EmptyLaps:
        def __len__(self):
            return 0

    session = SimpleNamespace(
        event={"EventName": "Test Grand Prix", "Location": "Testville", "Country": "Testland"},
        laps=_EmptyLaps(),
    )
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_circuit_map(2024, 1)
    assert out["outline"] == []
    assert out["points"] == []


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
