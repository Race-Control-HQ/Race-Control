"""
Offline tests for `fastf1_service._points_progression`'s in-process memo cache.

Regression: the WDC calculator's time-machine slider felt janky because every
distinct `through_round` was landing on a separate entry in main.py's
per-endpoint response cache (its cache key includes `through_round`), so each
new round the user scrubbed to triggered a brand new, independent, paginated
fetch of the whole season's race + sprint results from Ergast/Jolpica, even
though every round needs the exact same underlying season data. `_points_
progression` now memoizes that expensive fetch per year, one layer below the
per-endpoint cache, so the fetch happens once regardless of how many
different rounds get requested afterwards.

    python test_points_progression_cache.py     (or: pytest test_points_progression_cache.py)
"""

import fastf1_service as svc


def setup_function(_fn):
    # The cache is a module-level dict so it persists across tests in the
    # same process; start each test from a clean slate.
    svc._progression_cache.clear()


def _progression():
    return {
        "rounds": [1, 2],
        "drivers": {
            "verstappen": {
                "givenName": "Max", "familyName": "Verstappen", "code": "VER",
                "teamName": "Red Bull", "teamColor": "3671C6",
                "series": [{"round": 1, "points": 25}, {"round": 2, "points": 43}],
            },
        },
    }


def test_repeated_calls_for_the_same_year_only_compute_once(monkeypatch):
    calls = []

    def fake_compute(year):
        calls.append(year)
        return _progression()

    monkeypatch.setattr(svc, "_compute_points_progression", fake_compute)

    svc._points_progression(2026)
    svc._points_progression(2026)
    svc._points_progression(2026)

    assert calls == [2026]


def test_different_years_each_compute_independently(monkeypatch):
    calls = []

    def fake_compute(year):
        calls.append(year)
        return _progression()

    monkeypatch.setattr(svc, "_compute_points_progression", fake_compute)

    svc._points_progression(2025)
    svc._points_progression(2026)
    svc._points_progression(2025)  # still cached from the first call

    assert calls == [2025, 2026]


def test_cache_expires_after_ttl(monkeypatch):
    calls = []

    def fake_compute(year):
        calls.append(year)
        return _progression()

    monkeypatch.setattr(svc, "_compute_points_progression", fake_compute)

    clock = [1_000_000.0]
    monkeypatch.setattr(svc.time, "time", lambda: clock[0])

    svc._points_progression(2026)
    clock[0] += svc._PROGRESSION_CACHE_TTL_SECONDS + 1
    svc._points_progression(2026)

    assert calls == [2026, 2026]


def test_wdc_calculator_scrubbing_many_rounds_reuses_one_fetch(monkeypatch):
    # End-to-end version of the actual bug: scrubbing through several
    # different rounds of the same season should only hit the expensive
    # fetch once, not once per round.
    calls = []

    def fake_compute(year):
        calls.append(year)
        return _progression()

    monkeypatch.setattr(svc, "_compute_points_progression", fake_compute)
    monkeypatch.setattr(svc, "get_drivers", lambda year: [])
    monkeypatch.setattr(
        svc,
        "get_schedule",
        lambda year: [
            {"round": 1, "format": "conventional", "completed": True},
            {"round": 2, "format": "conventional", "completed": True},
        ],
    )

    svc.get_wdc_calculator(2026, through_round=1)
    svc.get_wdc_calculator(2026, through_round=2)
    svc.get_wdc_calculator(2026, through_round=1)

    assert calls == [2026]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
