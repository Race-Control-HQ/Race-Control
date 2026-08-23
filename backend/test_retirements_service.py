"""
Offline tests for the retirements endpoint (`fastf1_service.get_retirements`
/ `_is_finish_status`).

Regression coverage for a real bug: classified-but-lapped finishers were
showing up as "retirements" because the old logic only trusted the free-text
`Status` column (expected to start with "+", e.g. "+ 1 Lap"), which isn't
consistently formatted across FastF1's own timing data vs. its Ergast/Jolpica
fallback. `ClassifiedPosition` (a plain integer for any officially classified
finisher, lapped or not) is the authoritative signal and must win.

    python test_retirements_service.py     (or: pytest test_retirements_service.py)
"""

from types import SimpleNamespace

import pandas as pd

import fastf1_service as svc


def _results(rows):
    return pd.DataFrame(rows)


def _stub_session(rows, laps_by_number=None):
    laps_rows = []
    for num, lap_no in (laps_by_number or {}).items():
        for n in range(1, lap_no + 1):
            laps_rows.append({"DriverNumber": num, "LapNumber": float(n)})
    laps = pd.DataFrame(laps_rows, columns=["DriverNumber", "LapNumber"])
    return SimpleNamespace(
        event={"EventName": "Test Grand Prix"},
        results=_results(rows),
        laps=laps,
    )


def _row(abbr, status, classified_position, number=None):
    return {
        "Abbreviation": abbr,
        "FullName": abbr,
        "DriverId": abbr.lower(),
        "DriverNumber": number or abbr,
        "TeamName": "Test Team",
        "TeamColor": "FF0000",
        "Status": status,
        "ClassifiedPosition": classified_position,
    }


def test_is_finish_status_trusts_classified_position_over_status_text():
    # Numeric ClassifiedPosition => finisher, regardless of odd/blank Status text.
    assert svc._is_finish_status("", "14") is True
    assert svc._is_finish_status("Lapped", "18") is True
    assert svc._is_finish_status("Running", "3") is True
    # Standard finish text still works without ClassifiedPosition.
    assert svc._is_finish_status("Finished", None) is True
    assert svc._is_finish_status("+ 1 Lap", None) is True
    assert svc._is_finish_status("+2 Laps", None) is True
    # Non-numeric classification codes / DNF-style text are not finishes.
    assert svc._is_finish_status("Accident", "R") is False
    assert svc._is_finish_status("Gearbox", "R") is False
    assert svc._is_finish_status("Disqualified", "D") is False
    assert svc._is_finish_status("", "") is False
    assert svc._is_finish_status("", None) is False


def test_lapped_classified_finishers_excluded_from_retirements(monkeypatch):
    rows = [
        _row("VER", "Finished", "1"),
        _row("HAM", "+ 1 Lap", "14"),         # lapped but classified, not a retirement
        _row("LEC", "Lapped", "18"),          # odd status text, still classified, not a retirement
        _row("PER", "Accident", "R"),         # genuine retirement
        _row("SAI", "Gearbox", "R"),          # genuine retirement
        _row("ALO", "Disqualified", "D"),     # genuine retirement (DSQ)
    ]
    session = _stub_session(rows)
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_retirements(2024, 1)
    codes = {r["driver"] for r in out["retirements"]}
    assert codes == {"PER", "SAI", "ALO"}
    assert "HAM" not in codes
    assert "LEC" not in codes


def test_retirements_include_laps_completed(monkeypatch):
    rows = [
        _row("PER", "Accident", "R", number="11"),
        _row("SAI", "Gearbox", "R", number="55"),
        _row("VER", "Finished", "1", number="1"),
    ]
    session = _stub_session(rows, laps_by_number={"11": 23, "55": 40, "1": 58})
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_retirements(2024, 1)
    by_driver = {r["driver"]: r["lapsCompleted"] for r in out["retirements"]}
    assert by_driver == {"PER": 23, "SAI": 40}


def test_laps_completed_absent_when_lap_data_unavailable(monkeypatch):
    # No laps dataframe entries for these drivers; should degrade to None
    # rather than erroring or fabricating a number.
    rows = [_row("PER", "Accident", "R", number="11")]
    session = _stub_session(rows)  # no laps_by_number given
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_retirements(2024, 1)
    assert out["retirements"][0]["lapsCompleted"] is None


def test_no_retirements_when_all_classified(monkeypatch):
    rows = [_row("VER", "Finished", "1"), _row("HAM", "+ 1 Lap", "2")]
    session = _stub_session(rows)
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_retirements(2024, 1)
    assert out["retirements"] == []


def test_retiree_with_ambiguous_lapped_status_is_relabeled(monkeypatch):
    # A genuine retiree (ClassifiedPosition "R") whose Status text is the
    # ambiguous "Lapped" (last on-track state, not a DNF reason) should be
    # displayed as "Retired" so it doesn't read like a finishing description.
    rows = [
        _row("STR", "Lapped", "R"),
        _row("SAI", "Gearbox", "R"),  # real reason text left untouched
    ]
    session = _stub_session(rows)
    monkeypatch.setattr(svc, "_load_session", lambda *a, **k: session)

    out = svc.get_retirements(2024, 1)
    by_driver = {r["driver"]: r["status"] for r in out["retirements"]}
    assert by_driver["STR"] == "Retired"
    assert by_driver["SAI"] == "Gearbox"


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
