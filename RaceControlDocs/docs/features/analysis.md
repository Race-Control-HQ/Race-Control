# Race Analysis

Every completed race opens an analysis hub: a grid of tiles, each a deep-dive into one aspect of the race.

![Race detail on iOS, with the analysis grid](/images/ios/ios-race-detail.png){.doc-img .md}

## Telemetry

Speed, throttle and gear traces over a lap, with an optional second driver overlaid for a head-to-head comparison.

The view flags whether the lap you are looking at was run under yellow, safety car, virtual safety car or red flag conditions.

![Telemetry traces on iOS](/images/ios/ios-telemetry.png){.doc-img .md}

## Lap Times

A multi-driver lap-time evolution chart with outlier filtering. Flag and safety-car periods are banded onto the chart, so a slow lap under yellow reads as what it is.

![The lap times chart in the web app](/images/web/web-lap-times.png)

## Tyre Strategy

A stint timeline for each driver, with compound colours and pit-stop counts.

## Qualifying

A Q1, Q2 and Q3 breakdown with gap to pole.

## Weather

Air and track temperatures, humidity, wind and rainfall.

## Retirements

Non-finishers categorised by cause: mechanical, accident or disqualification.

## Flags

A lap-by-lap timeline of every flag and safety-car or virtual-safety-car period issued during the race. Each period shows the lap it started and ended, and the race-control message explaining why.

## Race Control

The full chronological race-control log. Where Flags shows only flag and safety-car messages collapsed into periods, this is every message: DRS enabled and disabled, car events, and investigations, penalties and reprimands, with the driver or drivers involved where the message names one.

## Derived analytics

The backend also serves a set of [derived analytics](/api/analytics) that answer questions the raw data only poses: race trace, tyre degradation, the pit-stop ledger, the qualifying sector waterfall, mini-sector dominance, title permutations and the driver fingerprint.

These arrive chart-ready, so every client draws the same numbers. Not every client has a screen for each one yet. The [Visual Parity Checklist](/contributing/visual-parity) tracks which visualizations exist where.
