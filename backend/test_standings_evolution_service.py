"""
Offline tests for `get_standings_evolution`'s *public response contract*.

`test_points_progression_cache.py` covers the shared `_points_progression`
memoization plumbing, but never asserts on what `get_standings_evolution`
itself returns to the `/api/standings-evolution/{year}` route. This file
closes that gap: it monkeypatches `_points_progression` (the same seam the
existing cache tests use) and checks the endpoint's actual output shape --
per-driver `series`, current `points` derived from the last series entry,
and descending-by-points ordering.

    python test_standings_evolution_service.py     (or: pytest test_standings_evolution_service.py)
"""

import fastf1_service as svc


def _progression():
    return {
        "rounds": [1, 2, 3],
        "drivers": {
            "verstappen": {
                "givenName": "Max", "familyName": "Verstappen", "code": "VER",
                "teamName": "Red Bull", "teamColor": "3671C6",
                "series": [
                    {"round": 1, "points": 25.0},
                    {"round": 2, "points": 43.0},
                    {"round": 3, "points": 61.0},
                ],
            },
            "norris": {
                "givenName": "Lando", "familyName": "Norris", "code": "NOR",
                "teamName": "McLaren", "teamColor": "FF8000",
                "series": [
                    {"round": 1, "points": 18.0},
                    {"round": 2, "points": 33.0},
                    {"round": 3, "points": 63.0},
                ],
            },
        },
    }


def test_returns_year_and_rounds_from_the_progression(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())

    out = svc.get_standings_evolution(2026)

    assert out["year"] == 2026
    assert out["rounds"] == [1, 2, 3]


def test_each_driver_carries_its_full_cumulative_series(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())

    out = svc.get_standings_evolution(2026)
    by_id = {d["driverId"]: d for d in out["drivers"]}

    ver = by_id["verstappen"]
    assert ver["name"] == "Max Verstappen"
    assert ver["code"] == "VER"
    assert ver["teamName"] == "Red Bull"
    assert ver["teamColor"] == "3671C6"
    assert ver["series"] == [
        {"round": 1, "points": 25.0},
        {"round": 2, "points": 43.0},
        {"round": 3, "points": 61.0},
    ]


def test_current_points_is_the_last_series_entry(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())

    out = svc.get_standings_evolution(2026)
    by_id = {d["driverId"]: d for d in out["drivers"]}

    # Norris leads on cumulative points despite trailing after round 2 --
    # "points" must reflect the final round, not an earlier snapshot.
    assert by_id["norris"]["points"] == 63.0
    assert by_id["verstappen"]["points"] == 61.0


def test_drivers_are_ordered_by_descending_current_points(monkeypatch):
    monkeypatch.setattr(svc, "_points_progression", lambda year: _progression())

    out = svc.get_standings_evolution(2026)

    assert [d["driverId"] for d in out["drivers"]] == ["norris", "verstappen"]


def test_a_driver_with_no_series_entries_defaults_to_zero_points(monkeypatch):
    progression = _progression()
    progression["drivers"]["stroll"] = {
        "givenName": "Lance", "familyName": "Stroll", "code": "STR",
        "teamName": "Aston Martin", "teamColor": "229971",
        "series": [],
    }
    monkeypatch.setattr(svc, "_points_progression", lambda year: progression)

    out = svc.get_standings_evolution(2026)
    by_id = {d["driverId"]: d for d in out["drivers"]}

    assert by_id["stroll"]["points"] == 0.0
    assert by_id["stroll"]["series"] == []


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
