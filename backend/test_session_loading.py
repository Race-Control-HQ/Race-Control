"""Incomplete FastF1 loads must remain retryable even when results exist."""

from types import SimpleNamespace

import pandas as pd
import pytest
from fastf1.exceptions import DataNotLoadedError

import fastf1_service as svc


class PartialSession:
    event = {"EventName": "United States Grand Prix"}
    results = pd.DataFrame([{"Abbreviation": "ANT", "DriverNumber": "12"}])

    @property
    def laps(self):
        raise DataNotLoadedError("Timing feed unavailable")

    @property
    def race_control_messages(self):
        raise DataNotLoadedError("Message feed unavailable")

    def load(self, **kwargs):
        pass


@pytest.fixture
def session_cache(monkeypatch):
    monkeypatch.setattr(svc, "_SESSION_CACHE", {})
    monkeypatch.setattr(svc, "_SESSION_ORDER", [])


def test_partial_load_retries_and_caches_recovered_session(monkeypatch, session_cache):
    partial = PartialSession()
    complete = SimpleNamespace(
        laps=pd.DataFrame([{"LapNumber": 1}]),
        car_data={"12": pd.DataFrame([{"Speed": 200}])},
        pos_data={"12": pd.DataFrame([{"X": 100}])},
        load=lambda **kwargs: None,
    )
    sessions = iter([partial, complete])
    monkeypatch.setattr(svc.fastf1, "get_session", lambda *args: next(sessions))

    assert svc._load_session(2026, 18, "R", True, True) is partial
    assert not svc._SESSION_CACHE
    assert svc._load_session(2026, 18, "R", True, True) is complete
    assert svc._load_session(2026, 18, "R", True, True) is complete


@pytest.mark.parametrize("missing", ["laps", "car_data", "pos_data",
                                     "race_control_messages", "weather_data"])
def test_each_requested_feed_is_required_for_caching(missing):
    fields = dict(results=PartialSession.results,
                  laps=pd.DataFrame([{"LapNumber": 1}]),
                  car_data={"12": [1]}, pos_data={"12": [1]},
                  race_control_messages=pd.DataFrame(),
                  weather_data=pd.DataFrame([{"AirTemp": 25}]))
    fields.pop(missing)
    assert not svc._session_has_data(SimpleNamespace(**fields), True, True, True, True)


def test_empty_loaded_message_log_is_valid():
    session = SimpleNamespace(laps=[1], race_control_messages=pd.DataFrame())
    assert svc._session_has_data(session, True, messages_requested=True)


def test_results_only_load_still_works():
    assert svc._session_has_data(PartialSession(), False)
    assert not svc._session_has_data(PartialSession(), True)


def test_forced_failed_refresh_evicts_previous_session(monkeypatch, session_cache):
    complete = SimpleNamespace(laps=[1], load=lambda **kwargs: None)
    sessions = iter([complete, complete, PartialSession(), complete])
    monkeypatch.setattr(svc.fastf1, "get_session", lambda *args: next(sessions))
    svc._load_session(2026, 18, "R", True)
    svc._load_session(2026, 18, "R", True, force=True)
    assert len(svc._SESSION_ORDER) == 1
    svc._load_session(2026, 18, "R", True, force=True)
    assert not svc._SESSION_CACHE
    assert not svc._SESSION_ORDER
    assert svc._load_session(2026, 18, "R", True) is complete


def test_telemetry_reports_unavailable_when_laps_failed(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *args, **kwargs: PartialSession())
    assert svc.get_telemetry(2026, 18, "ANT")["available"] is False
    assert svc.get_telemetry_compare(2026, 18, "ANT", "RUS")["available"] is False


def test_telemetry_reports_unavailable_when_trace_failed():
    class Lap(dict):
        def get_telemetry(self):
            raise DataNotLoadedError("Car data unavailable")

    class Laps:
        def pick_drivers(self, code):
            return self

        def __len__(self):
            return 1

        def pick_fastest(self):
            return Lap()

    assert svc._lap_telemetry(SimpleNamespace(laps=Laps()), "ANT") is None


def test_penalties_distinguish_failed_load_from_no_penalties(monkeypatch):
    monkeypatch.setattr(svc, "_load_session", lambda *args, **kwargs: PartialSession())
    out = svc.get_penalties(2026, 18)
    assert out["penalties"] == []
    assert out["available"] is False
