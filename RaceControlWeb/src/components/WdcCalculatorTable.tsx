"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSchedule, useWdcCalculator, warmWdcCalculatorCache } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import { WdcPointsBreakdown } from "@/components/WdcPointsBreakdown";
import { TitleScenarioMatrix } from "@/components/TitleScenarioMatrix";
import type { WdcCalculator, WdcDriver } from "@/lib/types";

const CAN_WIN_COLOR = "#22c55e";

/**
 * Full "who can still win the WDC" table for the Standings page's Title
 * Decider tab, formatted like the other data tables in the app (Results,
 * Qualifying) with column headers, rather than the dashboard card's compact
 * row-list, since this is the detail view rather than a preview.
 *
 * Includes a "time machine" round scrubber: once a season wraps up, the live
 * calculator collapses to "only the leader could win" for every round, which
 * isn't interesting to look back on. Scrubbing to an earlier round replays
 * the calculator using that round's actual cumulative points instead, so you
 * can see how the title picture genuinely evolved.
 *
 * Two things keep the drag itself smooth, mirroring the mobile apps'
 * onEditingChanged/onValueChangeFinished pattern:
 *  - Dragging only updates a local, un-fetched `dragRound` (the label and
 *    thumb track the pointer instantly); the actual round only "commits"
 *    (and triggers a fetch) once the pointer/key interaction ends, so
 *    dragging across ten rounds is one fetch, not ten.
 *  - The table keeps rendering the last-loaded data while a newly committed
 *    round is in flight, rather than unmounting to a full-page spinner. That
 *    unmount/remount on every round change is what made this feel like "the
 *    page refreshes" instead of a smooth scrub.
 *
 * See WdcCalculatorList.tsx for the method this is built on (FastF1's own
 * "who can still win the WDC" worked example) and its caveats.
 */
export function WdcCalculatorTable({ year }: { year: number }) {
  const [committedRound, setCommittedRound] = useState<number | null>(null);
  const [dragRound, setDragRound] = useState<number | null>(null);
  const { data, isLoading, error } = useWdcCalculator(year, committedRound);
  const { data: schedule } = useSchedule(year);

  // Keep showing the most recent successfully-loaded snapshot while a newly
  // committed round is still in flight, instead of blanking the whole table.
  // Adjusted directly during render rather than in an effect (React's own
  // "adjusting state during rendering" pattern), since this is deriving
  // state from a prop change, not synchronizing with an external system.
  const [lastData, setLastData] = useState<WdcCalculator | null>(null);
  if (data && data !== lastData) {
    setLastData(data);
  }
  const displayData = data ?? lastData;
  const isRefreshing = isLoading && displayData != null;

  const completedRounds = (schedule ?? [])
    .filter((e) => e.completed)
    .slice()
    .sort((a, b) => a.round - b.round);
  const lastCompletedRound = completedRounds.at(-1)?.round ?? 0;
  const isLive = committedRound === null;
  const sliderRound = dragRound ?? committedRound ?? lastCompletedRound;
  const viewingEvent = !isLive ? completedRounds.find((e) => e.round === sliderRound) : null;

  // Warm the backend's shared per-season cache as soon as we know the season
  // has a history to scrub through, rather than waiting for the user's first
  // slider drag to pay for it. One request for any round is enough: the
  // backend computes the whole season's progression once and reuses it for
  // every round, so this warms the entire slider, not just this one round.
  const warmedYearRef = useRef<number | null>(null);
  useEffect(() => {
    if (lastCompletedRound === 0 || warmedYearRef.current === year) return;
    warmedYearRef.current = year;
    warmWdcCalculatorCache(year, lastCompletedRound);
  }, [year, lastCompletedRound]);

  const commitRound = (value: number) => {
    setDragRound(null);
    setCommittedRound(value);
  };

  if (!displayData && isLoading) return <LoadingState label="Loading title-decider…" />;
  if (!displayData && error) return <ErrorState message="Couldn't load title-decider data." />;
  if (!displayData || displayData.drivers.length === 0) return <EmptyState message="No standings available yet." />;

  const canWinCount = displayData.drivers.filter((d) => d.canWin).length;

  return (
    <div>
      {lastCompletedRound > 0 && (
        <div className="mb-4 rounded-lg border border-border bg-surface p-3">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
            <span className="text-sm font-medium">
              {isLive
                ? "Viewing: live standings"
                : `Viewing: as of Round ${sliderRound}${viewingEvent ? ` (${viewingEvent.name})` : ""}`}
              {isRefreshing && <span className="ml-2 text-xs font-normal text-muted">Updating…</span>}
            </span>
            {!isLive && (
              <button
                type="button"
                onClick={() => {
                  setDragRound(null);
                  setCommittedRound(null);
                }}
                className="rounded-md bg-surface-raised px-2 py-1 text-xs font-semibold text-racing-red hover:bg-border"
              >
                Jump to live
              </button>
            )}
          </div>
          <input
            type="range"
            min={1}
            max={lastCompletedRound}
            value={sliderRound}
            onChange={(e) => setDragRound(Number(e.target.value))}
            onMouseUp={(e) => commitRound(Number(e.currentTarget.value))}
            onTouchEnd={(e) => commitRound(Number(e.currentTarget.value))}
            onKeyUp={(e) => commitRound(Number(e.currentTarget.value))}
            className="w-full accent-racing-red"
            aria-label="Round"
          />
          {/*
            With few rounds run so far (a season can have as few as 1-2
            completed early on), the slider's handful of steps are spread
            across the full track width, so a small drag jumps a
            disproportionately large visual gap between adjacent rounds,
            which reads as janky rather than intentional. A row of clickable
            per-round markers makes that discreteness explicit (it's a
            timeline of specific races, not a continuous scale) and gives a
            precise tap target for any round instead of trying to land a
            drag exactly on it. Each marker also carries its round number so
            you can see at a glance what each interval represents without
            having to hover for the tooltip.
          */}
          <div className="mt-2 flex items-start justify-between">
            {completedRounds.map((e, i) => {
              const isPast = e.round < sliderRound;
              const isCurrent = e.round === sliderRound;
              // Thin the numeric labels once there are too many rounds to
              // print every one legibly, but always keep the current round
              // and the very first/last labelled so the ends of the scale
              // are never ambiguous.
              const labelStride = completedRounds.length > 15 ? Math.ceil(completedRounds.length / 12) : 1;
              const showLabel = isCurrent || i === 0 || i === completedRounds.length - 1 || i % labelStride === 0;
              return (
                <button
                  key={e.round}
                  type="button"
                  title={`Round ${e.round}: ${e.name}`}
                  aria-label={`Jump to Round ${e.round}, ${e.name}`}
                  onClick={() => commitRound(e.round)}
                  className="group flex flex-1 flex-col items-center gap-1 py-1"
                >
                  <span
                    className={`rounded-full transition-all ${
                      isCurrent
                        ? "h-2.5 w-2.5 bg-racing-red"
                        : isPast
                          ? "h-1.5 w-1.5 bg-racing-red/50 group-hover:bg-racing-red/80"
                          : "h-1.5 w-1.5 bg-border group-hover:bg-muted"
                    }`}
                  />
                  <span
                    className={`tabular text-[10px] leading-none ${
                      isCurrent ? "font-semibold text-racing-red" : "text-muted"
                    } ${showLabel ? "" : "invisible"}`}
                  >
                    {e.round}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}

      <p className="mb-1 text-sm text-muted">
        {displayData.decided
          ? displayData.roundsRemaining === 0
            ? "Season complete. The title is decided."
            : "Mathematically settled. Only the leader can still win."
          : `${displayData.roundsRemaining} round${displayData.roundsRemaining === 1 ? "" : "s"} left · up to ${displayData.maxRemainingPoints} points still on offer · ${canWinCount} driver${canWinCount === 1 ? "" : "s"} can still win`}
      </p>
      <p className="mb-4 text-xs text-muted/70">
        &quot;Can win&quot; is the best-case ceiling (winning every remaining session while the leader
        scores nothing else), not a realistic forecast.
      </p>

      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-3 py-2 font-medium">Pos</th>
              <th className="px-3 py-2 font-medium">Driver</th>
              <th className="px-3 py-2 font-medium">Team</th>
              <th className="tabular px-3 py-2 text-right font-medium">Points</th>
              <th className="tabular px-3 py-2 text-right font-medium">Behind Leader</th>
              <th className="tabular px-3 py-2 text-right font-medium">Max Possible</th>
              <th className="px-3 py-2 text-right font-medium">Title Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {displayData.drivers.map((d, i) => (
              <WdcTableRow key={d.driverId ?? d.driverCode ?? `${d.position}-${i}`} driver={d} year={year} />
            ))}
          </tbody>
        </table>
      </div>

      <WdcPointsBreakdown />
      <TitleScenarioMatrix year={year} throughRound={committedRound} />
    </div>
  );
}

function WdcTableRow({ driver: d, year }: { driver: WdcDriver; year: number }) {
  return (
    <tr className="hover:bg-surface/60">
      <td className="tabular px-3 py-2 text-muted">{d.position ?? "-"}</td>
      <td className="px-3 py-2 font-medium">
        {d.driverId ? (
          <Link href={`/drivers/${year}/${d.driverId}`} className="hover:text-racing-red">
            {d.givenName} {d.familyName}
          </Link>
        ) : (
          `${d.givenName ?? ""} ${d.familyName ?? ""}`.trim() || "-"
        )}
      </td>
      <td className="px-3 py-2 text-muted">
        <span className="inline-flex items-center gap-2">
          <TeamColorDot color={d.teamColor} />
          <TeamLogo src={d.teamLogoUrl} name={d.teamName} sizeClassName="h-4 w-4" />
          {d.teamName ?? "-"}
        </span>
      </td>
      <td className="tabular px-3 py-2 text-right font-semibold">{d.points}</td>
      <td className="tabular px-3 py-2 text-right text-muted">{d.pointsBehindLeader > 0 ? `-${d.pointsBehindLeader}` : "Leader"}</td>
      <td className="tabular px-3 py-2 text-right text-muted">{d.maxPoints}</td>
      <td className="px-3 py-2 text-right">
        <CanWinPill canWin={d.canWin} />
      </td>
    </tr>
  );
}

/**
 * A custom hover tooltip rather than the native `title` attribute: `title`
 * on an element nested inside a link/row is unreliable across browsers, so
 * this renders its own, which shows immediately and consistently.
 */
function CanWinPill({ canWin }: { canWin: boolean }) {
  return (
    <span className="group relative inline-flex shrink-0">
      <span
        className="rounded-full px-2 py-0.5 text-xs font-semibold"
        style={
          canWin
            ? { backgroundColor: `${CAN_WIN_COLOR}26`, color: CAN_WIN_COLOR }
            : { backgroundColor: "var(--surface-raised)", color: "var(--muted)" }
        }
      >
        {canWin ? "Can win" : "Can't win"}
      </span>
      <span
        role="tooltip"
        className="pointer-events-none absolute bottom-full right-0 z-10 mb-1.5 w-44 rounded-md border border-border bg-surface-raised px-2 py-1.5 text-[11px] leading-snug text-muted opacity-0 shadow-lg transition-opacity group-hover:opacity-100"
      >
        {canWin
          ? "Could still overtake the leader in the best possible case."
          : "No longer mathematically possible, even in the best case."}
      </span>
    </span>
  );
}
