"use client";

import Link from "next/link";
import { TeamLogo } from "@/components/TeamLogo";

export interface GapRow {
  key: string;
  href: string;
  position: number | null;
  logoUrl: string | null | undefined;
  logoName: string | null | undefined;
  label: string;
  sublabel?: string | null;
  points: number;
  color: string | null;
}

/**
 * A ranked list with a gap-to-leader bar per row, inspired by the reference
 * "gap" chart the user shared: rather than just listing points, each row's
 * bar fills proportionally to how close that entry is to the season leader,
 * so the size of the championship gap is visible at a glance instead of
 * requiring the reader to do the subtraction themselves.
 */
export function StandingsGapList({
  title,
  href,
  isLoading,
  rows,
}: {
  title: string;
  href: string;
  isLoading: boolean;
  rows: GapRow[];
}) {
  const leaderPoints = rows[0]?.points ?? 0;

  return (
    <div className="rounded-lg border border-border bg-surface p-5">
      <div className="mb-3 flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-muted">{title}</p>
        <Link href={href} className="text-xs font-medium text-muted hover:text-foreground">
          View all →
        </Link>
      </div>
      {isLoading ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted">No standings available yet.</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {rows.map((r) => {
            const gap = leaderPoints - r.points;
            const pct = leaderPoints > 0 ? Math.max(2, (r.points / leaderPoints) * 100) : 0;
            return (
              <li key={r.key}>
                <Link
                  href={r.href}
                  className="flex items-center gap-2.5 rounded-md px-1.5 py-1.5 transition-colors hover:bg-surface-raised"
                >
                  <span className="tabular w-5 shrink-0 text-sm font-bold text-muted">{r.position ?? "-"}</span>
                  <TeamLogo src={r.logoUrl} name={r.logoName} sizeClassName="h-5 w-5" />
                  <span className="w-20 shrink-0 truncate text-sm font-medium sm:w-32">{r.label}</span>
                  <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-raised">
                    <span
                      className="block h-full rounded-full"
                      style={{ width: `${pct}%`, backgroundColor: r.color || "var(--racing-red)" }}
                    />
                  </span>
                  <span className="tabular hidden w-14 shrink-0 text-right text-xs text-muted sm:block">
                    {gap === 0 ? "Leader" : `-${gap}`}
                  </span>
                  <span className="tabular w-10 shrink-0 text-right text-sm font-semibold">{r.points}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
