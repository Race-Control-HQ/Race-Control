// Central place for every external/deep link the site renders. Everything
// is env-driven with a working fallback so the site builds and looks right
// before any of these are configured in Coolify — swap the env vars in,
// no code changes needed.

export const siteConfig = {
  githubOrg: "https://github.com/Race-Control-HQ",
  githubRepo: "https://github.com/Race-Control-HQ/Race-Control",

  // Public production backend (see ../backend, root README section 1b).
  backendApiUrl: process.env.NEXT_PUBLIC_BACKEND_API_URL ?? "https://racecontrol.owl-media.co.uk",
  backendDocsUrl:
    (process.env.NEXT_PUBLIC_BACKEND_API_URL ?? "https://racecontrol.owl-media.co.uk") + "/docs",
  backendGithubUrl: "https://github.com/Race-Control-HQ/Race-Control/tree/main/backend",

  // The Next.js web client (RaceControlWeb) — the third client of the same backend.
  webAppUrl: process.env.NEXT_PUBLIC_WEB_APP_URL ?? "https://web.getracecontrol.com",
  webAppGithubUrl: "https://github.com/Race-Control-HQ/Race-Control/tree/main/RaceControlWeb",

  iosGithubUrl: "https://github.com/Race-Control-HQ/Race-Control/tree/main/RaceControlApp",
  androidGithubUrl: "https://github.com/Race-Control-HQ/Race-Control/tree/main/RaceControlAndroid",

  // Store listings — set these once the apps are published.
  appStoreUrl: process.env.NEXT_PUBLIC_APP_STORE_URL ?? null,
  playStoreUrl: process.env.NEXT_PUBLIC_PLAY_STORE_URL ?? null,
} as const;
