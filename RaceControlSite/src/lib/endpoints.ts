// The complete public API surface, transcribed from the root README's endpoint
// tables (§1 "Endpoints" and "Derived analysis endpoints").
//
// Typed data rather than a static table in JSX so the docs page can filter and
// search it, and so there's exactly one place to update when the API changes.

export type Endpoint = {
  method: "GET";
  path: string;
  summary: string;
  /** Query parameters, where the endpoint takes any. */
  params?: { name: string; description: string }[];
  group: EndpointGroup;
  /** Endpoints whose first uncached call is deliberately expensive. */
  expensive?: boolean;
};

export const ENDPOINT_GROUPS = [
  "Season & schedule",
  "Results & standings",
  "Drivers & teams",
  "Circuits",
  "Replay & telemetry",
  "Race conditions",
  "Derived analytics",
] as const;

export type EndpointGroup = (typeof ENDPOINT_GROUPS)[number];

export const ENDPOINTS: Endpoint[] = [
  // Season & schedule
  { method: "GET", path: "/api/seasons", summary: "Available seasons (2018 to present).", group: "Season & schedule" },
  {
    method: "GET",
    path: "/api/schedule/{year}",
    summary: "Race calendar for a season, in round order.",
    group: "Season & schedule",
  },
  {
    method: "GET",
    path: "/api/health",
    summary: "Liveness probe. Deliberately unauthenticated so the platform can poll it.",
    group: "Season & schedule",
  },

  // Results & standings
  {
    method: "GET",
    path: "/api/results/{year}/{round}/{session}",
    summary: "Classification for a session. Session is one of R, Q, S, SQ, FP1, FP2, FP3.",
    group: "Results & standings",
  },
  {
    method: "GET",
    path: "/api/standings/drivers/{year}",
    summary: "Driver championship standings.",
    group: "Results & standings",
  },
  {
    method: "GET",
    path: "/api/standings/constructors/{year}",
    summary: "Constructor championship standings.",
    group: "Results & standings",
  },
  {
    method: "GET",
    path: "/api/standings-evolution/{year}",
    summary: "Round-by-round cumulative championship points per driver.",
    group: "Results & standings",
  },
  {
    method: "GET",
    path: "/api/reliability/{year}",
    summary: "Season DNF breakdown per driver and team.",
    group: "Results & standings",
  },

  // Drivers & teams
  {
    method: "GET",
    path: "/api/drivers/{year}",
    summary: "Season drivers with headshots, teams and points.",
    group: "Drivers & teams",
  },
  {
    method: "GET",
    path: "/api/drivers/{year}/{driverId}",
    summary: "Driver detail plus per-round results.",
    group: "Drivers & teams",
  },
  {
    method: "GET",
    path: "/api/racedrivers/{year}/{round}",
    summary: "Driver list for a single race, for populating pickers.",
    group: "Drivers & teams",
  },
  {
    method: "GET",
    path: "/api/teams/{year}",
    summary: "Constructors with rosters and livery colours.",
    group: "Drivers & teams",
  },
  { method: "GET", path: "/api/teams/{year}/{teamId}", summary: "Team detail.", group: "Drivers & teams" },
  {
    method: "GET",
    path: "/api/compare/{year}/{d1}/{d2}",
    summary: "Two-driver season head-to-head.",
    group: "Drivers & teams",
  },

  // Circuits
  { method: "GET", path: "/api/circuits/{year}", summary: "Circuits visited that season.", group: "Circuits" },
  {
    method: "GET",
    path: "/api/circuit/{year}/{round}",
    summary: "Track outline and corners, with length and fastest lap.",
    group: "Circuits",
  },

  // Replay & telemetry
  {
    method: "GET",
    path: "/api/replay/{year}/{round}",
    summary: "Lap-by-lap running order driving the race replay.",
    group: "Replay & telemetry",
  },
  {
    method: "GET",
    path: "/api/replay-positions/{year}/{round}",
    summary:
      "Per-driver car X/Y positions sampled across each lap, for animating cars around the track outline during replay.",
    group: "Replay & telemetry",
  },
  {
    method: "GET",
    path: "/api/telemetry/{year}/{round}/{driver}",
    summary: "Fastest-lap telemetry trace for one driver.",
    group: "Replay & telemetry",
  },
  {
    method: "GET",
    path: "/api/telemetry-compare/{year}/{round}",
    summary: "Two-driver telemetry overlay.",
    params: [
      { name: "d1", description: "First driver identifier." },
      { name: "d2", description: "Second driver identifier." },
    ],
    group: "Replay & telemetry",
  },
  {
    method: "GET",
    path: "/api/laptimes/{year}/{round}",
    summary: "Per-driver lap-time series for the evolution chart.",
    group: "Replay & telemetry",
  },

  // Race conditions
  {
    method: "GET",
    path: "/api/flags/{year}/{round}",
    summary:
      "Track flags and safety-car history: raw race-control events plus collapsed lap-range periods for yellow, double yellow, red, VSC and SC.",
    params: [{ name: "session", description: "Session code. Defaults to R." }],
    group: "Race conditions",
  },
  {
    method: "GET",
    path: "/api/racecontrol/{year}/{round}",
    summary:
      "The complete race-control message log in chronological order — flags and safety car, but also DRS enable/disable, car events, and investigations, penalties and reprimands.",
    params: [{ name: "session", description: "Session code. Defaults to R." }],
    group: "Race conditions",
  },
  {
    method: "GET",
    path: "/api/weather/{year}/{round}/{session}",
    summary: "Session weather summary plus the full cached weather timeline.",
    group: "Race conditions",
  },
  {
    method: "GET",
    path: "/api/retirements/{year}/{round}",
    summary: "Non-finishers with cause of retirement.",
    group: "Race conditions",
  },
  {
    method: "GET",
    path: "/api/strategy/{year}/{round}",
    summary: "Tyre stints and pit-stop counts per driver.",
    group: "Race conditions",
  },

  // Derived analytics
  {
    method: "GET",
    path: "/api/race-trace/{year}/{round}",
    summary:
      "Race trace — cumulative time delta per driver per lap, with safety-car periods and chart domains included.",
    params: [
      {
        name: "mode",
        description:
          "median (default) subtracts a fixed race-wide green-flag median lap from cumulative elapsed time; leader reports the gap to the leader on each lap.",
      },
    ],
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/tyre-performance/{year}/{round}",
    summary: "Tyre degradation — filtered per-stint samples, fitted slopes and field-wide compound baselines.",
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/pit-stops/{year}/{round}",
    summary:
      "Pit-stop ledger — real pit-lane transit loss, entry and rejoin positions, rival-window outcome, and circuit median.",
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/qualifying-sectors/{year}/{round}",
    summary: "Qualifying sector waterfall — gap-to-pole sector decomposition, ideal laps and speed traps.",
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/minisectors/{year}/{round}",
    summary: "Mini-sector dominance — roughly 24 curved track segments coloured by their fastest driver.",
    params: [
      { name: "session", description: "Session code. Defaults to Q." },
      { name: "top", description: "Number of drivers to include. Defaults to 10." },
    ],
    expensive: true,
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/title-scenarios/{year}",
    summary:
      "Title permutations — next-race finish-position matrix, projected points margins, and generated clinch summaries.",
    params: [
      { name: "d1", description: "First contender." },
      { name: "d2", description: "Second contender." },
      { name: "through_round", description: "Historical snapshot through this round." },
    ],
    group: "Derived analytics",
  },
  {
    method: "GET",
    path: "/api/driver-fingerprint/{year}/{driver_id}",
    summary:
      "Driver fingerprint — six season percentile axes covering qualifying, race pace, tyres, starts, reliability and wet pace.",
    group: "Derived analytics",
  },
];
