"""
Offline tests for the results endpoint (`fastf1_service.get_results`), in
particular the qualifying gap-to-pole fields (q1Gap/q2Gap/q3Gap) added
alongside the existing q1/q2/q3 formatted lap-time strings.

    python test_results_service.py     (or: pytest test_results_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def _td(seconds):
    return pd.Timedelta(seconds=seconds) if seconds is not None else pd.NaT


def _results(rows):
    return pd.DataFrame(rows, columns=[
        "Position", "ClassifiedPosition", "DriverNumber", "Abbreviation",
        "DriverId", "FirstName", "LastName", "FullName", "HeadshotUrl",
        "CountryCode", "TeamName", "TeamId", "TeamColor", "GridPosition",
        "Status", "Points", "Time", "Q1", "Q2", "Q3",
    ])


def _stub_session(results):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        name="Qualifying",
        results=results,
        total_laps=0,
    )


def _laps(rows):
    return pd.DataFrame(rows, columns=["DriverNumber", "LapTime"])


def _stub_practice_session(results, laps):
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        name="Practice 1",
        results=results,
        laps=laps,
        total_laps=0,
    )


def _practice_results():
    """As FastF1 returns them for practice: car-number order, no timing at all."""
    return _results([
        {"Position": None, "ClassifiedPosition": "", "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": None, "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": pd.NaT, "Q2": pd.NaT, "Q3": pd.NaT},
        {"Position": None, "ClassifiedPosition": "", "DriverNumber": "44", "Abbreviation": "HAM",
         "DriverId": None, "FirstName": "Lewis", "LastName": "Hamilton",
         "FullName": "Lewis Hamilton", "HeadshotUrl": None, "CountryCode": "GBR",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": pd.NaT, "Q2": pd.NaT, "Q3": pd.NaT},
    ])


def test_gap_is_none_for_pole_and_positive_for_the_rest(monkeypatch):
    results = _results([
        {"Position": 1, "ClassifiedPosition": "1", "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": "max_verstappen", "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": 1, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.0), "Q2": _td(89.0), "Q3": _td(88.123)},
        {"Position": 2, "ClassifiedPosition": "2", "DriverNumber": "44", "Abbreviation": "HAM",
         "DriverId": "hamilton", "FirstName": "Lewis", "LastName": "Hamilton",
         "FullName": "Lewis Hamilton", "HeadshotUrl": None, "CountryCode": "GBR",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "GridPosition": 2, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.5), "Q2": _td(89.4), "Q3": _td(88.456)},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "Q")
    pole, p2 = out["results"]

    assert pole["q3Gap"] is None
    assert pole["q1Gap"] is None
    assert p2["q3Gap"] == "+0.333"
    assert p2["q2Gap"] == "+0.400"
    assert p2["q1Gap"] == "+0.500"


def test_driver_without_a_time_in_a_segment_has_no_gap(monkeypatch):
    """A driver knocked out in Q1 has no Q3 time at all: gap must be None,
    not some nonsensical value computed against Q3's field-best."""
    results = _results([
        {"Position": 1, "ClassifiedPosition": "1", "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": "max_verstappen", "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": 1, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.0), "Q2": _td(89.0), "Q3": _td(88.0)},
        {"Position": 20, "ClassifiedPosition": "20", "DriverNumber": "2", "Abbreviation": "SAR",
         "DriverId": "sargeant", "FirstName": "Logan", "LastName": "Sargeant",
         "FullName": "Logan Sargeant", "HeadshotUrl": None, "CountryCode": "USA",
         "TeamName": "Williams", "TeamId": "williams", "TeamColor": "64C4FF",
         "GridPosition": 20, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(91.0), "Q2": pd.NaT, "Q3": pd.NaT},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "Q")
    last = out["results"][1]

    assert last["q1Gap"] == "+1.000"
    assert last["q2Gap"] is None
    assert last["q3Gap"] is None
    assert last["q2"] is None
    assert last["q3"] is None


def test_missing_position_falls_back_to_row_order(monkeypatch):
    """Qualifying results commonly don't carry a "Position" value at all;
    the Pos column shouldn't just go blank when that happens; the row's own
    place in the (already classification-ordered) results is used instead."""
    results = _results([
        {"Position": None, "ClassifiedPosition": None, "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": "max_verstappen", "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.0), "Q2": _td(89.0), "Q3": _td(88.0)},
        {"Position": None, "ClassifiedPosition": None, "DriverNumber": "44", "Abbreviation": "HAM",
         "DriverId": "hamilton", "FirstName": "Lewis", "LastName": "Hamilton",
         "FullName": "Lewis Hamilton", "HeadshotUrl": None, "CountryCode": "GBR",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.5), "Q2": _td(89.4), "Q3": _td(88.4)},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "Q")
    assert [r["position"] for r in out["results"]] == [1, 2]


def test_real_position_is_not_overridden(monkeypatch):
    results = _results([
        {"Position": 3, "ClassifiedPosition": "3", "DriverNumber": "16", "Abbreviation": "LEC",
         "DriverId": "leclerc", "FirstName": "Charles", "LastName": "Leclerc",
         "FullName": "Charles Leclerc", "HeadshotUrl": None, "CountryCode": "MON",
         "TeamName": "Ferrari", "TeamId": "ferrari", "TeamColor": "E8002D",
         "GridPosition": 3, "Status": "Finished", "Points": 15, "Time": _td(5400),
         "Q1": _td(90.0), "Q2": _td(89.0), "Q3": _td(88.0)},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "R")
    assert out["results"][0]["position"] == 3


def test_blank_classified_position_is_nulled(monkeypatch):
    """FastF1 leaves `ClassifiedPosition` as an empty *string* for non-race
    sessions, not None. Clients prefer that field and fall back with `??`,
    which only triggers on null, so a blank string sailed through and left
    the qualifying "Pos" column empty even though `position` was populated.
    It must be normalised to null so the fallback actually fires.
    """
    results = _results([
        {"Position": None, "ClassifiedPosition": "", "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": "max_verstappen", "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.0), "Q2": _td(89.0), "Q3": _td(88.0)},
        {"Position": None, "ClassifiedPosition": "   ", "DriverNumber": "44", "Abbreviation": "HAM",
         "DriverId": "hamilton", "FirstName": "Lewis", "LastName": "Hamilton",
         "FullName": "Lewis Hamilton", "HeadshotUrl": None, "CountryCode": "GBR",
         "TeamName": "Mercedes", "TeamId": "mercedes", "TeamColor": "27F4D2",
         "GridPosition": None, "Status": None, "Points": None, "Time": pd.NaT,
         "Q1": _td(90.5), "Q2": _td(89.4), "Q3": _td(88.4)},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "Q")
    assert [r["classifiedPosition"] for r in out["results"]] == [None, None]
    assert [r["position"] for r in out["results"]] == [1, 2]


def test_race_classified_position_still_passes_through(monkeypatch):
    """The blank-string normalisation must not eat real values like "R"."""
    results = _results([
        {"Position": 20, "ClassifiedPosition": "R", "DriverNumber": "1", "Abbreviation": "VER",
         "DriverId": "max_verstappen", "FirstName": "Max", "LastName": "Verstappen",
         "FullName": "Max Verstappen", "HeadshotUrl": None, "CountryCode": "NED",
         "TeamName": "Red Bull", "TeamId": "red_bull", "TeamColor": "3671C6",
         "GridPosition": 1, "Status": "Accident", "Points": 0, "Time": pd.NaT,
         "Q1": None, "Q2": None, "Q3": None},
    ])
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(results))
    out = svc.get_results(2024, 1, "R")
    assert out["results"][0]["classifiedPosition"] == "R"


def test_practice_is_classified_by_best_lap_not_car_number(monkeypatch):
    """FastF1 hands practice back in car-number order with no times on it, so
    numbering those rows 1, 2, 3… claims a timesheet that doesn't exist. The
    laps are the only record of what happened, so the order (and the times
    shown) must come from them."""
    laps = _laps([
        {"DriverNumber": "1", "LapTime": _td(90.0)},
        {"DriverNumber": "1", "LapTime": _td(90.4)},
        {"DriverNumber": "44", "LapTime": _td(89.0)},
    ])
    monkeypatch.setattr(svc, "_load_session",
                        lambda *a, **k: _stub_practice_session(_practice_results(), laps))
    out = svc.get_results(2024, 1, "FP1")
    first, second = out["results"]

    assert [r["abbreviation"] for r in out["results"]] == ["HAM", "VER"]
    assert [r["position"] for r in out["results"]] == [1, 2]
    assert first["bestLap"] == "1:29.000"
    assert first["bestLapGap"] is None
    assert first["lapsCompleted"] == 1
    assert second["bestLap"] == "1:30.000"
    assert second["bestLapGap"] == "+1.000"
    assert second["lapsCompleted"] == 2


def test_practice_driver_without_a_lap_time_goes_last(monkeypatch):
    """A car that sat in the garage all session has no time to rank on; it
    belongs at the back rather than at the front on a zero."""
    laps = _laps([{"DriverNumber": "44", "LapTime": _td(89.0)}])
    monkeypatch.setattr(svc, "_load_session",
                        lambda *a, **k: _stub_practice_session(_practice_results(), laps))
    out = svc.get_results(2024, 1, "FP1")

    assert [r["abbreviation"] for r in out["results"]] == ["HAM", "VER"]
    assert out["results"][1]["bestLap"] is None
    assert out["results"][1]["lapsCompleted"] is None


def test_sprint_qualifying_is_loaded_with_laps_and_messages(monkeypatch):
    """Sprint qualifying results arrive with the Q1/Q2/Q3 columns empty, and
    FastF1 will only work them out from lap times if it is given the laps *and*
    the race control messages that say which laps were deleted. Ask for the
    results alone and the session stays blank."""
    calls = {}

    def _fake_load(year, rnd, identifier, with_laps, **kwargs):
        calls["with_laps"] = with_laps
        calls["with_messages"] = kwargs.get("with_messages", False)
        return _stub_session(_practice_results())

    monkeypatch.setattr(svc, "_load_session", _fake_load)
    svc.get_results(2024, 1, "SQ")
    assert calls == {"with_laps": True, "with_messages": True}


def test_deleted_laps_do_not_set_a_practice_best(monkeypatch):
    """A lap the stewards deleted never happened as far as the timesheet is
    concerned, so it mustn't be the time a driver is ranked on."""
    laps = pd.DataFrame(
        [
            {"DriverNumber": "1", "LapTime": _td(88.0), "Deleted": True},
            {"DriverNumber": "1", "LapTime": _td(90.0), "Deleted": False},
            {"DriverNumber": "44", "LapTime": _td(89.0), "Deleted": False},
        ],
        columns=["DriverNumber", "LapTime", "Deleted"],
    )
    monkeypatch.setattr(svc, "_load_session",
                        lambda *a, **k: _stub_practice_session(_practice_results(), laps))
    out = svc.get_results(2024, 1, "FP1")

    assert [r["abbreviation"] for r in out["results"]] == ["HAM", "VER"]
    assert out["results"][1]["bestLap"] == "1:30.000"
    # The deleted lap still counts as a lap run, just not as a time.
    assert out["results"][1]["lapsCompleted"] == 2


def test_non_practice_sessions_carry_no_best_lap(monkeypatch):
    """The best-lap fields are always present, so the payload shape doesn't
    change from session to session; only practice fills them in."""
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(_practice_results()))
    out = svc.get_results(2024, 1, "Q")
    assert [r["bestLap"] for r in out["results"]] == [None, None]
    assert [r["abbreviation"] for r in out["results"]] == ["VER", "HAM"]


def test_no_results_returns_empty_list(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: _stub_session(None))
    out = svc.get_results(2024, 1, "Q")
    assert out["results"] == []


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
