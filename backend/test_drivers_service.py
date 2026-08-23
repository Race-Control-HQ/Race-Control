"""
Offline tests for `fastf1_service.get_drivers`, specifically which source it
trusts for a driver's team id when building `teamLogoUrl`.

Regression: the WDC calculator page (and anywhere else reusing `get_drivers`)
was showing missing logos for some drivers. Root cause: `get_drivers` picked
Ergast/Jolpica's `constructorId` (from `get_driver_standings`) over FastF1's
own `TeamId` (from `_season_reference_results`) when both were present.
Ergast's constructor ids lag for brand-new or renamed entrants (e.g. a new
season's entrant, or a team that rebranded), so they don't reliably match the
slugs in `_TEAM_LOGO_SLUGS` the way FastF1's ids do. Every other endpoint in
`fastf1_service.py` already sources its team id from FastF1 for exactly this
reason, so `get_drivers` should too.

    python test_drivers_service.py     (or: pytest test_drivers_service.py)
"""

import fastf1_service as svc


def _standings_with_stale_ergast_team_id():
    # Ergast/Jolpica still reports this driver under the team's old id.
    return [
        {"driverId": "hulkenberg", "givenName": "Nico", "familyName": "Hulkenberg",
         "driverCode": "HUL", "driverNumber": "27", "nationality": "German",
         "dateOfBirth": "1987-08-19", "teamName": "Sauber", "teamId": "sauber",
         "position": 15, "points": 20, "wins": 0},
    ]


def _reference_results_with_current_fastf1_team_id():
    # FastF1's own results for the season already reflect the rebrand.
    return {
        "hulkenberg": {
            "headshotUrl": None,
            "teamColor": None,
            "teamId": "audi",
            "abbreviation": "HUL",
            "driverNumber": "27",
            "countryCode": "DEU",
        }
    }


def test_current_fastf1_team_id_wins_over_stale_ergast_team_id(monkeypatch):
    monkeypatch.setattr(svc, "get_driver_standings", lambda year: _standings_with_stale_ergast_team_id())
    # _season_reference_results normally returns a pandas DataFrame that
    # get_drivers iterates with .iterrows(); a minimal fake with that same
    # shape avoids pulling pandas/FastF1 into this offline test.
    fake_ref = _FakeDataFrame(_reference_results_with_current_fastf1_team_id())
    monkeypatch.setattr(svc, "_season_reference_results", lambda year: fake_ref)

    drivers = svc.get_drivers(2026)

    assert len(drivers) == 1
    driver = drivers[0]
    assert driver["teamId"] == "audi"
    assert driver["teamLogoUrl"] is not None
    assert "audi" in driver["teamLogoUrl"]


def test_falls_back_to_ergast_team_id_when_no_reference_results(monkeypatch):
    # Early in a season, before any race has completed, there's no FastF1
    # reference data yet; Ergast's id is the only thing available.
    monkeypatch.setattr(svc, "get_driver_standings", lambda year: _standings_with_stale_ergast_team_id())
    monkeypatch.setattr(svc, "_season_reference_results", lambda year: None)

    drivers = svc.get_drivers(2026)

    assert len(drivers) == 1
    assert drivers[0]["teamId"] == "sauber"
    # "sauber" isn't a known logo slug, so this correctly comes back None
    # rather than a broken image URL, not the bug under test here.
    assert drivers[0]["teamLogoUrl"] is None


class _FakeRow(dict):
    def get(self, key, default=None):
        return dict.get(self, key, default)


class _FakeDataFrame:
    """Minimal stand-in for the pandas DataFrame `_season_reference_results`
    normally returns: just enough surface (`.iterrows()`) for `get_drivers`
    to consume, keyed by driverId with FastF1-shaped column names."""

    def __init__(self, meta_by_driver_id: dict):
        self._rows = [
            _FakeRow({
                "DriverId": driver_id,
                "HeadshotUrl": meta.get("headshotUrl"),
                "TeamColor": meta.get("teamColor"),
                "TeamId": meta.get("teamId"),
                "Abbreviation": meta.get("abbreviation"),
                "DriverNumber": meta.get("driverNumber"),
                "CountryCode": meta.get("countryCode"),
            })
            for driver_id, meta in meta_by_driver_id.items()
        ]

    def iterrows(self):
        for i, row in enumerate(self._rows):
            yield i, row


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
