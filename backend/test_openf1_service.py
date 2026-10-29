"""Offline provider/route regressions for the public historical fallback."""
from types import SimpleNamespace
import json

import pandas as pd
import pytest
import requests
from fastf1.exceptions import DataNotLoadedError

import fastf1_service as svc
import openf1_service as openf1


START = pd.Timestamp("2024-10-20T19:00:00Z")


def date(seconds):
    return (START + pd.Timedelta(seconds=seconds)).isoformat()


class NativeSession:
    api_path = "/static/test/"
    name = "Race"
    event = {"EventName": "United States Grand Prix", "Country": "United States",
             "Location": "Austin", "Session5": "Race", "Session5DateUtc": START}
    results = pd.DataFrame([
        {"DriverNumber": "12", "Abbreviation": "ANT", "FullName": "Kimi Antonelli",
         "DriverId": "antonelli", "TeamId": "mercedes", "TeamName": "Mercedes",
         "TeamColor": "27F4D2", "Position": 1, "ClassifiedPosition": "1", "Status": "Finished"},
        {"DriverNumber": "63", "Abbreviation": "RUS", "FullName": "George Russell",
         "DriverId": "russell", "TeamId": "mercedes", "TeamName": "Mercedes",
         "TeamColor": "27F4D2", "Position": 2, "ClassifiedPosition": "2", "Status": "Finished"},
    ])

    def load(self, **kwargs):
        pass

    def __getattr__(self, name):
        if name in ("laps", "race_control_messages", "weather_data", "car_data", "pos_data"):
            raise DataNotLoadedError(name)
        raise AttributeError(name)


@pytest.fixture
def feeds():
    return {
        "sessions": [{"session_key": 11396, "meeting_key": 1297, "session_name": "Race",
                      "date_start": date(0), "date_end": date(7200)}],
        "drivers": [{"driver_number": 12, "name_acronym": "ANT", "full_name": "Kimi Antonelli",
                     "team_name": "Mercedes", "team_colour": "27F4D2"},
                    {"driver_number": 63, "name_acronym": "RUS", "full_name": "George Russell",
                     "team_name": "Mercedes", "team_colour": "27F4D2"}],
        "laps": [dict(driver_number=number, lap_number=lap, date_start=date(start),
                      lap_duration=duration, duration_sector_1=30,
                      duration_sector_2=30, duration_sector_3=duration-60,
                      is_pit_out_lap=False, i1_speed=280, i2_speed=290, st_speed=310)
                 for number, lap, start, duration in [(12,1,0,90), (12,2,90,85),
                                                       (63,1,0,92), (63,2,92,90)]],
        "stints": [dict(driver_number=number, stint_number=stint, lap_start=lap,
                        lap_end=lap, compound=compound, tyre_age_at_start=0)
                   for number in (12,63) for stint,lap,compound in ((1,1,"MEDIUM"),(2,2,"HARD"))],
        "position": [dict(driver_number=number, date=date(0), position=position)
                     for number,position in ((12,1),(63,2))],
        "race_control": [dict(date=date(10), category="Flag", flag="YELLOW", scope="Track",
                              lap_number=1, message="YELLOW FLAG"),
                         dict(date=date(25.123), category="Flag", flag="GREEN", scope="Track",
                              lap_number=1, message="GREEN FLAG"),
                         dict(date=date(150), category="Other", driver_number=12, lap_number=2,
                              message="5 SECOND TIME PENALTY FOR CAR 12 (ANT) - TRACK LIMITS"),
                         dict(date=date(200), category="Other", driver_number=12,
                              message="CAR 12 (ANT) LAP TIME 1:25.000 DELETED - TRACK LIMITS")],
        "weather": [dict(date=date(0), air_temperature=25, track_temperature=35,
                         humidity=40, pressure=1000, wind_speed=2, rainfall=0),
                    dict(date=date(60), air_temperature=24, track_temperature=34,
                         humidity=41, pressure=1001, wind_speed=3, rainfall=1)],
        "car_data": [dict(date=date(t), speed=180, throttle=90, brake=100 if t==30 else 0,
                          rpm=10000, n_gear=6, drs=12) for t in range(0,91,10)],
        "location": [dict(date=date(t), x=t*10, y=t*5, z=t) for t in range(0,91,10)],
    }


@pytest.fixture
def provider(monkeypatch, feeds):
    calls = []

    def get(endpoint, **params):
        calls.append((endpoint, params))
        data = feeds[endpoint]
        if isinstance(data, Exception):
            raise data
        return data

    monkeypatch.setattr(openf1, "_get", get)
    monkeypatch.setattr(svc.fastf1, "get_session", lambda *args: NativeSession())
    monkeypatch.setattr(svc, "_SESSION_CACHE", {})
    monkeypatch.setattr(svc, "_SESSION_ORDER", [])
    monkeypatch.setenv("OPENF1_ENABLED", "true")
    return calls


def test_existing_endpoints_restored_by_shared_loader(provider):
    laps = svc.get_lap_times(2024, 19)
    assert laps["totalLaps"] == 2
    assert len(laps["drivers"]) == 2
    ant = next(d for d in laps["drivers"] if d["code"] == "ANT")
    assert ant["driverId"] == "antonelli"
    assert ant["laps"][0] == {"lap":1, "timeMs":90000, "compound":"MEDIUM"}
    strategy = svc.get_strategy(2024, 19)
    assert strategy["drivers"][0]["pitStops"] == 1
    assert strategy["drivers"][0]["stints"][1]["compound"] == "HARD"
    assert len(svc.get_race_control(2024, 19)["messages"]) == 4
    flags = svc.get_flags(2024, 19)
    assert len(flags["events"]) == 2
    assert flags["periods"][0]["type"] == "YELLOW"
    penalty = svc.get_penalties(2024, 19)["penalties"][0]
    assert penalty["driverCode"] == "ANT"
    assert penalty["value"] == "5 seconds"
    weather = svc.get_weather(2024, 19)
    assert weather["available"] is True
    assert weather["rainfall"] is True
    assert [p["timeSeconds"] for p in weather["timeline"]] == [0,60]
    replay = svc.get_race_replay(2024, 19)
    assert replay["frames"][0]["order"][0]["driver"] == "ANT"
    assert replay["frames"][0]["order"][1]["gapMs"] == 2000
    # Scheduling identity is derived from date/type; the API never sees rnd=19.
    assert all("round" not in query for _,query in provider)
    for payload in (laps, strategy, flags, weather, replay):
        json.dumps(payload, allow_nan=False)


def test_lap_objects_and_lazy_telemetry_remain_compatible(provider):
    session = svc._load_session(2024, 19, "R", True, True)
    assert isinstance(session, openf1.OpenF1Session)
    assert not any(endpoint in ("car_data","location") for endpoint,_ in provider)
    lap = session.laps.pick_drivers("ANT").pick_fastest()
    assert isinstance(lap, openf1.OpenF1Lap)
    # The quicker second lap was deleted; pick_fastest must select lap one.
    assert lap["LapNumber"] == 1
    trace = svc.get_telemetry(2024, 19, "ANT")
    assert trace["available"] is True
    assert trace["trace"]["lapNumber"] == 1
    assert trace["trace"]["distance"][-1] == 4500
    assert set(trace["trace"]["brake"]) <= {0,1}
    for endpoint, query in provider:
        if endpoint in ("car_data", "location"):
            assert query["driver_number"] == 12
            assert "date>" in query and "date<" in query
            assert query["date>"].endswith("Z")
            assert query["date<"].endswith("Z")


def test_date_filters_encode_like_the_working_curl_request(provider):
    session = svc._load_session(2024,19,"R",True,True)
    svc.get_telemetry(2024,19,"ANT")
    query = next(params for endpoint,params in provider if endpoint == "car_data")
    url = requests.Request("GET", "https://api.openf1.org/v1/car_data", params=query).prepare().url
    # '=' is the query separator, not part of the comparison key. Including
    # it in the key creates date%3E%3D=... and OpenF1 rejects that filter.
    assert "date%3E=" in url and "date%3C=" in url
    assert "date%3E%3D=" not in url and "date%3C%3D=" not in url


@pytest.mark.parametrize("missing", [False, True])
def test_2026_null_or_missing_drs_does_not_discard_telemetry(provider, feeds, missing):
    for row in feeds["car_data"]:
        if missing:
            row.pop("drs")
        else:
            row["drs"] = None
    result = svc.get_telemetry(2024,19,"ANT")
    assert result["available"] is True
    assert result["trace"]["drsAvailable"] is False
    assert set(result["trace"]["drs"]) == {0}
    assert len(result["trace"]["speed"]) >= 200


def test_telemetry_failure_does_not_break_other_feeds(provider, feeds):
    feeds["car_data"] = openf1.OpenF1Unavailable("HTTP 500")
    assert svc.get_telemetry(2024, 19, "ANT")["available"] is False
    assert len(svc.get_lap_times(2024, 19)["drivers"]) == 2
    assert len(svc.get_penalties(2024, 19)["penalties"]) == 1


def test_replay_positions_use_one_trace_per_driver(provider):
    payload = svc.get_replay_positions(2024, 19)
    assert payload["totalLaps"] == 2
    assert payload["laps"][0]["positions"]["ANT"]
    assert len([1 for endpoint,_ in provider if endpoint == "location"]) == 2


def test_available_native_session_does_not_call_openf1(monkeypatch):
    native = SimpleNamespace(laps=[1], results=[1], load=lambda **kwargs: None)
    monkeypatch.setattr(svc.fastf1, "get_session", lambda *args: native)
    monkeypatch.setattr(svc, "_SESSION_CACHE", {})
    monkeypatch.setattr(svc, "_SESSION_ORDER", [])
    monkeypatch.setattr(openf1, "_get", lambda *args, **kwargs: pytest.fail("unexpected fallback"))
    assert svc._load_session(2024, 19, "R", True) is native


def test_native_feed_and_identifiers_preserved(provider):
    base = NativeSession()
    base.race_control_messages = pd.DataFrame([{"Message": "NATIVE"}])
    session = openf1.supplement(base, 2024, "R", laps=True, messages=True)
    assert session.race_control_messages is base.race_control_messages
    assert session.get_driver("ANT")["DriverId"] == "antonelli"


@pytest.mark.parametrize("mode", ["future", "cancelled", "ambiguous", "wrong_type"])
def test_session_resolution_never_guesses(provider, feeds, mode):
    row = feeds["sessions"][0]
    if mode == "future":
        row["date_end"] = "2099-01-01T00:00:00Z"
    elif mode == "cancelled":
        row["is_cancelled"] = True
    elif mode == "ambiguous":
        feeds["sessions"].append(dict(row, session_key=999))
    else:
        row["session_name"] = "Qualifying"
    base = NativeSession()
    assert openf1.supplement(base, 2024, "R", laps=True) is base
    assert [endpoint for endpoint,_ in provider] == ["sessions"]


@pytest.mark.parametrize("year,enabled", [(2022,"true"),(2024,"false")])
def test_unsupported_or_disabled_fallback_makes_no_requests(monkeypatch, year, enabled):
    monkeypatch.setenv("OPENF1_ENABLED", enabled)
    monkeypatch.setattr(openf1,"_get",lambda *args, **kwargs: pytest.fail("unexpected request"))
    base = NativeSession()
    assert openf1.supplement(base, year, "R", laps=True) is base


def test_failed_optional_feeds_are_not_invented(provider, feeds):
    feeds["stints"] = openf1.OpenF1Unavailable("503")
    feeds["position"] = openf1.OpenF1Unavailable("503")
    assert svc.get_strategy(2024, 19)["available"] is False
    assert len(svc.get_lap_times(2024, 19)["drivers"]) == 2
    assert svc.get_race_replay(2024,19)["available"] is False


def test_failed_optional_feed_recovers_after_session_backoff(provider, feeds):
    stints = feeds["stints"]
    feeds["stints"] = openf1.OpenF1Unavailable("503")
    assert svc.get_strategy(2024, 19)["available"] is False
    session = svc._load_session(2024, 19, "R", True)
    session.expires_at = 0
    feeds["stints"] = stints
    assert svc.get_strategy(2024, 19)["drivers"][0]["stints"]


def test_fastf1_load_exception_can_recover(provider, monkeypatch):
    def fail(self, **kwargs):
        raise DataNotLoadedError("upstream")
    monkeypatch.setattr(NativeSession, "load", fail)
    assert svc.get_lap_times(2024,19)["totalLaps"] == 2


def test_sprint_qualifying_classification_comes_from_openf1(provider, feeds, monkeypatch):
    base = NativeSession()
    base.event = dict(base.event, Session5="Sprint Qualifying")
    base.results = base.results.copy()
    base.results["Position"] = None
    feeds["sessions"][0]["session_name"] = "Sprint Qualifying"
    feeds["session_result"] = [dict(driver_number=12, position=1, duration=[91,90,89]),
                               dict(driver_number=63, position=2, duration=[92,91,90])]
    monkeypatch.setattr(svc.fastf1, "get_session", lambda *args: base)
    result = svc.get_results(2024,19,"SQ")
    ant = result["results"][0]
    assert ant["position"] == 1
    assert ant["q3"] == "1:29.000"
    assert ant["driverId"] == "antonelli"
    json.dumps(result, allow_nan=False)


def test_partial_native_feed_is_not_replaced_when_other_feed_missing(provider):
    base = NativeSession()
    native_laps = pd.DataFrame([{"DriverNumber":"12", "LapNumber":1}])
    base.laps = native_laps
    session = openf1.supplement(base,2024,"R",laps=True,weather=True)
    assert session.laps is native_laps
    assert len(session.weather_data) == 2


def test_empty_loaded_message_log_is_preserved(provider):
    base = NativeSession()
    base.race_control_messages = pd.DataFrame()
    session = openf1.supplement(base,2024,"R",laps=True,messages=True)
    assert session.race_control_messages is base.race_control_messages


def test_http_cache_validation_and_recovery(monkeypatch):
    monkeypatch.setattr(openf1, "_cache", openf1.OrderedDict())
    monkeypatch.setattr(openf1, "_failures", openf1.OrderedDict())
    monkeypatch.setattr(openf1, "_last_request", -10)
    clock = [100.0]
    monkeypatch.setattr(openf1.time, "monotonic", lambda: clock[0])
    monkeypatch.setattr(openf1.time, "sleep", lambda delay: None)
    calls = []
    replies = iter([requests.HTTPError("429"), {"error":"unexpected"}, [], [{"lap_number":1}]])

    def get(*args, **kwargs):
        calls.append(kwargs)
        value = next(replies)
        if isinstance(value, Exception):
            raise value
        return SimpleNamespace(raise_for_status=lambda:None, json=lambda:value, status_code=200)

    monkeypatch.setattr(openf1.requests, "get", get)
    with pytest.raises(openf1.OpenF1Unavailable):
        openf1._get("laps",session_key=1)
    with pytest.raises(openf1.OpenF1Unavailable):
        openf1._get("laps",session_key=1)
    assert len(calls) == 1
    clock[0] += 31
    with pytest.raises(openf1.OpenF1Unavailable):
        openf1._get("laps",session_key=1)
    clock[0] += 31
    assert openf1._get("laps",session_key=1) == []
    assert openf1._get("laps",session_key=1) == [{"lap_number":1}]
    assert openf1._get("laps",session_key=1) == [{"lap_number":1}]
    assert len(calls) == 4
    assert all("timeout" in kwargs for kwargs in calls)
