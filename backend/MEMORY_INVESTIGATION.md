# Backend memory investigation — 30 October 2026

## Findings

The code and local reproductions show substantial intentional retention and a
concurrent-load defect. They do not establish an unbounded production leak:
production memory profiles and traffic history were not available.

### 1. Session cache is large enough to explain multi-day growth

`fastf1_service._SESSION_CACHE` retains 48 loaded sessions by default. The key
includes year, round, identifier and four feed-selection flags, so different
features can retain separate copies of the same race. Native FastF1 sessions
have no TTL; hits do not update the FIFO eviction order. An unused session can
remain for the lifetime of the process until displaced by new entries.

An offline load of the locally cached 2021 Abu Dhabi race, with laps and
telemetry, measured:

| Measurement | MiB |
| --- | ---: |
| Initial process peak RSS | 90.9 |
| Sum of car/position DataFrame deep memory usage | 214.8 |
| Peak RSS after loading | 383.8 |

This used local Python 3.14 and FastF1 3.8.3; the production Dockerfile uses
Python 3.12. RSS values are process high-water marks, not current usage or a
measurement of permanent allocation. Frame size is a separate measurement.
48 telemetry sessions of this size would contain about 10 GiB of trace frames
alone. Actual cache contents vary; many entries have no telemetry. A count
limit is not a memory budget. Gradual browsing across races can fill this cache
over days, even without an unbounded leak.

### 2. Concurrent cache misses duplicate session loads (fixed)

FastAPI executes synchronous routes in multiple threads even with one uvicorn
worker. `_load_session` previously checked, loaded and inserted without a lock.
An offline, mocked eight-thread reproduction of the same cache key produced
eight distinct sessions, one dictionary entry and eight eviction-order entries.
Duplicate loads multiply peak allocation and corrupt eviction bookkeeping.

The patch serializes session loads and cache mutations with a process-wide
reentrant lock. Concurrent requests for a successfully loaded key reuse that
session. Distinct cold loads are also serialized, reducing simultaneous heavy
allocations; session hits can wait behind a cold load. This trades some cold
request latency for lower memory pressure. It does not serialize subsequent
analysis/response construction or prevent loads in other processes.

### 3. Other caches add retention

`main._CACHE` defaults to 256 response objects and `openf1_service._cache` to
128 raw upstream responses. Neither is bounded by bytes. Replay positions and
raw OpenF1 location responses can be large. Their six-hour TTL is checked only
when that key is requested again; it does not release cold expired entries.
Lowering `CACHE_TTL_SECONDS` alone therefore does not clear accumulated memory.
The OpenF1 cache already serializes its own requests.

The endpoint response cache also lacks a lock around its producer and order
updates. Duplicate response computations remain possible after a shared session
load completes. The session patch does not claim to solve those allocations.

### 4. Evicted sessions can await cyclic garbage collection

In the real-session experiment, clearing the session cache left a weak reference
alive. Explicit cyclic collection released the session (10,331 objects collected
in that run). This is consistent with FastF1 session/laps back-references.
Eviction does not necessarily immediately free all session data, and the
allocator may retain freed pages. This experiment does not justify forcing
`gc.collect()` on every request or treating RSS alone as proof of live objects.

## Immediate deployment mitigation

For a memory-constrained container, start by setting and restarting with:

```dotenv
SESSION_CACHE_MAX=2
CACHE_MAX_ENTRIES=64
WEB_CONCURRENCY=1
```

These are starting values to evaluate under representative traffic, not a
guarantee that the service fits any particular RAM limit. More misses mean more
session reloads and slower requests. Keep the persistent FastF1 disk cache.
Do not set `CACHE_MAX_ENTRIES=0`: it disables the response entry limit.
The patch deliberately preserves existing cache defaults.

## Confirming the production cause

1. Record container memory, process RSS, traffic and cache occupancy together.
   Distinguish process anonymous memory from file/page cache counted by container
   metrics. Capture cache counts and approximate bytes by payload type without
   dumping authentication data or full responses.
2. Compare a fixed, repeated set of race requests against a run that continually
   introduces new races. Allow the count limits to be reached. Plateauing with
   cache occupancy supports retention; growth with fixed occupancy needs a heap
   investigation. Repeat after the concurrency fix and smaller cache limits.
3. On staging, compare live allocations before/after cache eviction and explicit
   cyclic collection. Use Python allocation snapshots plus process RSS: pandas
   and NumPy/native allocations and allocator retention can require additional
   native-memory tooling. A production Python 3.12 run is needed to confirm the
   magnitude seen locally.

Longer-term changes to evaluate are byte budgets for response/raw caches,
expiry sweeps, fewer duplicate feed variants, and bounded expensive-request
concurrency. Their tradeoffs should be tested with production-shaped traffic;
the current patch is a focused fix for the reproduced session-load race.

## Validation

Regression tests cover concurrent cold requests sharing one session and cyclic
sessions becoming collectible after eviction. Existing incomplete-feed,
forced-refresh, API-cache and OpenF1 tests are also exercised.

- Focused suite: 56 passed.
- Full backend suite: 219 passed, two App Attest failures in macOS system
  certificate trust-list extraction (`oscrypto` Security framework error -50).
- The concurrency regression test was also run against the original loader:
  it failed with eight constructed sessions, confirming it detects the defect.
- `git diff --check` passed.
