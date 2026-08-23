"""
Offline tests for `fastf1_service.get_wdc_calculator`, the "who can still
win the WDC" title decider, following FastF1's own example methodology
(https://docs.fastf1.dev/gen_modules/examples_gallery/standings/plot_who_can_still_win_wdc.html):
theoretical max points remaining vs. the championship leader's current total.

`get_wdc_calculator` composes `get_drivers` and `get_schedule`, both of which
go through several pages of Ergast/FastF1 calls, stubbing that whole chain
isn't worth it here, so these tests monkeypatch the two composed functions
directly (same approach as test_teams_service.py).

    python test_wdc_calculator_service.py     (or: pytest test_wdc_calculator_service.py)
"""

import fastf1_service as svc


def _drivers():
    return [
        {"driverId": "verstappen", "givenName": "Max", "familyName": "Verstappen", "code": "VER",
         "position": 1, "points": 400, "teamName": "Red Bull", "teamId": "red_bull",
         "teamLogoUrl": None, "teamColor": "3671C6", "headshotUrl": None},
        {"driverId": "norris", "givenName": "Lando", "familyName": "Norris", "code": "NOR",
         "position": 2, "points": 350, "teamName": "McLaren", "teamId": "mclaren",
         "teamLogoUrl": None, "teamColor": "FF8000", "headshotUrl": None},
        {"driverId": "leclerc", "givenName": "Charles", "familyName": "Leclerc", "code": "LEC",
         "position": 3, "points": 200, "teamName": "Ferrari", "teamId": "ferrari",
         "teamLogoUrl": None, "teamColor": "E8002D", "headshotUrl": None},
    ]


def _remaining_events(n_conventional=0, n_sprint=0, completed_conventional=0):
    events = []
    rnd = 1
    for _ in range(completed_conventional):
        events.append({"round": rnd, "format": "conventional", "completed": True})
        rnd += 1
    for _ in range(n_conventional):
        events.append({"round": rnd, "format": "conventional", "completed": False})
        rnd += 1
    for _ in range(n_sprint):
        events.append({"round": rnd, "format": "sprint_qualifying", "completed": False})
        rnd += 1
    return events


def test_close_title_fight_multiple_drivers_can_still_win(monkeypatch):
    # Leader has 400, 2 conventional rounds left = 52 max points on offer.
    # Norris (350) could reach 402 > 400 -> can still win.
    # Leclerc (200) could reach 252 < 400 -> cannot.
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())
    monkeypatch.setattr(svc, "get_schedule", lambda year: _remaining_events(n_conventional=2))

    out = svc.get_wdc_calculator(2024)
    by_id = {d["driverId"]: d for d in out["drivers"]}

    assert out["roundsRemaining"] == 2
    assert out["maxRemainingPoints"] == 2 * 26
    assert by_id["verstappen"]["canWin"] is True
    assert by_id["norris"]["canWin"] is True
    assert by_id["leclerc"]["canWin"] is False
    assert out["decided"] is False


def test_season_over_only_leader_can_win(monkeypatch):
    # No rounds left at all -> nothing changes, title is decided.
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())
    monkeypatch.setattr(svc, "get_schedule", lambda year: _remaining_events(completed_conventional=24))

    out = svc.get_wdc_calculator(2024)
    assert out["roundsRemaining"] == 0
    assert out["maxRemainingPoints"] == 0
    assert out["decided"] is True
    by_id = {d["driverId"]: d for d in out["drivers"]}
    assert by_id["verstappen"]["canWin"] is True
    assert by_id["norris"]["canWin"] is False


def test_sprint_weekends_add_sprint_points_on_top(monkeypatch):
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())
    monkeypatch.setattr(svc, "get_schedule", lambda year: _remaining_events(n_sprint=1))

    out = svc.get_wdc_calculator(2024)
    assert out["sprintRoundsRemaining"] == 1
    assert out["maxRemainingPoints"] == 8 + 25 + 1


def test_driver_with_no_points_on_record_does_not_crash(monkeypatch):
    drivers = _drivers()
    drivers.append({"driverId": "rookie", "givenName": "New", "familyName": "Driver", "code": "NEW",
                     "position": None, "points": None, "teamName": "Red Bull", "teamId": "red_bull",
                     "teamLogoUrl": None, "teamColor": None, "headshotUrl": None})
    monkeypatch.setattr(svc, "get_drivers", lambda year: drivers)
    monkeypatch.setattr(svc, "get_schedule", lambda year: _remaining_events(n_conventional=1))

    out = svc.get_wdc_calculator(2024)
    by_id = {d["driverId"]: d for d in out["drivers"]}
    assert by_id["rookie"]["points"] == 0
    assert by_id["rookie"]["canWin"] is False
    # A driver with no `position` sorts to the back rather than crashing/erroring.
    assert out["drivers"][-1]["driverId"] == "rookie"


def test_no_standings_returns_empty_but_valid_payload(monkeypatch):
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(svc, "get_schedule", lambda year: [])

    out = svc.get_wdc_calculator(2024)
    assert out["drivers"] == []
    assert out["decided"] is True


def test_live_calculator_reports_no_through_round(monkeypatch):
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())
    monkeypatch.setattr(svc, "get_schedule", lambda year: _remaining_events(n_conventional=2))

    out = svc.get_wdc_calculator(2024)
    assert out["throughRound"] is None


# --------------------------------------------------------------------------- #
#  Historical "time machine" snapshots (through_round)
# --------------------------------------------------------------------------- #
# These exercise `get_wdc_calculator`'s other branch, which sources points
# from `_points_progression`'s round-by-round series instead of live
# standings, so they monkeypatch that function directly rather than
# `get_drivers`/`get_schedule` alone.

def _progression():
    return {
        "rounds": [1, 2, 3],
        "drivers": {
            "verstappen": {
                "givenName": "Max", "familyName": "Verstappen", "code": "VER",
                "teamName": "Red Bull", "teamColor": "3671C6",
                "series": [{"round": 1, "points": 25}, {"round": 2, "points": 43}, {"round": 3, "points": 68}],
            },
            "norris": {
                "givenName": "Lando", "familyName": "Norris", "code": "NOR",
                "teamName": "McLaren", "teamColor": "FF8000",
                "series": [{"round": 1, "points": 18}, {"round": 2, "points": 36}, {"round": 3, "points": 61}],
            },
        },
    }


def _full_season_schedule():
    # Rounds 1-3 are already reflected in `_progression`; 4-5 haven't run.
    return [
        {"round": 1, "format": "conventional", "completed": True},
        {"round": 2, "format": "conventional", "completed": True},
        {"round": 3, "format": "conventional", "completed": True},
        {"round": 4, "format": "conventional", "completed": False},
        {"round": 5, "format": "conventional", "completed": False},
    ]


def test_through_round_uses_historical_points_not_live_standings(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(svc, "get_schedule", lambda year: _full_season_schedule())

    out = svc.get_wdc_calculator(2024, through_round=2)
    by_id = {d["driverId"]: d for d in out["drivers"]}

    assert out["throughRound"] == 2
    assert out["roundsInSeason"] == 5
    assert by_id["verstappen"]["points"] == 43
    assert by_id["norris"]["points"] == 36
    # "Remaining" as of round 2 means every later round (3, 4, 5) even though
    # round 3 has actually since been run: the whole point of the time
    # machine is answering "as of round 2", not "as of today".
    assert out["roundsRemaining"] == 3


def test_through_round_is_clamped_to_season_length(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(svc, "get_schedule", lambda year: _full_season_schedule())

    out = svc.get_wdc_calculator(2024, through_round=999)

    assert out["throughRound"] == 5
    assert out["roundsRemaining"] == 0
    # No round-5 data in the progression series, so points hold at the last
    # round actually recorded (round 3) rather than erroring or zeroing out.
    by_id = {d["driverId"]: d for d in out["drivers"]}
    assert by_id["verstappen"]["points"] == 68


def test_through_round_zero_or_negative_clamps_to_round_one(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(svc, "get_schedule", lambda year: _full_season_schedule())

    out = svc.get_wdc_calculator(2024, through_round=0)

    assert out["throughRound"] == 1
    by_id = {d["driverId"]: d for d in out["drivers"]}
    assert by_id["verstappen"]["points"] == 25


def test_through_round_positions_recomputed_from_historical_points(monkeypatch):
    # Positions must reflect the standings as of that round, not whatever
    # `position` field a driver happens to carry today.
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(svc, "get_schedule", lambda year: _full_season_schedule())

    out = svc.get_wdc_calculator(2024, through_round=1)
    assert out["drivers"][0]["driverId"] == "verstappen"
    assert out["drivers"][0]["position"] == 1
    assert out["drivers"][1]["driverId"] == "norris"
    assert out["drivers"][1]["position"] == 2


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
