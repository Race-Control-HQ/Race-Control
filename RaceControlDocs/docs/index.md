---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "RaceControl"
  text: "Formula 1 data, natively on every screen"
  tagline: Native iOS, Android and web apps for exploring F1 history from 2018 to today, with full session results, telemetry and a lap-by-lap race replay, all served by one open FastF1 backend.
  image:
    src: /logo.png
    alt: The RaceControl logo
  actions:
    - theme: brand
      text: Get Started
      link: /getting-started/index
    - theme: alt
      text: API Reference
      link: /api/index
    - theme: alt
      text: Contribute
      link: /contributing/index

features:
  - title: Getting Started
    details: Run the backend and any of the three clients on your own machine, in the right order.
    link: /getting-started/index
    linkText: Run It Locally
  - title: Features
    details: Every screen in the apps, from the season calendar to telemetry and the race replay.
    link: /features/index
    linkText: Take the Tour
  - title: API Reference
    details: Every backend endpoint, including the chart-ready derived analytics.
    link: /api/index
    linkText: Browse the API
  - title: Architecture
    details: How one FastAPI service feeds three independent clients, and how each client is built.
    link: /architecture/index
    linkText: See How It Fits
  - title: Self-Hosting
    details: Deploy the whole stack to Coolify, with environment variables, volumes and device attestation.
    link: /self-hosting/index
    linkText: Host Your Own
  - title: Contributing
    details: Development setup, the checks CI runs, pull request guidelines and the code of conduct.
    link: /contributing/index
    linkText: Join In
  - title: Platform Differences
    details: Where iOS, Android and web deliberately diverge, and the reasoning behind each choice.
    link: /features/platforms
    linkText: Compare Platforms
  - title: Help
    details: Troubleshooting, common questions and where to ask for support.
    link: /help/index
    linkText: Get Help
---

## What is RaceControl?

RaceControl is an open-source project for exploring historical Formula 1 data: drivers, teams, circuits, standings, full session results, telemetry and a lap-by-lap race replay. Data comes from the [FastF1](https://docs.fastf1.dev) library and covers 2018 to the present.

It is one repository holding six pieces:

| Piece | Tech | Folder |
|---|---|---|
| iOS app | Swift, SwiftUI (iOS 17+) | `RaceControlApp/` |
| Android app | Kotlin, Jetpack Compose (Android 8+) | `RaceControlAndroid/` |
| Web app | TypeScript, Next.js | `RaceControlWeb/` |
| Data backend | Python, FastAPI, FastF1 | `backend/` |
| Project site | TypeScript, Next.js | `RaceControlSite/` |
| Documentation | VitePress | `RaceControlDocs/` |

Three ideas run through everything:

- **One backend, three native clients.** FastF1 is a Python library, not a hosted service, so the backend runs it on a server and exposes clean JSON over REST. The iOS, Android and web apps all consume the same API.
- **Native, not ported.** Each client follows its own platform's conventions. Where they differ, the difference is deliberate and [written down](/features/platforms).
- **No accounts, no user keys.** The data is public F1 history. The published apps prove they are genuine with [device attestation](/architecture/authentication) rather than a login or a shared secret.

## Quick Links

- [Run the Backend](/getting-started/backend): the first step for any local setup
- [Core Endpoints](/api/endpoints): the whole REST surface on one page
- [Architecture Overview](/architecture/index): how the pieces talk to each other
- [Development Setup](/contributing/development): the checks to run before you push
- [Self-Hosting](/self-hosting/index): deploy your own copy to Coolify

## Need Help?

See [Troubleshooting](/help/troubleshooting) and [Common Questions](/help/faq), or find out [where to ask](/help/support).
