/**
 * Content registry for the in-app help drawer.
 *
 * Each entry maps a route "matcher" (a static pathname prefix plus an
 * expected dynamic-segment count, or an exact pathname), optionally scoped
 * to a query param key/value (e.g. the race-detail tab or standings mode),
 * to a short title + body used by <HelpDrawer />.
 *
 * `body` is a plain array of strings, each rendered as its own paragraph.
 * Keep entries short and factual: this is reference text, not marketing copy.
 */

export interface HelpContent {
  title: string;
  body: string[];
}

interface HelpEntry {
  /** Static path prefix, e.g. "/races". Matched against the first N segments of the pathname. */
  prefix: string;
  /** Exact number of path segments expected (prefix segments + dynamic segments). */
  segments: number;
  /** Optional query param to further discriminate (e.g. "tab", "mode"). */
  paramKey?: string;
  /** Map of param value -> content. Used together with paramKey. */
  paramContent?: Record<string, HelpContent>;
  /** Fallback content when there's no paramKey, or the param value isn't in paramContent. */
  content?: HelpContent;
}

function segmentCount(pathname: string): number {
  return pathname.split("/").filter(Boolean).length;
}

function prefixSegments(prefix: string): number {
  return prefix.split("/").filter(Boolean).length;
}

const RACE_TAB_CONTENT: Record<string, HelpContent> = {
  results: {
    title: "Race: Results",
    body: [
      "The classified race result: finishing position, driver, team, starting grid position, finishing status (Finished, +1 Lap, or a retirement reason), race time (or gap to the winner), and championship points scored.",
      "Drivers who didn't finish still appear, with their status column explaining why (e.g. Accident, Engine, Collision).",
    ],
  },
  qualifying: {
    title: "Race: Qualifying",
    body: [
      "The qualifying classification for this race weekend: grid position and each driver's best lap time in Q1, Q2, and Q3.",
      "A driver eliminated in Q1 or Q2 shows a time only for the segments they took part in; the remaining columns are blank.",
    ],
  },
  laptimes: {
    title: "Race: Lap Times",
    body: [
      "A lap-by-lap time chart for every driver, one line per driver, colored by team.",
      "Outlier laps (pit stops, safety-car laps, and anything more than 10% slower than the race median) are filtered out so the chart stays readable.",
      "Shaded bands mark flag periods (yellow, safety car, VSC, red) so you can see how they affected pace. Click a driver's chip above the chart to toggle their line on or off.",
    ],
  },
  strategy: {
    title: "Race: Strategy",
    body: [
      "Each driver's tyre strategy for the race: one horizontal bar per driver, split into stints. Stint width is proportional to how many laps it lasted, and color indicates the tyre compound (soft, medium, hard, intermediate, wet).",
      "The number on the right is the driver's total pit stop count. Hover a stint for its exact compound and lap range.",
    ],
  },
  weather: {
    title: "Race: Weather",
    body: [
      "Track and ambient conditions recorded during the race session: air temperature, track temperature (current and peak), humidity, wind speed, and whether rain fell.",
      "This is a session summary, not a lap-by-lap trace; it reflects overall conditions rather than a specific moment.",
    ],
  },
  retirements: {
    title: "Race: Retirements",
    body: [
      "Every driver who failed to finish the race, with their team and the official retirement reason (mechanical failure, accident, collision, disqualification, etc.).",
      "If nobody retired, this tab reports a clean race.",
    ],
  },
  flags: {
    title: "Race: Flags",
    body: [
      "Collapsed lap-range periods for track status changes (yellow, double-yellow, red, safety car, and virtual safety car), each shown with the race-control message that triggered it and the lap range it covered.",
      "Expand \"Raw race control log\" below the list to see every individual race control message in chronological order, including ones that aren't flag periods.",
    ],
  },
  racecontrol: {
    title: "Race: Race Control Log",
    body: [
      "The full, timestamped race control message feed for the session: flags, safety car deployments, DRS enable/disable notices, and car-related incidents (investigations, penalties, etc.).",
      "Use the filter chips at the top to narrow the log to just one category (Flags, Safety Car, DRS, or Incidents), or leave it on All to see everything in order.",
    ],
  },
  telemetry: {
    title: "Race: Telemetry",
    body: [
      "Speed, throttle, gear, and RPM traces plotted against distance around the lap for a driver's selected lap, plus a mini track map that highlights the corresponding position as you hover the charts.",
      "Pick a driver and either \"Fastest lap\" or any specific lap from the dropdown. Laps run under a flag (yellow, safety car, etc.) are marked with a colored dot in the lap picker.",
      "Turn on \"Compare\" to overlay a second driver's fastest lap on the same charts; comparison mode is restricted to fastest laps only.",
    ],
  },
  replay: {
    title: "Race: Replay",
    body: [
      "A lap-by-lap animated replay of the running order. Press Play to step through the race automatically, or drag the slider to jump to any lap.",
      "Each row shows position, driver, team, current tyre compound, and last lap time; rows animate as positions change between laps.",
    ],
  },
};

const STANDINGS_MODE_CONTENT: Record<string, HelpContent> = {
  drivers: {
    title: "Standings: Drivers",
    body: [
      "The drivers' championship table for the selected season: position, driver, team, race wins, and total points, ordered by points.",
    ],
  },
  constructors: {
    title: "Standings: Constructors",
    body: [
      "The constructors' championship table for the selected season: position, team, combined wins across both cars, and total points, ordered by points.",
    ],
  },
  progress: {
    title: "Standings: Progress",
    body: [
      "A running total of championship points by round for the top 8 point-scorers that season, one line per driver, colored by team.",
      "Only the top 8 are charted (plotting all 20 drivers on one line chart isn't readable), so a driver who ended the season lower down won't appear here even if they scored points early on.",
    ],
  },
  reliability: {
    title: "Standings: Reliability",
    body: [
      "Season-long reliability stats for drivers and teams: race starts, races finished, and a breakdown of DNFs into mechanical failures versus accidents, plus an overall finish percentage.",
      "Two separate tables are shown: one per driver, one per team (which combines both of a team's cars).",
    ],
  },
};

const ENTRIES: HelpEntry[] = [
  {
    prefix: "/schedule",
    segments: 1,
    content: {
      title: "Schedule",
      body: [
        "The full race calendar for the selected season, in round order, with each race's location and date.",
        "A sprint weekend is marked with a badge; races already run are shown in muted text, while the next upcoming race is highlighted with an \"Upcoming\" tag.",
        "Tap any race to open its detail page (results, qualifying, lap times, and the rest of the race-detail tabs).",
      ],
    },
  },
  {
    prefix: "/drivers/compare",
    segments: 2,
    content: {
      title: "Drivers: Head-to-Head",
      body: [
        "A side-by-side statistical comparison of two drivers for the selected season: total points, wins, podiums, poles, best finish, and DNFs.",
        "The two head-to-head rows at the bottom (Race H2H Wins, Qualifying H2H Wins) count how many times each driver finished ahead of the other in races and in qualifying that season, not their overall win totals.",
        "Pick both drivers from the dropdowns above to populate the comparison.",
      ],
    },
  },
  {
    prefix: "/drivers",
    segments: 1,
    content: {
      title: "Drivers",
      body: [
        "The full driver roster for the selected season, sorted by championship position (favorited drivers are pinned to the top).",
        "Each row shows the driver's team, current standings position, and a star button to favorite them; favorites also show up first on this list and in Settings.",
        "Use \"Head-to-head\" above the list to compare any two drivers directly.",
      ],
    },
  },
  {
    prefix: "/drivers",
    segments: 3,
    content: {
      title: "Driver Detail",
      body: [
        "A single driver's season: their team, car number, nationality, current points and championship position, and a full round-by-round results table.",
        "The results table lists every race that season with grid position, finishing position, status (Finished or a DNF reason), and points scored; click a race name to jump to its full race-detail page.",
      ],
    },
  },
  {
    prefix: "/teams",
    segments: 1,
    content: {
      title: "Teams",
      body: [
        "The constructor roster for the selected season, sorted by championship position (favorited teams are pinned to the top).",
        "Each row is tinted with the team's color and shows both drivers' codes, current standings position, and total points, plus a star button to favorite the team.",
      ],
    },
  },
  {
    prefix: "/teams",
    segments: 3,
    content: {
      title: "Team Detail",
      body: [
        "A single constructor's season summary: nationality, total points, championship position, and win count.",
        "Below that, both of the team's drivers for that season are listed; click through to either driver's own detail page.",
      ],
    },
  },
  {
    prefix: "/circuits",
    segments: 1,
    content: {
      title: "Circuits",
      body: [
        "Every venue on the selected season's calendar, in round order. Tap a circuit to see its track map and details for that race weekend.",
      ],
    },
  },
  {
    prefix: "/circuits",
    segments: 3,
    content: {
      title: "Circuit Detail",
      body: [
        "The track layout for this race weekend, drawn from GPS telemetry, along with the circuit's location and lap length.",
        "Below the map: an elevation profile of the lap, and (when available) the fastest lap set during the race weekend, with the driver, team, lap time, and tyre compound it was set on.",
      ],
    },
  },
  {
    prefix: "/standings",
    segments: 1,
    paramKey: "mode",
    paramContent: STANDINGS_MODE_CONTENT,
    content: STANDINGS_MODE_CONTENT.drivers,
  },
  {
    prefix: "/races",
    segments: 3,
    paramKey: "tab",
    paramContent: RACE_TAB_CONTENT,
    content: RACE_TAB_CONTENT.results,
  },
  {
    prefix: "/settings",
    segments: 1,
    content: {
      title: "Settings",
      body: [
        "Your favorited drivers and teams, each with a button to unfavorite them from here.",
        "Favorites are stored locally in your browser (not synced to an account) and are what gets pinned to the top of the Drivers and Teams lists.",
        "The About section at the bottom notes what data the app is built on: the FastF1 library and the Jolpica/Ergast API.",
      ],
    },
  },
];

const FALLBACK_CONTENT: HelpContent = {
  title: "About This Page",
  body: ["No specific help is available for this page yet."],
};

/**
 * Resolve the help content for a given pathname + optional query params.
 * Matching is by static prefix + exact segment count, so dynamic segments
 * like `/races/2024/3` match the `/races` (3-segment) entry without caring
 * about the actual year/round values.
 */
export function resolveHelpContent(pathname: string, searchParams: URLSearchParams): HelpContent {
  const segs = segmentCount(pathname);

  // Prefer the most specific (longest prefix) match among candidates with a matching segment count.
  const candidates = ENTRIES.filter(
    (e) => segs === e.segments && (pathname === e.prefix || pathname.startsWith(e.prefix + "/") || pathname === e.prefix),
  ).filter((e) => prefixSegments(e.prefix) <= segs);

  // Also require the prefix's own segments actually match the leading path segments.
  const pathSegs = pathname.split("/").filter(Boolean);
  const matched = candidates.filter((e) => {
    const prefixSegs = e.prefix.split("/").filter(Boolean);
    return prefixSegs.every((seg, i) => pathSegs[i] === seg);
  });

  matched.sort((a, b) => prefixSegments(b.prefix) - prefixSegments(a.prefix));
  const entry = matched[0];
  if (!entry) return FALLBACK_CONTENT;

  if (entry.paramKey && entry.paramContent) {
    const value = searchParams.get(entry.paramKey) ?? "";
    return entry.paramContent[value] ?? entry.content ?? FALLBACK_CONTENT;
  }

  return entry.content ?? FALLBACK_CONTENT;
}
