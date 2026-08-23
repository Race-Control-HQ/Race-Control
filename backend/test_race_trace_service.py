"""
Offline tests for the race-trace endpoint (`analytics_service.get_race_trace`).

The property that makes a race trace readable is that the vertical distance
between two lines at a given lap equals the real gap between those cars, so
these tests assert the arithmetic directly rather than just the payload shape.

    python test_race_trace_service.py     (or: pytest test_race_trace_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import analytics_service as analytics
import fastf1_service as svc


def _laps(rows):
    """Build a laps frame with the columns the trace derivation reads."""
    return pd.DataFrame(rows, columns=[
        "Driver", "LapNumber", "LapTime", "Time", "LapStartTime",
        "Compound", "TrackStatus", "IsAccurate",
    ])


def _results(rows):
    return pd.DataFrame(rows, columns=[
        "Abbreviation", "DriverId", "FullName", "TeamName", "TeamId",
        "TeamColor", "DriverNumber", "Status", "ClassifiedPosition", "Position",
    ])


def _td(seconds):
    return pd.Timedelta(seconds=seconds) if seconds is not None else pd.NaT


def _stub_session(laps, results):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        laps=laps,
        results=results,
    )


def _driver_rows(code, lap_times, *, start_offset=0.0, compound="MEDIUM",
                 track_status="1", accurate=True):
    """Lap rows for one driver, with `Time` accumulated from `lap_times`.

    `start_offset` shifts the race-start clock so tests can prove the common
    origin is subtracted rather than leaking into the deltas.
    """
    rows = []
    clock = start_offset
    for i, lt in enumerate(lap_times, start=1):
        lap_start = clock
        clock += lt
        rows.append({
            "Driver": code,
            "LapNumber": i,
            "LapTime": _td(lt),
            "Time": _td(clock),
            "LapStartTime": _td(lap_start),
            "Compound": compound,
            "TrackStatus": track_status,
            "IsAccurate": accurate,
        })
    return rows


def _two_car_session():
    # HAM laps a flat 90s; VER laps 91s, so VER loses exactly 1s per lap.
    laps = _laps(
        _driver_rows("HAM", [90.0] * 3)
        + _driver_rows("VER", [91.0] * 3)
    )
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "DriverNumber": "1", "Status": "Finished", "ClassifiedPosition": "2",
         "Position": 2.0},
    ])
    return _stub_session(laps, results)


def _patch(monkeypatch, session):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)
    # Flag periods are a separate fetch; stub it so these tests stay offline.
    monkeypatch.setattr(svc, "get_flags", lambda *a, **k: {"periods": []})


# --------------------------------------------------------------------------- #
#  Leader mode
# --------------------------------------------------------------------------- #
def test_leader_mode_puts_the_leader_on_zero(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="leader")

    assert out["available"] is True
    assert out["mode"] == "leader"
    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    assert [lap["deltaMs"] for lap in ham["laps"]] == [0, 0, 0]


def test_leader_mode_gap_grows_by_one_second_per_lap(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="leader")

    ver = next(d for d in out["drivers"] if d["code"] == "VER")
    # Negative because a trailing car is behind; 1s lost each lap.
    assert [lap["deltaMs"] for lap in ver["laps"]] == [-1000, -2000, -3000]


def test_vertical_distance_between_lines_is_the_real_gap(monkeypatch):
    """The defining property of the chart: line separation == gap in seconds."""
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="leader")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    ver = next(d for d in out["drivers"] if d["code"] == "VER")
    for lap in range(3):
        separation = ham["laps"][lap]["deltaMs"] - ver["laps"][lap]["deltaMs"]
        expected_gap = (lap + 1) * 1000
        assert separation == expected_gap


# --------------------------------------------------------------------------- #
#  Median mode
# --------------------------------------------------------------------------- #
def test_median_mode_reports_the_green_flag_lap_as_context(monkeypatch):
    """The race-wide green-flag reference is returned with the chart."""
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="median")

    # Six laps: three at 90s, three at 91s -> median 90.5s.
    assert out["greenFlagMedianLapMs"] == 90_500


def test_median_mode_puts_a_lone_car_flat_on_its_own_median(monkeypatch):
    """With one car it *is* the median, so it sits flat by construction."""
    laps = _laps(_driver_rows("HAM", [90.0] * 4))
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    assert [lap["deltaMs"] for lap in ham["laps"]] == [0, 0, 0, 0]


def test_median_mode_gap_between_two_cars_still_equals_the_real_gap(monkeypatch):
    """The property that makes the chart readable has to survive the change of
    baseline: a shared baseline cancels when two lines are subtracted."""
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    ver = next(d for d in out["drivers"] if d["code"] == "VER")
    for lap in range(3):
        separation = ham["laps"][lap]["deltaMs"] - ver["laps"][lap]["deltaMs"]
        assert separation == (lap + 1) * 1000


def test_median_mode_splits_two_cars_symmetrically_about_zero(monkeypatch):
    # Two cars: the median sits midway, so the quicker car is +half the gap and
    # the slower car -half.
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    ver = next(d for d in out["drivers"] if d["code"] == "VER")
    assert [lap["deltaMs"] for lap in ham["laps"]] == [500, 1000, 1500]
    assert [lap["deltaMs"] for lap in ver["laps"]] == [-500, -1000, -1500]


def test_a_faster_than_field_car_trends_upward(monkeypatch):
    """Higher delta must mean further ahead, the sign convention the clients
    rely on to orient the y-axis."""
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    deltas = [lap["deltaMs"] for lap in ham["laps"]]
    assert deltas == sorted(deltas), "the quicker car's line must rise"
    assert deltas[-1] > deltas[0]


def test_a_neutralisation_moves_both_lines_by_the_same_amount(monkeypatch):
    """The fixed reference exposes lost race time without changing real gaps."""
    # Lap 2 is run 60s slower for BOTH cars (a safety car), and HAM is 1s/lap
    # quicker than VER on the green laps either side.
    laps = _laps(
        _driver_rows("HAM", [90.0, 150.0, 90.0])
        + _driver_rows("VER", [91.0, 151.0, 91.0])
    )
    laps.loc[laps["LapNumber"] == 2, "TrackStatus"] = "4"
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "DriverNumber": "1", "Status": "Finished", "ClassifiedPosition": "2",
         "Position": 2.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    assert [lap["deltaMs"] for lap in ham["laps"]] == [500, -59_000, -58_500]


# --------------------------------------------------------------------------- #
#  Reference-lap selection
# --------------------------------------------------------------------------- #
def test_safety_car_laps_are_excluded_from_the_reference(monkeypatch):
    """A crawling SC lap must not drag the baseline; that would tilt every
    line on the chart."""
    laps = _laps(
        _driver_rows("HAM", [90.0, 90.0])
        + [{
            "Driver": "HAM", "LapNumber": 3, "LapTime": _td(150.0), "Time": _td(330.0),
            "LapStartTime": _td(180.0), "Compound": "MEDIUM",
            "TrackStatus": "4", "IsAccurate": True,  # 4 = safety car
        }]
    )
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    assert out["greenFlagMedianLapMs"] == 90_000, "the 150s SC lap should be ignored"


def test_inaccurate_laps_are_excluded_from_the_reference(monkeypatch):
    laps = _laps(
        _driver_rows("HAM", [90.0, 90.0])
        + [{
            "Driver": "HAM", "LapNumber": 3, "LapTime": _td(120.0), "Time": _td(300.0),
            "LapStartTime": _td(180.0), "Compound": "MEDIUM",
            "TrackStatus": "1", "IsAccurate": False,  # in/out lap
        }]
    )
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    assert out["greenFlagMedianLapMs"] == 90_000


def test_all_laps_under_yellow_still_produces_a_reference(monkeypatch):
    """If no lap qualifies as green, fall back to the median of all timed laps
    rather than returning nothing — a skewed baseline still charts."""
    laps = _laps(_driver_rows("HAM", [95.0, 95.0], track_status="2"))
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    assert out["available"] is True
    assert out["greenFlagMedianLapMs"] == 95_000


# --------------------------------------------------------------------------- #
#  Race-start origin
# --------------------------------------------------------------------------- #
def test_the_session_clock_offset_is_removed(monkeypatch):
    """`Laps.Time` is a session clock, not time-since-start. A large constant
    offset must cancel, or every delta would be wrong by that offset."""
    laps = _laps(
        _driver_rows("HAM", [90.0] * 3, start_offset=3600.0)
        + _driver_rows("VER", [91.0] * 3, start_offset=3600.0)
    )
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "DriverNumber": "1", "Status": "Finished", "ClassifiedPosition": "2",
         "Position": 2.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="median")

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    # First lap elapsed is 90s, not 3690s.
    assert ham["laps"][0]["cumulativeMs"] == 90_000


# --------------------------------------------------------------------------- #
#  Metadata, ordering and degenerate inputs
# --------------------------------------------------------------------------- #
def test_drivers_are_returned_in_finishing_order(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1)
    assert [d["code"] for d in out["drivers"]] == ["HAM", "VER"]


def test_team_colour_and_identity_are_resolved_server_side(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1)

    ham = next(d for d in out["drivers"] if d["code"] == "HAM")
    assert ham["teamColor"] == "#27F4D2"
    assert ham["driverId"] == "hamilton"
    assert ham["teamName"] == "Mercedes"
    assert ham["finishPosition"] == 1


def test_a_y_domain_is_supplied_for_the_clients(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="leader")

    low, high = out["yDomainMs"]
    assert low < -3000 <= high, "the domain must cover every plotted delta"


def test_retirement_is_flagged_and_the_series_just_ends(monkeypatch):
    laps = _laps(
        _driver_rows("HAM", [90.0] * 3)
        + _driver_rows("VER", [91.0] * 1)  # stopped after one lap
    )
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
        {"Abbreviation": "VER", "DriverId": "max_verstappen", "FullName": "Max Verstappen",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "DriverNumber": "1", "Status": "Engine", "ClassifiedPosition": "R",
         "Position": 20.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="leader")

    ver = next(d for d in out["drivers"] if d["code"] == "VER")
    assert ver["retired"] is True
    assert ver["status"] == "Engine"
    assert ver["lapsCompleted"] == 1
    assert len(ver["laps"]) == 1


def test_compound_travels_with_each_lap(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1)
    assert out["drivers"][0]["laps"][0]["compound"] == "MEDIUM"


def test_an_unknown_mode_falls_back_to_median(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    out = analytics.get_race_trace(2024, 1, mode="nonsense")
    assert out["mode"] == "median"
    assert out["greenFlagMedianLapMs"] is not None


def test_mode_is_case_insensitive(monkeypatch):
    _patch(monkeypatch, _two_car_session())
    assert analytics.get_race_trace(2024, 1, mode="LEADER")["mode"] == "leader"


def test_no_laps_returns_an_unavailable_payload_rather_than_raising(monkeypatch):
    _patch(monkeypatch, _stub_session(_laps([]), _results([])))
    out = analytics.get_race_trace(2024, 1)

    assert out["available"] is False
    assert out["drivers"] == []
    assert out["eventName"] == "Test Grand Prix"


def test_none_laps_returns_an_unavailable_payload(monkeypatch):
    _patch(monkeypatch, _stub_session(None, _results([])))
    out = analytics.get_race_trace(2024, 1)
    assert out["available"] is False


def test_missing_results_still_produces_a_trace(monkeypatch):
    """Driver metadata is a nicety; the timing data is the point."""
    _patch(monkeypatch, _stub_session(_laps(_driver_rows("HAM", [90.0] * 2)), None))
    out = analytics.get_race_trace(2024, 1, mode="leader")

    assert out["available"] is True
    assert out["drivers"][0]["code"] == "HAM"
    assert out["drivers"][0]["retired"] is False


def test_flag_periods_are_included_for_chart_bands(monkeypatch):
    session = _two_car_session()
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)
    monkeypatch.setattr(svc, "get_flags", lambda *a, **k: {
        "periods": [{"type": "SC", "startLap": 2, "endLap": 3, "reason": "SAFETY CAR"}]
    })
    out = analytics.get_race_trace(2024, 1)

    assert out["periods"] == [
        {"type": "SC", "startLap": 2, "endLap": 3, "reason": "SAFETY CAR"}
    ]


def test_a_failing_flag_fetch_does_not_fail_the_trace(monkeypatch):
    session = _two_car_session()
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    def boom(*a, **k):
        raise RuntimeError("race control feed unavailable")

    monkeypatch.setattr(svc, "get_flags", boom)
    out = analytics.get_race_trace(2024, 1)

    assert out["available"] is True
    assert out["periods"] == []


def test_falls_back_to_cumulative_lap_times_without_a_session_clock(monkeypatch):
    """Some older seasons lack a usable `Time`/`LapStartTime`; accumulating
    `LapTime` still yields a chartable trace."""
    rows = _driver_rows("HAM", [90.0] * 3)
    for r in rows:
        r["Time"] = pd.NaT
        r["LapStartTime"] = pd.NaT
    laps = _laps(rows)
    results = _results([
        {"Abbreviation": "HAM", "DriverId": "hamilton", "FullName": "Lewis Hamilton",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "DriverNumber": "44", "Status": "Finished", "ClassifiedPosition": "1",
         "Position": 1.0},
    ])
    _patch(monkeypatch, _stub_session(laps, results))
    out = analytics.get_race_trace(2024, 1, mode="leader")

    assert out["available"] is True
    ham = out["drivers"][0]
    assert [lap["cumulativeMs"] for lap in ham["laps"]] == [90_000, 180_000, 270_000]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
