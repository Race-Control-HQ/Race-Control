"""
Offline tests for `fastf1_service._lap_telemetry` (used by `get_telemetry` /
`get_telemetry_compare`), covering the telemetry mini-map's position trace.

Regression coverage for a real bug: the telemetry mini-map (TelemetryMiniMap.tsx)
drew a visibly jagged track outline, the same symptom already root-caused and
fixed for the circuit map (test_circuit_map_service.py): `lap.get_telemetry()`
merges the low-frequency (~3.7Hz) position channel onto the much higher-
frequency car-data channel, holding each position sample across many rows
until the next real update. The old code downsampled by taking every Nth
*row* (uniform in time), which over-samples those held stretches and
under-samples the transitions between them. The fix resamples at fixed steps
of *distance* along the lap instead, mirroring get_circuit_map's approach.

    python test_telemetry_service.py     (or: pytest test_telemetry_service.py)
"""

from types import SimpleNamespace

import numpy as np
import pandas as pd

import fastf1_service as svc


def _staircase_telemetry():
    """A lap that spends many held/duplicate samples in a slow corner (X~0)
    then jumps through a fast, sparsely-time-sampled straight (X 0->1000)."""
    xs, ys, dists, times = [], [], [], []
    d = 0.0
    t = 0.0
    for i in range(200):
        xs.append(0.0 + (0.01 if i % 2 else 0.0))
        ys.append(0.0)
        d += 0.05
        t += 0.01
        dists.append(d)
        times.append(t)
    for i in range(1, 6):
        xs.append(i * 200.0)
        ys.append(0.0)
        d += 200.0
        t += 1.0
        dists.append(d)
        times.append(t)
    n = len(xs)
    return pd.DataFrame({
        "X": xs,
        "Y": ys,
        "Speed": [50.0] * 200 + [300.0] * 5,
        "Throttle": [30.0] * 200 + [100.0] * 5,
        "Brake": [0] * 200 + [0] * 5,
        "nGear": [2] * 200 + [7] * 5,
        "RPM": [8000.0] * 200 + [12000.0] * 5,
        "DRS": [0] * n,
        "Distance": dists,
        "Time": pd.to_timedelta(times, unit="s"),
    })


class _FakeLapRow(dict):
    """Mimics a FastF1 `Lap` (dict-like row) with a `.get_telemetry()` method."""

    def __init__(self, tel, **fields):
        super().__init__(LapNumber=1, LapTime=pd.Timedelta(seconds=90), Compound="MEDIUM", **fields)
        self._tel = tel

    def get_telemetry(self):
        return self._tel

    def get(self, key, default=None):
        return self[key] if key in self else default


class _FakeDriverLaps:
    def __init__(self, lap):
        self._lap = lap

    def __len__(self):
        return 1

    def pick_fastest(self):
        return self._lap


class _FakeLaps:
    def __init__(self, lap):
        self._lap = lap

    def pick_drivers(self, abbr):
        return _FakeDriverLaps(self._lap)


def test_position_trace_resampled_by_distance_not_time_row():
    tel = _staircase_telemetry()
    lap = _FakeLapRow(tel)
    session = SimpleNamespace(laps=_FakeLaps(lap))

    trace = svc._lap_telemetry(session, "VER", "fastest")
    assert trace is not None

    xs = np.array(trace["x"])
    # The old row-uniform downsample would put the vast majority of the
    # (up to 500) sampled points inside the held cluster near x=0 (200 of
    # 205 raw rows live there) and almost none across the 0->1000 jump.
    # Distance-uniform resampling should instead spread points across the
    # full x range, same as the circuit-map outline fix.
    assert xs.max() > 900
    assert (xs > 500).sum() > 20


def test_distance_is_evenly_spaced():
    tel = _staircase_telemetry()
    lap = _FakeLapRow(tel)
    session = SimpleNamespace(laps=_FakeLaps(lap))

    trace = svc._lap_telemetry(session, "VER", "fastest")
    dist = np.array(trace["distance"])
    steps = np.diff(dist)
    # Evenly spaced (within floating point tolerance) rather than clustered.
    assert steps.std() < steps.mean() * 0.05


def _held_position_telemetry(vertices=20, rows_per_vertex=250, length_m=4400.0):
    """A circular lap whose *position* channel only genuinely updates
    `vertices` times, each value held across many rows: what the merged
    pos/car-data frame actually looks like. Speed etc. still vary per row."""
    xs, ys = [], []
    for v in range(vertices):
        angle = 2 * np.pi * v / vertices
        vx, vy = np.cos(angle) * 1000, np.sin(angle) * 1000
        xs.extend([vx] * rows_per_vertex)
        ys.extend([vy] * rows_per_vertex)
    n = len(xs)
    return pd.DataFrame({
        "X": xs,
        "Y": ys,
        "Speed": np.full(n, 200.0),
        "Throttle": np.full(n, 100.0),
        "Brake": np.zeros(n),
        "nGear": np.full(n, 7),
        "RPM": np.full(n, 11000.0),
        "DRS": np.zeros(n),
        "Distance": np.linspace(0, length_m, n),
        "Time": pd.to_timedelta(np.linspace(0, 90, n), unit="s"),
    })


def test_held_position_samples_do_not_render_as_a_polygon():
    # Regression: interpolating X/Y straight against distance reproduces the
    # held plateaus as a staircase, so the resampled trace sits on ~20
    # straight lines with sharp joints: a polygon the frontend can't smooth
    # away. Deduping to genuine position updates first should instead give a
    # trace that stays close to the true circular racing line.
    lap = _FakeLapRow(_held_position_telemetry(vertices=20))
    session = SimpleNamespace(laps=_FakeLaps(lap))

    trace = svc._lap_telemetry(session, "VER", "fastest")
    xs = np.array(trace["x"])
    ys = np.array(trace["y"])

    # The giveaway is *distinct* positions, not radius: interpolating the held
    # values reproduces the step function, so nearly every resampled point
    # lands exactly on one of the 20 vertices (measured: 22 distinct points
    # out of 550). Deduping first spreads them along the chords instead
    # (measured: 523 of 550). Radius alone does not separate the two cases:
    # a staircase actually scores *better* on it, since sitting on a vertex
    # means sitting exactly on the true racing line.
    distinct = len(np.unique(np.column_stack([np.round(xs, 1), np.round(ys, 1)]), axis=0))
    assert distinct > len(xs) * 0.8, (
        f"only {distinct} distinct positions out of {len(xs)}, "
        "the trace collapsed back onto a polygon"
    )


def test_discrete_channels_stay_in_valid_range():
    tel = _staircase_telemetry()
    lap = _FakeLapRow(tel)
    session = SimpleNamespace(laps=_FakeLaps(lap))

    trace = svc._lap_telemetry(session, "VER", "fastest")
    assert all(b in (0, 1) for b in trace["brake"])
    assert all(d in (0, 1) for d in trace["drs"])
    assert all(g >= 0 for g in trace["gear"])


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
