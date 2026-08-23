"""
Offline tests for `get_compare`'s *public response contract* (the
`/api/compare/{year}/{d1}/{d2}` head-to-head endpoint).

No existing test module exercises `get_compare` directly -- only the shared
`_points_progression`/cache plumbing used elsewhere is covered. These tests
follow the `_collect_multi`-mocking pattern already used in
`test_driver_fingerprint_service.py` (stub `_ergast.get_race_results`/
`get_qualifying_results` to return an opaque sentinel, then stub
`_collect_multi` to return canned `(contents, description)` per sentinel) so
no real Ergast/Jolpica response object needs to be reconstructed.

    python test_compare_service.py     (or: pytest test_compare_service.py)
"""

import pandas as pd

import fastf1_service as svc


def _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc):
    monkeypatch.setattr(svc._ergast, "get_race_results", lambda **_kwargs: "race")
    monkeypatch.setattr(svc._ergast, "get_qualifying_results", lambda **_kwargs: "qual")
    monkeypatch.setattr(
        svc,
        "_collect_multi",
        lambda response: (race_frames, race_desc) if response == "race" else (qual_frames, qual_desc),
    )


def _race_rounds():
    # Round 1: VER wins, NOR 2nd. Round 2: NOR wins, VER 2nd. Round 3: NOR
    # wins, VER retires (DNF) -- exercises both the win/podium/dnf tally and
    # the "only count head-to-head when both finished with a position" rule.
    frames = [
        pd.DataFrame([
            {"driverId": "verstappen", "position": 1, "points": 25, "status": "Finished",
             "givenName": "Max", "familyName": "Verstappen",
             "constructorName": "Red Bull Racing", "constructorId": "red_bull"},
            {"driverId": "norris", "position": 2, "points": 18, "status": "Finished",
             "givenName": "Lando", "familyName": "Norris",
             "constructorName": "McLaren", "constructorId": "mclaren"},
        ]),
        pd.DataFrame([
            {"driverId": "norris", "position": 1, "points": 25, "status": "Finished",
             "givenName": "Lando", "familyName": "Norris",
             "constructorName": "McLaren", "constructorId": "mclaren"},
            {"driverId": "verstappen", "position": 2, "points": 18, "status": "Finished",
             "givenName": "Max", "familyName": "Verstappen",
             "constructorName": "Red Bull Racing", "constructorId": "red_bull"},
        ]),
        pd.DataFrame([
            {"driverId": "norris", "position": 1, "points": 25, "status": "Finished",
             "givenName": "Lando", "familyName": "Norris",
             "constructorName": "McLaren", "constructorId": "mclaren"},
            {"driverId": "verstappen", "position": None, "points": 0, "status": "Accident",
             "givenName": "Max", "familyName": "Verstappen",
             "constructorName": "Red Bull Racing", "constructorId": "red_bull"},
        ]),
    ]
    desc = pd.DataFrame([
        {"round": 1, "raceName": "Race One"},
        {"round": 2, "raceName": "Race Two"},
        {"round": 3, "raceName": "Race Three"},
    ])
    return frames, desc


def _qualifying_rounds():
    # Q1: VER pole. Q2: NOR pole.
    frames = [
        pd.DataFrame([
            {"driverId": "verstappen", "position": 1},
            {"driverId": "norris", "position": 2},
        ]),
        pd.DataFrame([
            {"driverId": "norris", "position": 1},
            {"driverId": "verstappen", "position": 2},
        ]),
    ]
    desc = pd.DataFrame([{"round": 1}, {"round": 2}])
    return frames, desc


def test_response_covers_year_and_both_requested_drivers_in_order(monkeypatch):
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "norris")

    assert out["year"] == 2026
    assert [d["driverId"] for d in out["drivers"]] == ["verstappen", "norris"]


def test_points_wins_podiums_and_best_finish_are_aggregated_across_rounds(monkeypatch):
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "norris")
    ver, nor = out["drivers"]

    assert ver["name"] == "Max Verstappen"
    assert ver["points"] == 43.0  # 25 + 18 + 0
    assert ver["wins"] == 1
    assert ver["podiums"] == 2
    assert ver["bestFinish"] == 1
    assert ver["dnf"] == 1

    assert nor["name"] == "Lando Norris"
    assert nor["points"] == 68.0  # 18 + 25 + 25
    assert nor["wins"] == 2
    assert nor["podiums"] == 3
    assert nor["bestFinish"] == 1
    assert nor["dnf"] == 0


def test_head_to_head_counters_only_count_rounds_where_both_have_a_position(monkeypatch):
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "norris")
    ver, nor = out["drivers"]

    # Round 1 -> VER, round 2 -> NOR, round 3 excluded (VER DNF'd, no position).
    assert ver["raceWins_h2h"] == 1
    assert nor["raceWins_h2h"] == 1

    # Q1 -> VER pole/h2h, Q2 -> NOR pole/h2h.
    assert ver["poles"] == 1
    assert nor["poles"] == 1
    assert ver["qualWins_h2h"] == 1
    assert nor["qualWins_h2h"] == 1


def test_per_round_entries_are_recorded_even_for_a_dnf(monkeypatch):
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "norris")
    ver, nor = out["drivers"]

    assert [r["round"] for r in ver["rounds"]] == [1, 2, 3]
    assert ver["rounds"][2] == {"round": 3, "raceName": "Race Three", "position": None}
    assert [r["position"] for r in nor["rounds"]] == [2, 1, 1]


def test_team_and_logo_are_resolved_for_both_drivers(monkeypatch):
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "norris")
    ver, nor = out["drivers"]

    assert ver["teamName"] == "Red Bull Racing"
    assert ver["teamId"] == "red_bull"
    assert ver["teamLogoUrl"] is not None and "redbullracing" in ver["teamLogoUrl"]

    assert nor["teamName"] == "McLaren"
    assert nor["teamId"] == "mclaren"
    assert nor["teamLogoUrl"] is not None and "mclaren" in nor["teamLogoUrl"]


def test_a_driver_absent_from_every_round_still_gets_a_blank_entry(monkeypatch):
    # d2 never appears in any race/qualifying frame -- get_compare must not
    # error, and should return its zeroed "blank" defaults.
    race_frames, race_desc = _race_rounds()
    qual_frames, qual_desc = _qualifying_rounds()
    _install_fixture(monkeypatch, race_frames, race_desc, qual_frames, qual_desc)

    out = svc.get_compare(2026, "verstappen", "hamilton")
    ver, ham = out["drivers"]

    assert ham["driverId"] == "hamilton"
    assert ham["name"] == "hamilton"  # falls back to the id, no results ever seen
    assert ham["points"] == 0.0
    assert ham["wins"] == 0
    assert ham["rounds"] == []
    # A driver who never appears can't win a head-to-head round against them.
    assert ver["raceWins_h2h"] == 0
    assert ver["qualWins_h2h"] == 0


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
