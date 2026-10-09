# Common Questions

## Is RaceControl official?

No. It is an unofficial project and is not associated with the Formula 1 companies. F1, FORMULA 1 and related marks are trademarks of Formula One Licensing BV.

## Where does the data come from?

From the [FastF1](https://docs.fastf1.dev) library, which sources it from the F1 live-timing API and the Ergast/Jolpica database. The project site's Open Source page gives full attribution.

## Which seasons are covered?

2018 to the present.

## Is it live timing?

No. RaceControl explores historical data: completed sessions, results, telemetry and replays of races that have been run.

## Do I need an account?

No. There are no user accounts. The data is public F1 history.

## Do I need an API key to use the apps?

No. The published apps prove they are genuine through [device attestation](/architecture/authentication). There is nothing to enter.

## Why can't I just call the production API from my own code?

The production backend only accepts requests from genuine installs of the apps, or with the admin token. That stops abuse and scraping of a service that is expensive to run.

The backend is open source, so you can [run your own](/getting-started/backend) in a couple of minutes, with no auth at all.

## Do my favourites sync between devices?

No. Favourites are stored on the device, or in the browser's `localStorage` on the web. With no accounts there is nowhere to sync them to.

## Why does the web app not have session reminders?

There is no clean web equivalent without a Service Worker and push infrastructure the backend does not have. It was deliberately left out.

## Why do the iOS and Android apps look different?

On purpose. Each follows its own platform's conventions rather than one being a port of the other. See [Platform Differences](/features/platforms).

## Why is there no light theme?

The palette is tuned for OLED and built around official F1 team and tyre colours, which carry meaning. The apps are dark only by design.

## Why does the Android app ignore my wallpaper colours?

Material You dynamic colour would collide with the team and tyre colours the app uses to convey meaning, so it is switched off.

## Can I run my own copy?

Yes. See [Self-Hosting](/self-hosting/index). Only the backend is required.

## Why must the backend run a single worker?

Its caches are held in process memory and are not shared between workers. See [Single worker only](/self-hosting/backend#single-worker-only).

## Can I contribute?

Yes, please. Start with [Contributing](/contributing/index).

## What licence is it under?

The [MIT License](https://github.com/Race-Control-HQ/Race-Control/blob/main/LICENSE).
