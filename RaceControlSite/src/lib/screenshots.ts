export type ScreenshotFrame = "phone" | "browser";

export type ScreenshotMeta = {
  /** Path under /public. */
  src: string;
  width: number;
  height: number;
  frame: ScreenshotFrame;
  platform: "iOS" | "Web";
  alt: string;
  caption: string;
};

/**
 * Real RaceControl product captures used throughout the marketing site.
 *
 * Mobile captures are currently from the iOS app. Keep the platform label on
 * every figure until an equivalent Android capture set is available.
 */
export const SCREENSHOTS = {
  iosSchedule: {
    src: "/screenshots/ios-schedule.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "RaceControl on iOS showing the 2026 Formula 1 season calendar and the upcoming Dutch Grand Prix.",
    caption: "Season calendar and next race",
  },
  iosRaceDetail: {
    src: "/screenshots/ios-race-detail.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "Australian Grand Prix detail screen on iOS with the weekend schedule and analysis tools.",
    caption: "Race weekend and analysis hub",
  },
  iosTelemetry: {
    src: "/screenshots/ios-telemetry.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "RaceControl telemetry playback on iOS with driver selection, circuit position, controls and speed trace.",
    caption: "Interactive telemetry playback",
  },
  iosTrackMap: {
    src: "/screenshots/ios-track-map.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "Albert Park circuit detail on iOS with a speed-coloured track map, numbered corners and DRS zones.",
    caption: "Circuit map, corners and DRS zones",
  },
  iosStandings: {
    src: "/screenshots/ios-standings.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "RaceControl driver championship standings on iOS with points, wins and gap bars.",
    caption: "Drivers’ championship standings",
  },
  iosConstructors: {
    src: "/screenshots/ios-constructors.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "RaceControl constructors list on iOS showing teams, drivers and championship points.",
    caption: "Constructors, drivers and points",
  },
  iosDrivers: {
    src: "/screenshots/ios-drivers.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "RaceControl driver directory on iOS with portraits, teams, numbers, nationalities and points.",
    caption: "Driver directory and favourites",
  },
  iosRaceClassification: {
    src: "/screenshots/ios-race-classification.png",
    width: 1206,
    height: 2622,
    frame: "phone",
    platform: "iOS",
    alt: "Australian Grand Prix classification on iOS showing drivers, teams, times and position changes.",
    caption: "Race classification and position changes",
  },
  webDashboard: {
    src: "/screenshots/web-dashboard.png",
    width: 2940,
    height: 3986,
    frame: "browser",
    platform: "Web",
    alt: "RaceControl web dashboard with the latest result, next race, standings and title eligibility.",
    caption: "Season dashboard",
  },
  webResults: {
    src: "/screenshots/web-results.png",
    width: 2940,
    height: 2554,
    frame: "browser",
    platform: "Web",
    alt: "Hungarian Grand Prix results in the RaceControl web client.",
    caption: "Full race classification",
  },
  webLapTimes: {
    src: "/screenshots/web-lap-times.png",
    width: 2940,
    height: 2004,
    frame: "browser",
    platform: "Web",
    alt: "RaceControl web lap-time chart comparing three drivers and highlighting a safety-car period.",
    caption: "Multi-driver lap-time comparison",
  },
  webTrackMap: {
    src: "/screenshots/web-track-map.png",
    width: 2940,
    height: 4876,
    frame: "browser",
    platform: "Web",
    alt: "Barcelona circuit analysis in the RaceControl web client with track map, elevation and corner speeds.",
    caption: "Circuit geometry and corner analysis",
  },
  webDrivers: {
    src: "/screenshots/web-drivers.png",
    width: 2940,
    height: 2300,
    frame: "browser",
    platform: "Web",
    alt: "RaceControl web driver directory with teams, championship positions and points.",
    caption: "Driver directory",
  },
} satisfies Record<string, ScreenshotMeta>;

export type ScreenshotId = keyof typeof SCREENSHOTS;
