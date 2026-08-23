// The screen-by-screen feature catalogue.
//
// Source of truth is RaceControlAndroid/docs/FEATURES.md §3 (which documents the
// iOS screens and every deliberate Android divergence) plus the root README §3.
// Kept here as typed data rather than prose in a page component so the features
// page, the platform pages and the home page all render from one list and can't
// drift apart.

export type Platform = "ios" | "android" | "web";

export type Feature = {
  id: string;
  name: string;
  tagline: string;
  detail: string;
  /** Bullet points of what's actually on the screen. */
  highlights: string[];
  /** Backend endpoints this screen reads. */
  endpoints: string[];
  platforms: Platform[];
  /** Called out when a platform deliberately differs. */
  divergence?: string;
  category: "Browse" | "Race analysis" | "Championship";
};

export const FEATURES: Feature[] = [
  {
    id: "schedule",
    name: "Races",
    tagline: "The season calendar, with an up-next banner",
    detail:
      "The season calendar in round order. The first non-completed event is promoted into an 'up next' banner; past rounds carry a completed check, and sprint weekends are badged. Tapping a round opens its results.",
    highlights: [
      "Round number, circuit flag, race name, location and date per row",
      "SPRINT badge on sprint weekends, completed check on past rounds",
      "Season picker for any year from 2018 to present",
      "Pull to refresh, with loading / error-and-retry / empty states",
    ],
    endpoints: ["/api/schedule/{year}", "/api/seasons"],
    platforms: ["ios", "android", "web"],
    category: "Browse",
  },
  {
    id: "race-detail",
    name: "Race detail & results",
    tagline: "Full classification for every session of a weekend",
    detail:
      "A header card with the official event name and long-form date, the weekend session schedule, and the full classification. A session picker switches between Race, Qualifying, Sprint, Sprint Quali and FP1–FP3 — whichever that weekend actually ran.",
    highlights: [
      "Position badge with a gold treatment for the podium, plus a team accent bar",
      "Race times as absolute for the leader and gap for everyone else",
      "Qualifying shows the Q1/Q2/Q3 best times instead",
      "Grid delta arrow (places gained or lost) and points per driver",
      "Non-finishers show cause: DNF, +1 Lap, Accident",
    ],
    endpoints: ["/api/results/{year}/{round}/{session}"],
    platforms: ["ios", "android", "web"],
    divergence:
      "Session counts vary from 1 to 6 per weekend, so Android uses a scrollable tab row where iOS uses a segmented control.",
    category: "Browse",
  },
  {
    id: "replay",
    name: "Race replay",
    tagline: "Scrub or play a race lap by lap and watch the order change",
    detail:
      "The most stateful screen in the app. Step or play through a race one lap at a time and watch the running order animate, with each driver's movement against the previous lap, their tyre compound and their lap time.",
    highlights: [
      "Transport controls: scrub slider, first lap, −5, play/pause, +5, final lap",
      "Playback speed at 0.5×, 1×, 2× or 4×",
      "Position changes animate as a spring, with movement triangles up/down/level",
      "Tyre compound badge and lap time per driver, per lap",
      "Respects reduce-motion; playback stops when you leave the screen",
    ],
    endpoints: ["/api/replay/{year}/{round}", "/api/replay-positions/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    divergence:
      "Android keeps the screen awake during playback — an addition the iOS build doesn't need, since playback sessions there are typically short.",
    category: "Race analysis",
  },
  {
    id: "telemetry",
    name: "Telemetry",
    tagline: "Speed, throttle and gear traces over a lap",
    detail:
      "Fastest-lap telemetry traced over lap distance, with up to three drivers overlaid for a head-to-head. A lap replay sweeps a dot around a mini track map with live speed and gear readouts and a matching chart playhead.",
    highlights: [
      "Speed, throttle and gear traces sharing one distance axis",
      "Multi-select up to three drivers for direct comparison",
      "Playhead sweeping a mini track map with live readouts",
      "Flags the lap you're viewing if it ran under yellow, SC, VSC or red",
    ],
    endpoints: [
      "/api/telemetry/{year}/{round}/{driver}",
      "/api/telemetry-compare/{year}/{round}",
    ],
    platforms: ["ios", "android", "web"],
    divergence:
      "Three chart stacks render this: Swift Charts on iOS, an in-house Compose Canvas layer on Android, Recharts on web.",
    category: "Race analysis",
  },
  {
    id: "laptimes",
    name: "Lap times",
    tagline: "Multi-driver lap-time evolution",
    detail:
      "A lap-time evolution line chart across the race for any selection of drivers, with outlier filtering so a single safety-car lap doesn't flatten the whole scale. Safety-car and flag periods are banded onto the chart.",
    highlights: [
      "Multi-series line chart, one series per selected driver in team colours",
      "Hide-outliers toggle, plus All/None selection chips",
      "Flag and safety-car periods drawn as translucent bands behind the traces",
    ],
    endpoints: ["/api/laptimes/{year}/{round}", "/api/flags/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "strategy",
    name: "Tyre strategy",
    tagline: "Stint timelines and pit-stop counts",
    detail:
      "A per-driver stint timeline showing which compound ran when, coloured by the official tyre compound colours, with pit-stop counts per driver.",
    highlights: [
      "Horizontal stint bars per driver across the race distance",
      "Official compound colours: soft, medium, hard, intermediate, wet",
      "Pit-stop count per driver",
    ],
    endpoints: ["/api/strategy/{year}/{round}", "/api/pit-stops/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "flags",
    name: "Flags",
    tagline: "Every flag and safety-car period, lap by lap",
    detail:
      "A chronological timeline of every flag and safety-car period issued during the race, each with the lap range it covered and the race-control message explaining why. The raw event timeline sits underneath, collapsed.",
    highlights: [
      "Collapsed periods for yellow, double yellow, red, safety car and VSC",
      "Inclusive lap range and reason per period",
      "Expandable raw race-control event timeline underneath",
      "The same periods band the lap-times chart and badge the telemetry view",
    ],
    endpoints: ["/api/flags/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "racecontrol",
    name: "Race control",
    tagline: "The complete stewards' log, not just flags",
    detail:
      "Where Flags collapses only flag and safety-car messages into periods, this is the full chronological race-control log: DRS enabled and disabled, car events, and the investigations, penalties and reprimands that Flags never surfaces.",
    highlights: [
      "Every message in order, with the lap and the driver(s) named where there are any",
      "Filter chips: All, Flags, Safety Car, DRS, Incidents",
      "Category icons and colours consistent with the Flags screen",
      "Real examples: 'TURN 3 INCIDENT INVOLVING CARS 16 AND 55 UNDER INVESTIGATION'",
    ],
    endpoints: ["/api/racecontrol/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "qualifying",
    name: "Qualifying",
    tagline: "Q1/Q2/Q3 breakdown with gap to pole",
    detail:
      "The full qualifying breakdown across all three segments, with each driver's gap to pole and elimination shading showing where each was knocked out. A sector waterfall decomposes where the gap to pole was actually lost.",
    highlights: [
      "Q1, Q2 and Q3 times per driver with elimination shading",
      "Gap to pole per segment",
      "Sector-by-sector decomposition of the gap, plus ideal laps and speed traps",
    ],
    endpoints: ["/api/qualifying-sectors/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "weather",
    name: "Weather",
    tagline: "Session conditions and how they changed",
    detail:
      "Air and track temperature, humidity, pressure, wind and rainfall for a session, both as a summary and as the full cached timeline across the session.",
    highlights: [
      "Air and track temperature, including maxima",
      "Humidity, pressure and wind",
      "Rainfall flag, with rain periods banded onto the timeline",
    ],
    endpoints: ["/api/weather/{year}/{round}/{session}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "retirements",
    name: "Retirements",
    tagline: "Who didn't finish, and why",
    detail:
      "Non-finishers for a race grouped by cause, separating mechanical failures from accidents and disqualifications.",
    highlights: ["Grouped by mechanical, accident, disqualified and other", "Cause text per retirement"],
    endpoints: ["/api/retirements/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Race analysis",
  },
  {
    id: "circuits",
    name: "Circuits & track maps",
    tagline: "Track outlines with numbered corners and DRS zones",
    detail:
      "Season venues in calendar order, tagged Raced or Upcoming. Raced circuits open a detail page with a rich track map: the outline plus numbered corner markers, coloured by speed with DRS zones highlighted.",
    highlights: [
      "Track outline with numbered corner markers, rotated to the correct orientation",
      "Trace coloured by speed, DRS zones highlighted",
      "Length in km and miles, corner count, total laps",
      "Fastest lap with driver, team, time and compound, plus the podium",
      "Pinch to zoom and pan",
    ],
    endpoints: ["/api/circuits/{year}", "/api/circuit/{year}/{round}"],
    platforms: ["ios", "android", "web"],
    category: "Browse",
  },
  {
    id: "drivers",
    name: "Drivers",
    tagline: "Searchable roster and per-driver season detail",
    detail:
      "A searchable roster by name, code or number, with headshots, team colour accents, championship position, points and wins. Favourites pin to the top. Driver detail adds a form sparkline and every result of the season.",
    highlights: [
      "Search by name, code or number; favourite star pins a driver to the top",
      "Headshot, number, team, nationality and date of birth",
      "Season stat cells for points, wins and championship position",
      "Form sparkline across rounds, plus the full per-round result list",
    ],
    endpoints: ["/api/drivers/{year}", "/api/drivers/{year}/{driverId}"],
    platforms: ["ios", "android", "web"],
    category: "Browse",
  },
  {
    id: "head-to-head",
    name: "Head-to-head",
    tagline: "Two drivers, compared directly",
    detail:
      "Pick any two drivers from a season and compare them across the headline season numbers plus their direct records against each other in races and qualifying.",
    highlights: [
      "Points, wins, podiums, poles, best finish and DNFs side by side",
      "Direct race and qualifying win records between the pair",
    ],
    endpoints: ["/api/compare/{year}/{d1}/{d2}"],
    platforms: ["ios", "android", "web"],
    category: "Championship",
  },
  {
    id: "teams",
    name: "Constructors",
    tagline: "Team cards in standings order with liveries",
    detail:
      "Standings-ordered team cards in livery colours, showing position, points, wins and the driver line-up. Detail adds the full roster.",
    highlights: ["Livery colour per team", "Position, points and wins", "Driver line-up with avatars"],
    endpoints: ["/api/teams/{year}", "/api/teams/{year}/{teamId}"],
    platforms: ["ios", "android", "web"],
    category: "Championship",
  },
  {
    id: "standings",
    name: "Standings",
    tagline: "Four views: drivers, teams, progress and reliability",
    detail:
      "The drivers' and constructors' championships with gap-to-leader bars and win counts, plus a round-by-round cumulative points chart and a season reliability breakdown.",
    highlights: [
      "Drivers and Teams: rank, points, wins and a gap-to-leader bar",
      "Progress: multi-line cumulative championship points by round",
      "Reliability: finish rate per driver and team, stacked by cause of DNF",
    ],
    endpoints: [
      "/api/standings/drivers/{year}",
      "/api/standings/constructors/{year}",
      "/api/standings-evolution/{year}",
      "/api/reliability/{year}",
    ],
    platforms: ["ios", "android", "web"],
    divergence:
      "Four modes is wider than a comfortable segmented control on a compact Android screen, so Android uses a tab row.",
    category: "Championship",
  },
  {
    id: "race-trace",
    name: "Race trace",
    tagline: "Cumulative time delta, lap by lap",
    detail:
      "The classic race trace: cumulative time delta per driver per lap, against either a fixed race-wide median lap or the leader. The vertical distance between two drivers is their real on-track time gap.",
    highlights: [
      "Median mode subtracts a fixed green-flag median lap from cumulative elapsed time",
      "Leader mode reports the gap to the leader on each lap",
      "Neutralised safety-car periods returned and drawn as explicit bands",
    ],
    endpoints: ["/api/race-trace/{year}/{round}"],
    platforms: ["ios", "web"],
    divergence: "Not yet on Android — tracked in the cross-platform parity checklist.",
    category: "Race analysis",
  },
  {
    id: "tyre-performance",
    name: "Tyre degradation",
    tagline: "How each compound fell away, per stint",
    detail:
      "Filtered per-stint lap samples with fitted degradation slopes per driver, set against field-wide baselines for each compound.",
    highlights: [
      "Per-stint samples with outlier laps filtered out",
      "Fitted degradation slope per stint",
      "Field-wide compound baselines to compare against",
    ],
    endpoints: ["/api/tyre-performance/{year}/{round}"],
    platforms: ["ios", "web"],
    category: "Race analysis",
  },
  {
    id: "pit-stops",
    name: "Pit-stop ledger",
    tagline: "Real pit-lane loss and whether the stop worked",
    detail:
      "Actual pit-lane transit loss per stop rather than stationary time, with the positions a driver entered and rejoined in, the outcome against the rival window, and the circuit median to compare against.",
    highlights: [
      "Real pit-lane transit loss, not just stationary time",
      "Entry and rejoin positions per stop",
      "Rival-window outcome, and the circuit median stop",
    ],
    endpoints: ["/api/pit-stops/{year}/{round}"],
    platforms: ["ios", "web"],
    category: "Race analysis",
  },
  {
    id: "minisectors",
    name: "Mini-sector dominance",
    tagline: "Who owned which piece of track",
    detail:
      "The lap split into roughly 24 curved mini-sectors, each coloured by whichever driver was fastest through it — the clearest single view of where a lap was won.",
    highlights: [
      "~24 curved track segments coloured by fastest driver",
      "Configurable across the top N drivers",
      "Works on any session, not just the race",
    ],
    endpoints: ["/api/minisectors/{year}/{round}"],
    platforms: ["ios", "web"],
    category: "Race analysis",
  },
  {
    id: "title-scenarios",
    name: "Title scenarios",
    tagline: "What has to happen for the championship to be decided",
    detail:
      "A next-race finish-position matrix between two title contenders, with projected points margins and generated clinch summaries. Historical seasons follow a time machine, so no hypothetical race is shown after a season has finished.",
    highlights: [
      "Finish-position matrix for the next race between two contenders",
      "Projected points margins and clinch conditions",
      "Historical snapshots through any round of a past season",
    ],
    endpoints: ["/api/title-scenarios/{year}"],
    platforms: ["ios", "web"],
    category: "Championship",
  },
  {
    id: "fingerprint",
    name: "Driver fingerprint",
    tagline: "A season on six axes",
    detail:
      "Six season percentile axes covering qualifying, race pace, tyre management, starts, reliability and wet pace — a shape you can compare between drivers at a glance.",
    highlights: [
      "Qualifying, race pace, tyres, starts, reliability and wet pace",
      "Expressed as season percentiles against the field",
    ],
    endpoints: ["/api/driver-fingerprint/{year}/{driver_id}"],
    platforms: ["ios", "web"],
    category: "Championship",
  },
  {
    id: "notifications",
    name: "Session reminders",
    tagline: "Get told before a session starts",
    detail:
      "Local notifications before sessions you care about, configurable per session type and lead time.",
    highlights: [
      "Remind a day before, an hour before, or 15 minutes before",
      "Choose which session types: practice, qualifying, sprint, race",
    ],
    endpoints: [],
    platforms: ["ios", "android"],
    divergence:
      "Deliberately not on web: there's no clean stateless-web equivalent without service-worker push infrastructure the backend doesn't have.",
    category: "Browse",
  },
  {
    id: "offline",
    name: "Offline cache",
    tagline: "Schedule and standings without a connection",
    detail:
      "A 10 MB response cache backs the schedule and standings, with a banner making it explicit when you're looking at cached data.",
    highlights: ["10 MB response cache", "Explicit 'showing cached data' banner"],
    endpoints: [],
    platforms: ["android"],
    divergence:
      "Android only. The iOS build is designed around always having a backend reachable on the same network during development.",
    category: "Browse",
  },
];

export const PLATFORM_LABELS: Record<Platform, string> = {
  ios: "iOS",
  android: "Android",
  web: "Web",
};

export const FEATURE_CATEGORIES = ["Browse", "Race analysis", "Championship"] as const;
