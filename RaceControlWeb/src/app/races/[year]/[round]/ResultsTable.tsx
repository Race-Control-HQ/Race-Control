"use client";

import Link from "next/link";
import { formatMs } from "@/lib/format";
import { TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import type { ResultRow, SessionResults } from "@/lib/types";

/**
 * The displayed finishing/grid position.
 *
 * `classifiedPosition` is preferred (it carries "R"/"D"/"NC" style outcomes
 * for races), but FastF1 leaves it *blank* for non-race sessions. A blank
 * string is not null, so `??` sails straight past it and renders an empty
 * cell, which is why the qualifying "Pos" column showed nothing even though
 * `position` was populated. Treat blank as missing.
 */
function displayPosition(r: ResultRow): string {
  const classified = r.classifiedPosition?.trim();
  if (classified) return classified;
  return r.position != null ? String(r.position) : "-";
}

/**
 * How a session classifies, which decides the columns:
 *  - `race`: finishing order, with grid, status, race time and points;
 *  - `qualifying`: the segment times, Q1/Q2/Q3 (SQ1/SQ2/SQ3 for a sprint);
 *  - `bestLap`: best lap, gap to the fastest, and laps run — practice, where
 *    nobody finishes anything and there's only ever a timesheet.
 */
export type ResultsVariant = "race" | "qualifying" | "bestLap";

export function ResultsTable({
  data,
  year,
  variant = "race",
}: {
  data: SessionResults;
  year: number;
  variant?: ResultsVariant;
}) {
  // Sprint qualifying runs the same three-segment format under its own names.
  const segment = data.session === "SQ" || data.session === "SS" ? "SQ" : "Q";

  if (data.results.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border px-4 py-10 text-center text-sm text-muted">
        No results available yet.
      </div>
    );
  }

  return (
    <div className="table-scroll rounded-lg border border-border">
      <table className="w-full min-w-[720px] text-sm">
        <thead className="bg-surface text-left text-xs uppercase tracking-wide text-muted">
          <tr>
            <th className="px-3 py-2 font-medium">Pos</th>
            <th className="px-3 py-2 font-medium">Driver</th>
            <th className="px-3 py-2 font-medium">Team</th>
            {variant === "qualifying" && (
              <>
                <th className="tabular px-3 py-2 text-right font-medium">{segment}1</th>
                <th className="tabular px-3 py-2 text-right font-medium">{segment}2</th>
                <th className="tabular px-3 py-2 text-right font-medium">{segment}3</th>
              </>
            )}
            {variant === "bestLap" && (
              <>
                <th className="tabular px-3 py-2 text-right font-medium">Best Lap</th>
                <th className="tabular px-3 py-2 text-right font-medium">Gap</th>
                <th className="tabular px-3 py-2 text-right font-medium">Laps</th>
              </>
            )}
            {variant === "race" && (
              <>
                <th className="tabular px-3 py-2 text-right font-medium">Grid</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="tabular px-3 py-2 text-right font-medium">Time</th>
                <th className="tabular px-3 py-2 text-right font-medium">Pts</th>
              </>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {data.results.map((r, i) => (
            // `driverId`/`driverNumber` can both be missing or duplicated for
            // reserve/no-time entries (and some upstream rows arrive with a
            // literal "nan" string rather than a real value); fold in the
            // row index so the key is always unique regardless of what the
            // data actually contains.
            <tr key={`${r.driverId ?? r.driverNumber ?? "row"}-${i}`} className="hover:bg-surface/60">
              <td className="tabular px-3 py-2 text-muted">{displayPosition(r)}</td>
              <td className="px-3 py-2 font-medium">
                {r.driverId ? (
                  <Link href={`/drivers/${year}/${r.driverId}`} className="hover:text-racing-red">
                    {r.fullName ?? r.abbreviation}
                  </Link>
                ) : (
                  (r.fullName ?? r.abbreviation)
                )}
              </td>
              <td className="px-3 py-2 text-muted">
                <span className="inline-flex items-center gap-2">
                  <TeamColorDot color={r.teamColor} />
                  <TeamLogo src={r.teamLogoUrl} name={r.teamName} sizeClassName="h-4 w-4" />
                  {r.teamName ?? "-"}
                </span>
              </td>
              {variant === "qualifying" && (
                <>
                  <QualifyingTimeCell time={r.q1} gap={r.q1Gap} />
                  <QualifyingTimeCell time={r.q2} gap={r.q2Gap} />
                  <QualifyingTimeCell time={r.q3} gap={r.q3Gap} />
                </>
              )}
              {variant === "bestLap" && (
                <>
                  <td className="tabular px-3 py-2 text-right">{r.bestLap ?? "-"}</td>
                  <td className="tabular px-3 py-2 text-right text-muted">{r.bestLapGap ?? "-"}</td>
                  <td className="tabular px-3 py-2 text-right text-muted">{r.lapsCompleted ?? "-"}</td>
                </>
              )}
              {variant === "race" && (
                <>
                  <td className="tabular px-3 py-2 text-right">{r.gridPosition ?? "-"}</td>
                  <td className="px-3 py-2 text-muted">{r.status ?? "-"}</td>
                  <td className="tabular px-3 py-2 text-right">{r.timeMs ? formatMs(r.timeMs) : "-"}</td>
                  <td className="tabular px-3 py-2 text-right font-semibold">{r.points ?? 0}</td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** A Q1/Q2/Q3 time with its gap to the segment's fastest time underneath;
    no gap shown for whoever set that fastest time, or for a driver with no
    time in that segment at all (knocked out earlier). */
function QualifyingTimeCell({ time, gap }: { time: string | null; gap: string | null }) {
  return (
    <td className="tabular px-3 py-2 text-right">
      <div>{time ?? "-"}</div>
      {gap && <div className="text-[11px] text-muted">{gap}</div>}
    </td>
  );
}
