"""
Tests for the FastAPI app's own infrastructure — the rate limiter's client
identity, its map hygiene, and the response cache's TTL and size bound. These
are the parts of `main.py` that sit in front of every endpoint, so a regression
here degrades the whole API rather than one route.

Env is set and `main` re-imported before the app is built, mirroring
`test_attest_endpoints.py`, so this file is order-independent: it does not
inherit whichever auth configuration another test module happened to install.
"""

import os
import sys

# No auth: these tests are about the rate limiter and cache, not authorisation,
# and an open API keeps the requests below to one concern each.
for var in ["APP_ATTEST_ENABLED", "PLAY_INTEGRITY_ENABLED", "API_TOKEN"]:
    os.environ.pop(var, None)
# Small enough to trip inside a test without 120 requests.
os.environ["RATE_LIMIT_PER_MINUTE"] = "3"

for m in ["attest", "playintegrity", "main"]:
    sys.modules.pop(m, None)

import main  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402


def _fresh_client() -> TestClient:
    main._rate_hits.clear()
    main._CACHE.clear()
    main._CACHE_ORDER.clear()
    return TestClient(main.app)


class _Req:
    """Minimal stand-in for a Starlette Request for `_client_key`."""

    class _Client:
        def __init__(self, host):
            self.host = host

    def __init__(self, headers=None, peer="10.0.0.1"):
        self.headers = headers or {}
        self.client = self._Client(peer) if peer else None


# --------------------------------------------------------------------------- #
#  _client_key — the A3 fix
# --------------------------------------------------------------------------- #
def test_client_key_prefers_forwarded_for():
    # Behind a proxy, the peer address is the proxy's; the origin client is the
    # leftmost X-Forwarded-For entry.
    req = _Req({"x-forwarded-for": "203.0.113.7"}, peer="172.17.0.1")
    assert main._client_key(req) == "203.0.113.7"


def test_client_key_takes_leftmost_of_a_proxy_chain():
    req = _Req({"x-forwarded-for": "203.0.113.7, 70.41.3.18, 172.17.0.1"}, peer="172.17.0.1")
    assert main._client_key(req) == "203.0.113.7"


def test_client_key_falls_back_to_peer_without_the_header():
    # Local ./run.sh: no proxy, so the peer address really is the client.
    assert main._client_key(_Req({}, peer="192.168.1.50")) == "192.168.1.50"


def test_client_key_ignores_an_empty_or_blank_forwarded_header():
    assert main._client_key(_Req({"x-forwarded-for": ""}, peer="192.168.1.50")) == "192.168.1.50"
    assert main._client_key(_Req({"x-forwarded-for": "  ,  "}, peer="192.168.1.50")) == "192.168.1.50"


def test_client_key_handles_a_missing_peer():
    assert main._client_key(_Req({}, peer=None)) == "unknown"


# --------------------------------------------------------------------------- #
#  The proxy-trust boundary is a deployment invariant, not just code — pin the
#  Dockerfile flags that make trusting X-Forwarded-For safe, so someone
#  editing the CMD can't silently break the assumption _client_key relies on.
# --------------------------------------------------------------------------- #
def test_dockerfile_declares_the_proxy_trust_boundary():
    dockerfile = os.path.join(os.path.dirname(__file__), "Dockerfile")
    with open(dockerfile, encoding="utf-8") as f:
        cmd = f.read()
    assert "--proxy-headers" in cmd, (
        "uvicorn must be started with --proxy-headers, or X-Forwarded-For is "
        "ignored and the per-client rate limiter collapses to one shared bucket"
    )
    assert "--forwarded-allow-ips" in cmd, (
        "without an explicit --forwarded-allow-ips, uvicorn's default "
        "allowlist (127.0.0.1 only) discards X-Forwarded-For behind a "
        "non-loopback reverse proxy such as Coolify/Traefik"
    )


def test_web_concurrency_above_one_is_rejected_at_startup():
    # main.py's response/session/points-progression caches are per-process
    # dicts with no cross-worker sharing (see the comments beside each cache
    # definition); a second worker silently serves inconsistent cached state
    # rather than failing, so this must be a hard startup error instead.
    os.environ["WEB_CONCURRENCY"] = "2"
    try:
        for m in ["attest", "playintegrity", "main"]:
            sys.modules.pop(m, None)
        try:
            import main as _reimported  # noqa: F401
        except RuntimeError as exc:
            assert "WEB_CONCURRENCY" in str(exc)
        else:
            raise AssertionError("expected main import to fail with WEB_CONCURRENCY=2")
    finally:
        os.environ.pop("WEB_CONCURRENCY", None)
        for m in ["attest", "playintegrity", "main"]:
            sys.modules.pop(m, None)
        import main  # noqa: F401  (restore a clean import for subsequent tests)


# --------------------------------------------------------------------------- #
#  Rate limiting is per client, not global — the point of the A3 fix
# --------------------------------------------------------------------------- #
def test_distinct_forwarded_clients_get_independent_buckets():
    c = _fresh_client()
    main.svc.get_seasons = lambda: [2026]

    # Client A exhausts its allowance (limit 3).
    for _ in range(3):
        assert c.get("/api/seasons", headers={"X-Forwarded-For": "203.0.113.7"}).status_code == 200
    assert c.get("/api/seasons", headers={"X-Forwarded-For": "203.0.113.7"}).status_code == 429

    # Client B is unaffected. Before the fix both shared the proxy's address,
    # so A's burst would have 429'd B too.
    assert c.get("/api/seasons", headers={"X-Forwarded-For": "198.51.100.4"}).status_code == 200


def test_health_is_never_rate_limited():
    # The container healthcheck polls this every 30s and must not be throttled.
    c = _fresh_client()
    for _ in range(10):
        assert c.get("/api/health").status_code == 200


def test_rate_hit_map_is_swept_of_stale_clients():
    # One-shot scanners never return, so entries are only reclaimed by the
    # sweep; without it the map grows unboundedly (A5).
    c = _fresh_client()
    main.svc.get_seasons = lambda: [2026]

    stale_at = 0.0  # epoch: far outside the 60s window
    for i in range(1100):
        main._rate_hits[f"198.51.100.{i}"] = [stale_at]
    assert len(main._rate_hits) > 1024

    c.get("/api/seasons", headers={"X-Forwarded-For": "203.0.113.7"})

    assert len(main._rate_hits) == 1, "stale clients should have been reclaimed"
    assert "203.0.113.7" in main._rate_hits


def test_sweep_keeps_clients_still_inside_the_window():
    c = _fresh_client()
    main.svc.get_seasons = lambda: [2026]

    import time
    now = time.time()
    for i in range(1100):
        main._rate_hits[f"198.51.100.{i}"] = [now]  # all still active

    c.get("/api/seasons", headers={"X-Forwarded-For": "203.0.113.7"})

    # Active clients must survive, otherwise the sweep is itself a bypass.
    assert len(main._rate_hits) == 1101


# --------------------------------------------------------------------------- #
#  Response cache — TTL behaviour and the A4 size bound
# --------------------------------------------------------------------------- #
def test_cached_calls_the_producer_once_within_the_ttl():
    _fresh_client()
    calls = []

    def producer():
        calls.append(1)
        return {"v": len(calls)}

    assert main.cached("k", producer) == {"v": 1}
    assert main.cached("k", producer) == {"v": 1}
    assert len(calls) == 1


def test_cached_recomputes_after_the_ttl_expires():
    _fresh_client()
    calls = []

    def producer():
        calls.append(1)
        return len(calls)

    assert main.cached("k", producer) == 1
    # Backdate the entry past the TTL rather than sleeping.
    stamp, value = main._CACHE["k"]
    main._CACHE["k"] = (stamp - main._TTL_SECONDS - 1, value)
    assert main.cached("k", producer) == 2


def test_cache_is_bounded_and_evicts_least_recently_used_first():
    _fresh_client()
    original = main._CACHE_MAX_ENTRIES
    main._CACHE_MAX_ENTRIES = 5
    try:
        for i in range(8):
            main.cached(f"k{i}", lambda i=i: i)

        assert len(main._CACHE) == 5
        # With no repeat access, insertion order is also usage order.
        assert [k for k in ("k0", "k1", "k2") if k in main._CACHE] == []
        assert all(f"k{i}" in main._CACHE for i in range(3, 8))
    finally:
        main._CACHE_MAX_ENTRIES = original


def test_a_cache_hit_protects_the_key_from_lru_eviction():
    _fresh_client()
    original = main._CACHE_MAX_ENTRIES
    main._CACHE_MAX_ENTRIES = 3
    try:
        for key in ("old", "middle", "new"):
            main.cached(key, lambda key=key: key)

        # Touch the oldest key, making "middle" the least recently used.
        assert main.cached("old", lambda: "wrong") == "old"
        main.cached("newest", lambda: "newest")

        assert "old" in main._CACHE
        assert "middle" not in main._CACHE
    finally:
        main._CACHE_MAX_ENTRIES = original


def test_refreshing_an_existing_key_does_not_grow_the_order_list():
    # A key re-computed after TTL expiry must not be appended twice, or the
    # order list would outgrow the dict and evict live entries early.
    _fresh_client()
    main.cached("k", lambda: 1)
    stamp, value = main._CACHE["k"]
    main._CACHE["k"] = (stamp - main._TTL_SECONDS - 1, value)
    main.cached("k", lambda: 2)

    assert main._CACHE_ORDER.count("k") == 1
    assert len(main._CACHE) == 1


def test_cache_bound_can_be_disabled_with_zero():
    _fresh_client()
    original = main._CACHE_MAX_ENTRIES
    main._CACHE_MAX_ENTRIES = 0
    try:
        for i in range(50):
            main.cached(f"k{i}", lambda i=i: i)
        assert len(main._CACHE) == 50
    finally:
        main._CACHE_MAX_ENTRIES = original
