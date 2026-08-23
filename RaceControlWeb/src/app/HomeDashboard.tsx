"use client";

import clsx from "clsx";
import Link from "next/link";
import { useMemo } from "react";
import { useYearParam } from "@/lib/useYearParam";
import {
  useSchedule,
  useDriverStandings,
  useConstructorStandings,
  useResults,
  useCircuitMap,
  useDrivers,
  useWeather,
} from "@/lib/api";
import { useFavorites } from "@/components/FavoritesProvider";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import { DriverAvatar } from "@/components/DriverAvatar";
import { MiniTrackMap } from "@/components/MiniTrackMap";
import { WdcCalculatorList } from "@/components/WdcCalculatorList";
import { StandingsGapList, type GapRow } from "./StandingsGapList";
import { formatDateTime, formatSessionTime } from "@/lib/format";
import { raceFinished, sessionState, weekendState } from "@/lib/weekend";
import type { DriverStanding, ConstructorStanding, ScheduleEvent, SessionResults, CircuitMap, WeatherResponse } from "@/lib/types";

export function HomeDashboard({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const { data: events, error: scheduleError, isLoading: scheduleLoading } = useSchedule(year);
  const { data: driverStandings, isLoading: driversLoading } = useDriverStandings(year);
  const { data: constructorStandings, isLoading: constructorsLoading } = useConstructorStandings(year);
  // Ergast-derived standings carry no team colour (Ergast doesn't have one);
  // borrow it from the FastF1-backed drivers list, which is fetched for this
  // page anyway, rather than adding a backend merge just for a bar colour.
  const { data: drivers } = useDrivers(year);
  const { favoriteDrivers, favoriteTeams } = useFavorites();

  // The headline race is always the most recently run one: full results,
  // podium, and weather are only available after a race has actually
  // finished, which `raceFinished` decides from the race session time rather
  // than from the backend's coarser `completed` flag.
  const lastRace = useMemo(() => {
    const races = (events ?? []).filter((e) => e.round > 0 && raceFinished(e)).sort((a, b) => a.round - b.round);
    return races[races.length - 1];
  }, [events]);

  // The next race on the calendar, so drivers/fans can see when the sessions
  // for the upcoming weekend actually start. A weekend that is already under
  // way still belongs here until its race has been run — the card labels
  // itself accordingly.
  const nextRace = useMemo(() => {
    const races = (events ?? []).filter((e) => e.round > 0 && !raceFinished(e)).sort((a, b) => a.round - b.round);
    return races[0];
  }, [events]);

  const { data: lastRaceResults, isLoading: lastRaceLoading } = useResults(year, lastRace?.round ?? null, "R");
  const { data: lastRaceCircuit, isLoading: lastCircuitLoading } = useCircuitMap(year, lastRace?.round ?? null);
  const { data: lastRaceWeather, isLoading: weatherLoading } = useWeather(year, lastRace?.round ?? null, "R");

  const teamColorById = useMemo(() => {
    const map = new Map<string, string | null>();
    for (const d of drivers ?? []) {
      if (d.teamId && !map.has(d.teamId)) map.set(d.teamId, d.teamColor);
    }
    return map;
  }, [drivers]);
  const driverColorById = useMemo(() => {
    const map = new Map<string, string | null>();
    for (const d of drivers ?? []) map.set(d.driverId, d.teamColor);
    return map;
  }, [drivers]);

  const driverRows: GapRow[] = (driverStandings ?? []).slice(0, 10).map((d) => ({
    key: d.driverId,
    href: `/drivers/${year}/${d.driverId}`,
    position: d.position,
    logoUrl: d.teamLogoUrl,
    logoName: d.teamName,
    label: `${d.givenName ?? ""} ${d.familyName ?? ""}`.trim(),
    points: d.points ?? 0,
    color: driverColorById.get(d.driverId) ?? null,
  }));

  const teamRows: GapRow[] = (constructorStandings ?? []).map((t) => ({
    key: t.teamId,
    href: `/teams/${year}/${t.teamId}`,
    position: t.position,
    logoUrl: t.teamLogoUrl,
    logoName: t.teamName,
    label: t.teamName ?? "-",
    points: t.points ?? 0,
    color: teamColorById.get(t.teamId) ?? null,
  }));

  const favoriteDriverRows = (driverStandings ?? []).filter((d) => favoriteDrivers.has(d.driverId));
  const favoriteTeamRows = (constructorStandings ?? []).filter((t) => favoriteTeams.has(t.teamId));
  const hasFavorites = favoriteDriverRows.length > 0 || favoriteTeamRows.length > 0;

  if (scheduleError) return <ErrorState message="Couldn't load the season overview." />;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{year} Season</h1>
          <p className="text-sm text-muted">A snapshot of where the championship stands.</p>
        </div>
        <SeasonPicker year={year} onChange={setYear} />
      </div>

      {scheduleLoading ? (
        <LoadingState label="Loading season overview…" />
      ) : (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <MostRecentRaceCard
              event={lastRace}
              year={year}
              circuit={lastRaceCircuit}
              circuitLoading={lastCircuitLoading}
              weather={lastRaceWeather}
              weatherLoading={weatherLoading}
            />
            <PodiumCard event={lastRace} year={year} results={lastRaceResults} isLoading={lastRaceLoading} />
          </div>

          {nextRace && <UpcomingRaceCard event={nextRace} year={year} />}

          {hasFavorites && <FavoritesStrip year={year} drivers={favoriteDriverRows} teams={favoriteTeamRows} />}

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <StandingsGapList
              title="Driver Standings"
              href={`/standings?year=${year}`}
              isLoading={driversLoading}
              rows={driverRows}
            />
            <StandingsGapList
              title="Constructor Standings"
              href={`/standings?year=${year}&mode=constructors`}
              isLoading={constructorsLoading}
              rows={teamRows}
            />
          </div>

          <div className="rounded-lg border border-border bg-surface p-5">
            <div className="mb-1 flex items-center justify-between">
              <p className="text-xs font-medium uppercase tracking-wide text-muted">Who Can Still Win the WDC?</p>
              <Link href={`/standings?year=${year}&mode=wdc`} className="text-xs font-medium text-muted hover:text-foreground">
                View all →
              </Link>
            </div>
            <WdcCalculatorList year={year} limit={10} moreHref={`/standings?year=${year}&mode=wdc`} />
          </div>

          <QuickLinks year={year} />
        </div>
      )}
    </div>
  );
}

function MostRecentRaceCard({
  event,
  year,
  circuit,
  circuitLoading,
  weather,
  weatherLoading,
}: {
  event: ScheduleEvent | undefined;
  year: number;
  circuit: CircuitMap | undefined;
  circuitLoading: boolean;
  weather: WeatherResponse | undefined;
  weatherLoading: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-border bg-surface p-5">
      <div className="flex min-w-0 flex-1 flex-col justify-between self-stretch">
        <div>
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Most Recent Race</p>
          {event ? (
            <>
              <p className="truncate text-lg font-bold">{event.name}</p>
              <p className="truncate text-sm text-muted">
                {event.location}, {event.country}
              </p>
              <p className="tabular mt-1 text-sm text-muted">
                {formatDateTime(event.sessions.find((s) => s.identifier === "R")?.date ?? event.date)}
              </p>
              <WeatherSummary weather={weather} isLoading={weatherLoading} />
              {circuitLoading && (
                <p className="mt-2 max-w-xs text-xs text-muted/70">
                  First load can take up to a minute while race data is fetched fresh; it&apos;s cached and much
                  faster after that.
                </p>
              )}
            </>
          ) : (
            <p className="text-sm text-muted">No races completed yet this season.</p>
          )}
        </div>
        {event && (
          <Link
            href={`/races/${year}/${event.round}`}
            className="mt-4 inline-block w-fit rounded-md bg-racing-red px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
          >
            View Full Results
          </Link>
        )}
      </div>
      {event && (
        <div className="shrink-0 text-foreground/80">
          <MiniTrackMap circuit={circuit} isLoading={circuitLoading} className="h-24 w-24" />
        </div>
      )}
    </div>
  );
}

function WeatherSummary({ weather, isLoading }: { weather: WeatherResponse | undefined; isLoading: boolean }) {
  if (isLoading) return <p className="mt-2 text-xs text-muted">Loading weather…</p>;
  if (!weather || !weather.available) return null;

  const parts = [
    weather.airTemp != null ? `${weather.airTemp}°C air` : null,
    weather.trackTemp != null ? `${weather.trackTemp}°C track` : null,
    weather.rainfall ? "rain" : null,
  ].filter(Boolean);
  if (parts.length === 0) return null;

  return (
    <p className="mt-2 flex items-center gap-1.5 text-xs text-muted">
      <span aria-hidden>{weather.rainfall ? "🌧️" : "☀️"}</span>
      {parts.join(" · ")}
    </p>
  );
}

function UpcomingRaceCard({ event, year }: { event: ScheduleEvent; year: number }) {
  // A weekend that has already started is still the next race — the grand
  // prix hasn't been run — but calling it "Next Race" hides the fact that
  // practice and qualifying are done and their results are there to read.
  const state = weekendState(event);
  const raceDate = event.sessions.find((s) => s.identifier === "R")?.date ?? event.date;
  const daysAway = daysUntil(raceDate);
  // The race page opens on the most recent session that has run, so the link
  // is worth offering from the moment anything has — but not before, when it
  // would only ever land on an empty classification.
  const anySessionRun = event.sessions.some((s) => sessionState(s) === "done");

  return (
    <div className="rounded-lg border border-border bg-surface p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted">
          {state === "live" ? "This Weekend" : "Next Race"}
        </p>
        {state === "live" ? (
          <span className="rounded-full bg-racing-red px-2.5 py-1 text-xs font-semibold text-white">Under way</span>
        ) : (
          daysAway != null && (
            <span className="rounded-full bg-racing-red/10 px-2.5 py-1 text-xs font-semibold text-racing-red">
              {daysAway <= 0 ? "This weekend" : `In ${daysAway} day${daysAway === 1 ? "" : "s"}`}
            </span>
          )
        )}
      </div>
      <p className="truncate text-lg font-bold">{event.name}</p>
      <p className="truncate text-sm text-muted">
        {event.location}, {event.country}
      </p>

      {event.sessions.length === 0 ? (
        <p className="mt-3 text-sm text-muted">Session times aren&apos;t available yet.</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-border">
          {event.sessions.map((s) => {
            const sState = sessionState(s);
            return (
              <li
                key={s.identifier}
                className={clsx(
                  "flex items-center justify-between gap-3 py-2 text-sm first:pt-0 last:pb-0",
                  sState === "done" && "text-muted",
                )}
              >
                <span className={sState === "done" ? undefined : "font-medium"}>{s.name ?? s.identifier}</span>
                <span className="flex shrink-0 items-center gap-2">
                  {sState === "live" && (
                    <span className="rounded-full bg-racing-red px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-white">
                      Live
                    </span>
                  )}
                  {sState === "done" && <span className="text-xs text-muted">Done</span>}
                  <span className="tabular text-muted">{formatSessionTime(s.date)}</span>
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {state === "live" && anySessionRun && (
        <Link
          href={`/races/${year}/${event.round}`}
          className="mt-4 inline-block w-fit rounded-md bg-racing-red px-4 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          View Weekend Results
        </Link>
      )}
    </div>
  );
}

function daysUntil(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const target = new Date(iso);
  if (Number.isNaN(target.getTime())) return null;
  const msPerDay = 24 * 60 * 60 * 1000;
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target.getTime() - startOfToday.getTime()) / msPerDay);
}

function PodiumCard({
  event,
  year,
  results,
  isLoading,
}: {
  event: ScheduleEvent | undefined;
  year: number;
  results: SessionResults | undefined;
  isLoading: boolean;
}) {
  const podium = (results?.results ?? []).slice(0, 3);

  return (
    <div className="rounded-lg border border-border bg-surface p-5">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Podium</p>
        {event && (
          <Link href={`/races/${year}/${event.round}`} className="text-xs font-medium text-muted hover:text-foreground">
            Full results →
          </Link>
        )}
      </div>
      {!event ? (
        <p className="text-sm text-muted">No races completed yet this season.</p>
      ) : isLoading ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : podium.length === 0 ? (
        <p className="text-sm text-muted">Results aren&apos;t available yet.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {podium.map((r, i) => (
            <li key={r.driverId ?? `${i}`} className="flex items-center gap-3">
              <span className="tabular w-4 shrink-0 text-sm font-bold text-muted">{i + 1}</span>
              <DriverAvatar src={r.headshotUrl} name={r.fullName ?? r.abbreviation ?? ""} sizeClassName="h-9 w-9" />
              <div className="min-w-0 flex-1">
                {r.driverId ? (
                  <Link href={`/drivers/${year}/${r.driverId}`} className="block truncate text-sm font-medium hover:text-racing-red">
                    {r.fullName ?? r.abbreviation}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-medium">{r.fullName ?? r.abbreviation}</p>
                )}
                <p className="truncate text-xs text-muted">{r.teamName}</p>
              </div>
              <TeamColorDot color={r.teamColor} />
              <TeamLogo src={r.teamLogoUrl} name={r.teamName} sizeClassName="h-5 w-5" />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function FavoritesStrip({
  year,
  drivers,
  teams,
}: {
  year: number;
  drivers: DriverStanding[];
  teams: ConstructorStanding[];
}) {
  return (
    <div className="rounded-lg border border-border bg-surface p-5">
      <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted">Your Favorites</p>
      <div className="flex flex-wrap gap-2">
        {drivers.map((d) => (
          <Link
            key={d.driverId}
            href={`/drivers/${year}/${d.driverId}`}
            className="flex items-center gap-2 rounded-full border border-border bg-surface-raised px-3 py-1.5 text-sm transition-colors hover:border-foreground"
          >
            <span className="font-medium">
              {d.givenName} {d.familyName}
            </span>
            <span className="tabular text-muted">P{d.position ?? "-"}</span>
          </Link>
        ))}
        {teams.map((t) => (
          <Link
            key={t.teamId}
            href={`/teams/${year}/${t.teamId}`}
            className="flex items-center gap-2 rounded-full border border-border bg-surface-raised px-3 py-1.5 text-sm transition-colors hover:border-foreground"
          >
            <TeamLogo src={t.teamLogoUrl} name={t.teamName} sizeClassName="h-4 w-4" />
            <span className="font-medium">{t.teamName}</span>
            <span className="tabular text-muted">P{t.position ?? "-"}</span>
          </Link>
        ))}
      </div>
    </div>
  );
}

function QuickLinks({ year }: { year: number }) {
  const links = [
    { href: `/schedule?year=${year}`, label: "Schedule" },
    { href: `/circuits?year=${year}`, label: "Circuits" },
    { href: `/drivers?year=${year}`, label: "Drivers" },
    { href: `/teams?year=${year}`, label: "Teams" },
    { href: `/drivers/compare?year=${year}`, label: "Head-to-Head" },
    { href: `/standings?year=${year}&mode=reliability`, label: "Reliability" },
    { href: `/standings?year=${year}&mode=wdc`, label: "Title Decider" },
  ];
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
      {links.map((l) => (
        <Link
          key={l.label}
          href={l.href}
          className="rounded-lg border border-border bg-surface px-3 py-3 text-center text-sm font-medium transition-colors hover:bg-surface-raised"
        >
          {l.label}
        </Link>
      ))}
    </div>
  );
}
