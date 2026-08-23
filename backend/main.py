"""
RaceControl API
---------------
A thin FastAPI wrapper over the FastF1 library that serves historical Formula 1
data (2018-present) as JSON for the RaceControl iOS app.

Run locally:
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000 --reload

Interactive docs once running:  http://localhost:8000/docs
"""

from __future__ import annotations

import logging
import os
import secrets
import time
from typing import Any, Callable, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel

import analytics_service as analytics
import fastf1_service as svc

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("racecontrol")

CACHE_DIR = os.environ.get(
    "FASTF1_CACHE",
    os.path.join(os.path.dirname(__file__), ".fastf1_cache"),
)
os.makedirs(CACHE_DIR, exist_ok=True)
svc.configure_cache(CACHE_DIR)

# This process's response cache (`cached()` below), FastF1 session cache
# (fastf1_service._SESSION_CACHE), and points-progression cache
# (fastf1_service._progression_cache) are plain in-process dicts with no
# cross-process sharing. That is fine — and cheap — at WEB_CONCURRENCY=1,
# but a second uvicorn worker gets its own independent copy of all three
# caches: memory is duplicated, hit rates fall, and a request can observe
# different cached state depending on which worker answers it. Rather than
# let that happen silently, fail loudly at startup so a scale-out attempt is
# caught immediately instead of showing up later as unexplained inconsistency.
_WEB_CONCURRENCY = int(os.environ.get("WEB_CONCURRENCY", "1"))
if _WEB_CONCURRENCY > 1:
    raise RuntimeError(
        f"WEB_CONCURRENCY={_WEB_CONCURRENCY}, but main.py's in-process caches "
        "are only correct under a single worker. Either run with "
        "WEB_CONCURRENCY=1 (the supported deployment today) or externalize "
        "the response/session/points-progression caches to a shared store "
        "before scaling out."
    )

app = FastAPI(
    title="RaceControl API",
    version="1.0.0",
    description="Historical Formula 1 data (2018+) powered by FastF1.",
)

# The iOS and Android apps are native clients (no browser Origin header), so
# CORS is only relevant to a hypothetical direct-from-browser caller. The web
# client (RaceControlWeb) already proxies through its own Next.js server
# (see RaceControlWeb/src/app/api/proxy/[...path]/route.ts), so it never
# issues a cross-origin request to this API either. Default to allowing no
# browser origins at all; set ALLOWED_ORIGINS (comma-separated) if a genuine
# browser-facing origin is ever added.
_allowed_origins = [
    origin.strip()
    for origin in os.environ.get("ALLOWED_ORIGINS", "").split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_methods=["GET"],
    allow_headers=["*"],
)

# --------------------------------------------------------------------------- #
#  Authentication
# --------------------------------------------------------------------------- #
# Three independent mechanisms, any one of which authorises an /api request:
#
#   1. App Attest (APP_ATTEST_ENABLED): the published iOS app proves it is a
#      genuine copy of our app on a real Apple device and receives a short-lived
#      JWT. This is the mechanism for App Store distribution: no user key.
#
#   2. Play Integrity (PLAY_INTEGRITY_ENABLED), the Android counterpart: the
#      published Android app proves (via Google Play services) that it is a
#      genuine, unmodified copy of our app on a genuine device, installed
#      through Play, and receives a short-lived JWT the same shape as #1's.
#      This is the mechanism for Play Store distribution: no user key, and no
#      secret embedded in the APK for someone to extract with a decompiler.
#
#   3. API_TOKEN: a shared secret, useful as an admin/break-glass credential
#      and for `curl` during development.
#
# If NONE are configured the API is fully open, so `./run.sh` works unchanged.
# These are additive, not exclusive: enabling Play Integrity for the Android
# rollout does not require or affect the App Attest configuration, and vice
# versa; a request is authorised if it satisfies *any* enabled mechanism.
import attest  # noqa: E402
import playintegrity  # noqa: E402

API_TOKEN = os.environ.get("API_TOKEN", "").strip()

_attest_config = attest.AttestConfig.from_env()
attest_verifier: attest.AppAttestVerifier | None = (
    attest.AppAttestVerifier(_attest_config) if _attest_config.enabled else None
)

_play_integrity_config = playintegrity.PlayIntegrityConfig.from_env()
play_integrity_verifier: playintegrity.PlayIntegrityVerifier | None = (
    playintegrity.PlayIntegrityVerifier(_play_integrity_config)
    if _play_integrity_config.enabled else None
)

# Paths reachable without any credential: health probe, docs, and the App
# Attest / Play Integrity bootstrap endpoints (each gated by the attestation
# itself, not by _is_authorized).
_OPEN_PATHS = {
    "/", "/api/health", "/docs", "/openapi.json", "/redoc",
    "/attest/challenge", "/attest/verify", "/attest/token", "/attest/status",
    "/playintegrity/challenge", "/playintegrity/verify", "/playintegrity/status",
}

if attest_verifier:
    log.info("App Attest auth is ENABLED (app_id=%s, production=%s)",
             _attest_config.app_id, _attest_config.production)
if play_integrity_verifier:
    log.info("Play Integrity auth is ENABLED (package=%s)",
             _play_integrity_config.package_name)
if API_TOKEN:
    log.info("Shared-secret API_TOKEN auth is ENABLED")
if not attest_verifier and not play_integrity_verifier and not API_TOKEN:
    log.warning("No auth configured: API is open (fine for local dev)")


def _is_authorized(request: Request) -> bool:
    header = request.headers.get("authorization", "")
    # Admin / dev shared secret.
    if API_TOKEN and secrets.compare_digest(header, f"Bearer {API_TOKEN}"):
        return True
    # App-issued JWT (from App Attest or Play Integrity, same JWT shape,
    # checked against whichever mechanisms are enabled).
    if header.startswith("Bearer "):
        token = header[len("Bearer "):]
        if attest_verifier and attest_verifier.verify_app_token(token):
            return True
        if play_integrity_verifier and play_integrity_verifier.verify_app_token(token):
            return True
    # No auth configured at all → open.
    return not attest_verifier and not play_integrity_verifier and not API_TOKEN


@app.middleware("http")
async def authenticate(request: Request, call_next):
    if request.url.path not in _OPEN_PATHS and not _is_authorized(request):
        return JSONResponse(status_code=401, content={"detail": "Unauthorized"})
    return await call_next(request)


# --------------------------------------------------------------------------- #
#  Per-IP rate limiting (defence in depth against abuse of a public endpoint)
# --------------------------------------------------------------------------- #
_RATE_LIMIT = int(os.environ.get("RATE_LIMIT_PER_MINUTE", 120))
_rate_hits: dict[str, list[float]] = {}
_rate_lock_window = 60.0


def _client_key(request: Request) -> str:
    """The identity to rate-limit on.

    In deployment the app sits behind a reverse proxy (Coolify/Traefik), so
    `request.client.host` is the *proxy's* address and is identical for every
    caller — keying on it turns a per-IP limit into a single global throttle
    where one abusive client 429s the entire user base. The real client is the
    leftmost entry of `X-Forwarded-For`.

    Trusting a client-settable header is only safe because the proxy overwrites
    it on ingress; `--forwarded-allow-ips` on the uvicorn command line (see the
    Dockerfile) is what makes that true. With no proxy in front (local
    `./run.sh`), no such header is present and this falls back to the peer
    address, which is then genuinely the client.
    """
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        # "client, proxy1, proxy2" — the first entry is the origin client.
        first = forwarded.split(",")[0].strip()
        if first:
            return first
    return request.client.host if request.client else "unknown"


@app.middleware("http")
async def rate_limit(request: Request, call_next):
    if _RATE_LIMIT > 0 and request.url.path != "/api/health":
        client = _client_key(request)
        now = time.time()
        hits = [t for t in _rate_hits.get(client, []) if now - t < _rate_lock_window]
        if len(hits) >= _RATE_LIMIT:
            return JSONResponse(status_code=429, content={"detail": "Too many requests"})
        hits.append(now)
        _rate_hits[client] = hits
        # Without this sweep the map retains one entry per IP ever seen: an
        # entry is only pruned when *that* client comes back, which a one-shot
        # scanner never does. Sweep on every write so even a low-volume service
        # eventually releases absent clients rather than waiting for an
        # arbitrary high-water mark.
        for stale in [
            k for k, v in _rate_hits.items()
            if k != client and (not v or now - v[-1] >= _rate_lock_window)
        ]:
            _rate_hits.pop(stale, None)
    return await call_next(request)

# --------------------------------------------------------------------------- #
#  Tiny in-process response cache (FastF1 loads are expensive)
# --------------------------------------------------------------------------- #
_CACHE: dict[str, tuple[float, Any]] = {}
_CACHE_ORDER: list[str] = []
# 6 hours by default; historical data is effectively static.
_TTL_SECONDS = int(os.environ.get("CACHE_TTL_SECONDS", 60 * 60 * 6))
# Bounded for the same reason `SESSION_CACHE_MAX` bounds the session cache in
# fastf1_service: the TTL is only consulted on read, so an entry nobody asks
# for again is retained forever. Keys multiply across year x round x session x
# driver, and some payloads (telemetry, replay positions) are large, on a
# container the README sizes at 1-2 GB.
_CACHE_MAX_ENTRIES = int(os.environ.get("CACHE_MAX_ENTRIES", 256))


def cached(key: str, producer: Callable[[], Any]) -> Any:
    now = time.time()
    hit = _CACHE.get(key)
    if hit and (now - hit[0]) < _TTL_SECONDS:
        # A hit makes this the most recently used key.
        if key in _CACHE_ORDER:
            _CACHE_ORDER.remove(key)
        _CACHE_ORDER.append(key)
        return hit[1]
    value = producer()
    # Recomputing an expired entry also makes it most recently used.
    if key in _CACHE_ORDER:
        _CACHE_ORDER.remove(key)
    _CACHE_ORDER.append(key)
    _CACHE[key] = (now, value)
    while _CACHE_MAX_ENTRIES > 0 and len(_CACHE_ORDER) > _CACHE_MAX_ENTRIES:
        _CACHE.pop(_CACHE_ORDER.pop(0), None)
    return value


def _guard(fn: Callable[[], Any], context: str) -> Any:
    try:
        return fn()
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        log.exception("error in %s", context)
        raise HTTPException(status_code=502, detail=f"{context}: {exc}") from exc


# --------------------------------------------------------------------------- #
#  Routes
# --------------------------------------------------------------------------- #
@app.get("/")
def root() -> dict[str, Any]:
    return {"service": "RaceControl API", "status": "ok", "docs": "/docs"}


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# --------------------------------------------------------------------------- #
#  App Attest bootstrap endpoints
# --------------------------------------------------------------------------- #
class AttestVerifyBody(BaseModel):
    keyId: str
    attestation: str
    challenge: str


class AttestTokenBody(BaseModel):
    keyId: str
    assertion: str
    challenge: str


@app.get("/attest/status")
def attest_status() -> dict[str, Any]:
    """Non-sensitive diagnostics for verifying App Attest setup on a device."""
    if not attest_verifier:
        return {"enabled": False, "adminTokenSet": bool(API_TOKEN)}
    return {**attest_verifier.status(), "adminTokenSet": bool(API_TOKEN)}


@app.get("/attest/challenge")
def attest_challenge() -> dict[str, Any]:
    if not attest_verifier:
        raise HTTPException(status_code=404, detail="App Attest is not enabled")
    return {"challenge": attest_verifier.new_challenge()}


@app.post("/attest/verify")
async def attest_verify(body: AttestVerifyBody) -> dict[str, Any]:
    if not attest_verifier:
        raise HTTPException(status_code=404, detail="App Attest is not enabled")
    try:
        token = await attest_verifier.verify_attestation(
            body.keyId, body.attestation, body.challenge)
    except attest.AttestError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    return {"token": token, "expiresIn": _attest_config.jwt_ttl}


@app.post("/attest/token")
async def attest_token(body: AttestTokenBody) -> dict[str, Any]:
    if not attest_verifier:
        raise HTTPException(status_code=404, detail="App Attest is not enabled")
    try:
        token = await attest_verifier.verify_assertion(
            body.keyId, body.assertion, body.challenge)
    except attest.AttestError as exc:
        # 409 tells the app its key is unknown and it should re-attest.
        status = 409 if "re-attestation" in str(exc).lower() else 401
        raise HTTPException(status_code=status, detail=str(exc)) from exc
    return {"token": token, "expiresIn": _attest_config.jwt_ttl}


# --------------------------------------------------------------------------- #
#  Play Integrity bootstrap endpoints
# --------------------------------------------------------------------------- #
class PlayIntegrityVerifyBody(BaseModel):
    nonce: str
    integrityToken: str


@app.get("/playintegrity/status")
def play_integrity_status() -> dict[str, Any]:
    """Non-sensitive diagnostics for verifying Play Integrity setup on a device."""
    if not play_integrity_verifier:
        return {"enabled": False, "adminTokenSet": bool(API_TOKEN)}
    return {**play_integrity_verifier.status(), "adminTokenSet": bool(API_TOKEN)}


@app.get("/playintegrity/challenge")
def play_integrity_challenge() -> dict[str, Any]:
    if not play_integrity_verifier:
        raise HTTPException(status_code=404, detail="Play Integrity is not enabled")
    return {"nonce": play_integrity_verifier.new_challenge()}


@app.post("/playintegrity/verify")
def play_integrity_verify(body: PlayIntegrityVerifyBody) -> dict[str, Any]:
    if not play_integrity_verifier:
        raise HTTPException(status_code=404, detail="Play Integrity is not enabled")
    try:
        token = play_integrity_verifier.verify_token(body.nonce, body.integrityToken)
    except playintegrity.PlayIntegrityError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc
    return {"token": token, "expiresIn": _play_integrity_config.jwt_ttl}


@app.get("/api/seasons")
def seasons() -> list[int]:
    return svc.get_seasons()


@app.get("/api/schedule/{year}")
def schedule(year: int) -> Any:
    return cached(f"schedule:{year}", lambda: _guard(lambda: svc.get_schedule(year), "schedule"))


@app.get("/api/results/{year}/{rnd}/{session}")
def results(year: int, rnd: int, session: str) -> Any:
    key = f"results:{year}:{rnd}:{session}"
    return cached(key, lambda: _guard(lambda: svc.get_results(year, rnd, session), "results"))


@app.get("/api/standings/drivers/{year}")
def driver_standings(year: int) -> Any:
    key = f"dstand:{year}"
    return cached(key, lambda: _guard(lambda: svc.get_driver_standings(year), "driver standings"))


@app.get("/api/standings/constructors/{year}")
def constructor_standings(year: int) -> Any:
    key = f"cstand:{year}"
    return cached(
        key, lambda: _guard(lambda: svc.get_constructor_standings(year), "constructor standings")
    )


@app.get("/api/wdc-calculator/{year}")
def wdc_calculator(year: int, through_round: Optional[int] = None) -> Any:
    key = f"wdccalc:{year}:{through_round if through_round is not None else 'live'}"
    return cached(
        key,
        lambda: _guard(lambda: svc.get_wdc_calculator(year, through_round=through_round), "wdc calculator"),
    )


@app.get("/api/drivers/{year}")
def drivers(year: int) -> Any:
    return cached(f"drivers:{year}", lambda: _guard(lambda: svc.get_drivers(year), "drivers"))


@app.get("/api/drivers/{year}/{driver_id}")
def driver_detail(year: int, driver_id: str) -> Any:
    key = f"driver:{year}:{driver_id}"
    return cached(key, lambda: _guard(lambda: svc.get_driver_detail(year, driver_id), "driver"))


@app.get("/api/teams/{year}")
def teams(year: int) -> Any:
    return cached(f"teams:{year}", lambda: _guard(lambda: svc.get_teams(year), "teams"))


@app.get("/api/teams/{year}/{team_id}")
def team_detail(year: int, team_id: str) -> Any:
    key = f"team:{year}:{team_id}"
    return cached(key, lambda: _guard(lambda: svc.get_team_detail(year, team_id), "team"))


@app.get("/api/circuits/{year}")
def circuits(year: int) -> Any:
    return cached(f"circuits:{year}", lambda: _guard(lambda: svc.get_circuits(year), "circuits"))


@app.get("/api/circuit/{year}/{rnd}")
def circuit_map(year: int, rnd: int) -> Any:
    key = f"circuitmap:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_circuit_map(year, rnd), "circuit map"))


@app.get("/api/replay/{year}/{rnd}")
def replay(year: int, rnd: int) -> Any:
    key = f"replay:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_race_replay(year, rnd), "replay"))


@app.get("/api/replay-positions/{year}/{rnd}")
def replay_positions(year: int, rnd: int) -> Any:
    key = f"replaypos:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_replay_positions(year, rnd), "replay positions"))


@app.get("/api/laptimes/{year}/{rnd}")
def laptimes(year: int, rnd: int) -> Any:
    key = f"laptimes:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_lap_times(year, rnd), "lap times"))


@app.get("/api/strategy/{year}/{rnd}")
def strategy(year: int, rnd: int) -> Any:
    key = f"strategy:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_strategy(year, rnd), "strategy"))


@app.get("/api/weather/{year}/{rnd}/{session}")
def weather(year: int, rnd: int, session: str) -> Any:
    key = f"weather:{year}:{rnd}:{session}"
    return cached(key, lambda: _guard(lambda: svc.get_weather(year, rnd, session), "weather"))


@app.get("/api/flags/{year}/{rnd}")
def flags(year: int, rnd: int, session: str = "R") -> Any:
    key = f"flags:{year}:{rnd}:{session}"
    return cached(key, lambda: _guard(lambda: svc.get_flags(year, rnd, session), "flags"))


@app.get("/api/racecontrol/{year}/{rnd}")
def race_control(year: int, rnd: int, session: str = "R") -> Any:
    key = f"racecontrol:{year}:{rnd}:{session}"
    return cached(key, lambda: _guard(lambda: svc.get_race_control(year, rnd, session), "race control"))


@app.get("/api/penalties/{year}/{rnd}")
def penalties(year: int, rnd: int, session: str = "R") -> Any:
    key = f"penalties:{year}:{rnd}:{session}"
    return cached(key, lambda: _guard(lambda: svc.get_penalties(year, rnd, session), "penalties"))


@app.get("/api/racedrivers/{year}/{rnd}")
def race_drivers(year: int, rnd: int) -> Any:
    key = f"racedrivers:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.list_race_drivers(year, rnd), "race drivers"))


@app.get("/api/telemetry/{year}/{rnd}/{driver}")
def telemetry(year: int, rnd: int, driver: str, lap: str = "fastest") -> Any:
    key = f"tel:{year}:{rnd}:{driver}:{lap}"
    return cached(key, lambda: _guard(lambda: svc.get_telemetry(year, rnd, driver, lap), "telemetry"))


@app.get("/api/telemetry-compare/{year}/{rnd}")
def telemetry_compare(year: int, rnd: int, d1: str, d2: str) -> Any:
    key = f"telcmp:{year}:{rnd}:{d1}:{d2}"
    return cached(key, lambda: _guard(lambda: svc.get_telemetry_compare(year, rnd, d1, d2), "telemetry compare"))


@app.get("/api/retirements/{year}/{rnd}")
def retirements(year: int, rnd: int) -> Any:
    key = f"retire:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: svc.get_retirements(year, rnd), "retirements"))


@app.get("/api/reliability/{year}")
def reliability(year: int) -> Any:
    key = f"reliability:{year}"
    return cached(key, lambda: _guard(lambda: svc.get_reliability(year), "reliability"))


@app.get("/api/compare/{year}/{d1}/{d2}")
def compare(year: int, d1: str, d2: str) -> Any:
    key = f"compare:{year}:{d1}:{d2}"
    return cached(key, lambda: _guard(lambda: svc.get_compare(year, d1, d2), "compare"))


@app.get("/api/standings-evolution/{year}")
def standings_evolution(year: int) -> Any:
    key = f"standevo:{year}"
    return cached(key, lambda: _guard(lambda: svc.get_standings_evolution(year), "standings evolution"))


# --------------------------------------------------------------------------- #
#  Derived analysis (analytics_service)
# --------------------------------------------------------------------------- #
@app.get("/api/race-trace/{year}/{rnd}")
def race_trace(year: int, rnd: int, mode: str = "median") -> Any:
    key = f"racetrace:{year}:{rnd}:{mode}"
    return cached(key, lambda: _guard(lambda: analytics.get_race_trace(year, rnd, mode), "race trace"))


@app.get("/api/tyre-performance/{year}/{rnd}")
def tyre_performance(year: int, rnd: int) -> Any:
    key = f"tyreperformance:{year}:{rnd}"
    return cached(
        key,
        lambda: _guard(lambda: analytics.get_tyre_performance(year, rnd), "tyre performance"),
    )


@app.get("/api/pit-stops/{year}/{rnd}")
def pit_stops(year: int, rnd: int) -> Any:
    key = f"pitstops:{year}:{rnd}"
    return cached(key, lambda: _guard(lambda: analytics.get_pit_stops(year, rnd), "pit stops"))


@app.get("/api/qualifying-sectors/{year}/{rnd}")
def qualifying_sectors(year: int, rnd: int) -> Any:
    key = f"qualifyingsectors:{year}:{rnd}"
    return cached(
        key,
        lambda: _guard(lambda: analytics.get_qualifying_sectors(year, rnd), "qualifying sectors"),
    )


@app.get("/api/minisectors/{year}/{rnd}")
def minisectors(year: int, rnd: int, session: str = "Q", top: int = 10) -> Any:
    key = f"minisectors:{year}:{rnd}:{session}:{top}"
    return cached(
        key,
        lambda: _guard(
            lambda: analytics.get_minisectors(year, rnd, session, top),
            "mini sectors",
        ),
    )


@app.get("/api/title-scenarios/{year}")
def title_scenarios(
    year: int,
    d1: str | None = None,
    d2: str | None = None,
    through_round: int | None = None,
) -> Any:
    key = f"titlescenarios:{year}:{d1 or ''}:{d2 or ''}:{through_round if through_round is not None else 'live'}"
    return cached(
        key,
        lambda: _guard(
            lambda: analytics.get_title_scenarios(year, d1, d2, through_round),
            "title scenarios",
        ),
    )


@app.get("/api/driver-fingerprint/{year}/{driver_id}")
def driver_fingerprint(year: int, driver_id: str) -> Any:
    key = f"driverfingerprint:{year}:{driver_id}"
    return cached(
        key,
        lambda: _guard(
            lambda: analytics.get_driver_fingerprint(year, driver_id),
            "driver fingerprint",
        ),
    )


# Normalise any uncaught error into JSON rather than an HTML 500 page.
@app.exception_handler(Exception)
def unhandled(_request, exc: Exception) -> JSONResponse:  # noqa: ANN001
    log.exception("unhandled error")
    return JSONResponse(status_code=500, content={"detail": str(exc)})
