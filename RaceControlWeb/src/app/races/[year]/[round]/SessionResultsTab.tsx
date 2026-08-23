"use client";

import clsx from "clsx";
import { useQueryParam } from "@/lib/useQueryParam";
import { useResults } from "@/lib/api";
import { LoadingState, ErrorState } from "@/components/StateViews";
import { sessionState } from "@/lib/weekend";
import type { EventSession, SessionResults } from "@/lib/types";
import { ResultsTable, type ResultsVariant } from "./ResultsTable";

/**
 * Sessions that produce a classification worth showing, and the short label
 * each one gets in the picker. Anything else on the schedule feed (and any
 * identifier we don't recognise) is left out rather than offering a tab that
 * can only ever be empty.
 */
const SESSION_LABELS: Record<string, string> = {
  FP1: "FP1",
  FP2: "FP2",
  FP3: "FP3",
  SQ: "Sprint Quali",
  SS: "Sprint Quali",
  S: "Sprint",
  Q: "Qualifying",
  R: "Race",
};

/** Which set of columns a session's classification needs; see `ResultsTable`. */
function variantFor(identifier: string): ResultsVariant {
  if (identifier === "Q" || identifier === "SQ" || identifier === "SS") return "qualifying";
  if (identifier.startsWith("FP")) return "bestLap";
  return "race";
}

function resultSessions(sessions: EventSession[]): EventSession[] {
  return sessions.filter((s) => s.identifier in SESSION_LABELS);
}

/**
 * Which session to open on.
 *
 * The race, once it has been run — but mid-weekend there isn't one yet, and
 * landing on an empty race classification hides the practice and qualifying
 * results that *are* there. The feed lists sessions in weekend order, so the
 * last one that has finished is the most recent thing to have happened.
 */
function defaultSession(sessions: EventSession[]): string {
  const run = sessions.filter((s) => sessionState(s) === "done");
  return run[run.length - 1]?.identifier ?? "R";
}

export function SessionResultsTab({
  year,
  round,
  sessions,
  initialResults,
  sessionOverride,
}: {
  year: number;
  round: number;
  sessions: EventSession[];
  /** Server-rendered race classification, or null if the race hasn't run. */
  initialResults: SessionResults | null;
  /** Forces a session regardless of the query param; see `RaceDetailClient`. */
  sessionOverride?: string;
}) {
  const available = resultSessions(sessions);
  const fallback = defaultSession(available);
  const [param, setParam] = useQueryParam("session", fallback);
  const requested = sessionOverride ?? param;
  // A hand-typed or stale `?session=` shouldn't strand the page on a session
  // this weekend never had.
  const active = available.some((s) => s.identifier === requested) ? requested : fallback;

  // The race classification is already in hand from the server, so don't
  // fetch it again; every other session is loaded on demand.
  const useInitial = active === "R" && initialResults != null;
  const { data, error, isLoading } = useResults(year, useInitial ? null : round, active);
  const results = useInitial ? initialResults : data;
  const label = SESSION_LABELS[active] ?? active;

  return (
    <div className="flex flex-col gap-4">
      {available.length > 1 && (
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Session">
          {available.map((s) => {
            const upcoming = sessionState(s) === "upcoming";
            return (
              <button
                key={s.identifier}
                type="button"
                role="tab"
                aria-selected={active === s.identifier}
                disabled={upcoming}
                onClick={() => setParam(s.identifier)}
                title={upcoming ? `${s.name} hasn't run yet` : undefined}
                className={clsx(
                  "rounded-full border px-3 py-1 text-xs font-semibold transition-colors",
                  active === s.identifier
                    ? "border-racing-red bg-racing-red/10 text-racing-red"
                    : "border-border text-muted hover:text-foreground",
                  upcoming && "cursor-not-allowed opacity-40 hover:text-muted",
                )}
              >
                {SESSION_LABELS[s.identifier]}
              </button>
            );
          })}
        </div>
      )}

      {isLoading ? (
        <LoadingState label={`Loading ${label}…`} />
      ) : error ? (
        <ErrorState message={`${label} results aren’t available yet.`} />
      ) : results ? (
        <ResultsTable data={results} year={year} variant={variantFor(active)} />
      ) : null}
    </div>
  );
}
