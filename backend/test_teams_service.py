"""
Offline tests for `fastf1_service.get_teams`, specifically the per-driver
points breakdown added to each team's roster (`Team.drivers[].points`) and
the ordering of that roster.

`get_teams` composes `get_constructor_standings` and `get_drivers`, both of
which go through several pages of Ergast/Jolpica responses, stubbing that
whole chain isn't worth it here, so these tests monkeypatch the two
composed functions directly and exercise only `get_teams`'s own logic.

    python test_teams_service.py     (or: pytest test_teams_service.py)
"""

import fastf1_service as svc


def _standings():
    return [
        {"teamId": "red_bull", "teamName": "Red Bull", "position": 1, "points": 500, "wins": 10,
         "nationality": "Austrian"},
        {"teamId": "ferrari", "teamName": "Ferrari", "position": 2, "points": 400, "wins": 5,
         "nationality": "Italian"},
    ]


def _drivers():
    return [
        {"driverId": "perez", "givenName": "Sergio", "familyName": "Perez", "code": "PER",
         "number": "11", "headshotUrl": None, "teamId": "red_bull", "points": 150},
        {"driverId": "verstappen", "givenName": "Max", "familyName": "Verstappen", "code": "VER",
         "number": "1", "headshotUrl": None, "teamId": "red_bull", "points": 350},
        {"driverId": "leclerc", "givenName": "Charles", "familyName": "Leclerc", "code": "LEC",
         "number": "16", "headshotUrl": None, "teamId": "ferrari", "points": 400},
    ]


def test_team_roster_includes_individual_driver_points(monkeypatch):
    monkeypatch.setattr(svc, "get_constructor_standings", lambda year: _standings())
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())

    teams = svc.get_teams(2024)
    by_id = {t["teamId"]: t for t in teams}

    red_bull_points = {d["driverId"]: d["points"] for d in by_id["red_bull"]["drivers"]}
    assert red_bull_points == {"perez": 150, "verstappen": 350}
    assert by_id["ferrari"]["drivers"][0]["points"] == 400


def test_team_roster_sorted_by_points_descending(monkeypatch):
    monkeypatch.setattr(svc, "get_constructor_standings", lambda year: _standings())
    monkeypatch.setattr(svc, "get_drivers", lambda year: _drivers())

    teams = svc.get_teams(2024)
    red_bull = next(t for t in teams if t["teamId"] == "red_bull")

    # Verstappen (350) outscored Perez (150), so should lead the roster despite
    # being added second in the source driver list.
    assert [d["driverId"] for d in red_bull["drivers"]] == ["verstappen", "perez"]


def test_team_roster_handles_missing_points(monkeypatch):
    # A driver with no points on record (e.g. DNS all season) shouldn't
    # crash the sort or be dropped from the roster.
    drivers = _drivers()
    drivers.append({"driverId": "rookie", "givenName": "New", "familyName": "Driver", "code": "NEW",
                     "number": "99", "headshotUrl": None, "teamId": "red_bull", "points": None})
    monkeypatch.setattr(svc, "get_constructor_standings", lambda year: _standings())
    monkeypatch.setattr(svc, "get_drivers", lambda year: drivers)

    teams = svc.get_teams(2024)
    red_bull_ids = [d["driverId"] for d in next(t for t in teams if t["teamId"] == "red_bull")["drivers"]]
    assert red_bull_ids == ["verstappen", "perez", "rookie"]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
