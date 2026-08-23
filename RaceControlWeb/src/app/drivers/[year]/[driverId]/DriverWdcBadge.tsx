"use client";

import Link from "next/link";
import { useWdcCalculator } from "@/lib/api";

const CAN_WIN_COLOR = "#22c55e";

/**
 * "Can win" / "Can't win" title-decider status, linking through to the full
 * WDC calculator. Uses the live calculator (no `through_round`): for a
 * season still in progress that's the current picture, and for a season
 * that's already finished it correctly collapses to "Can win" for just the
 * champion, since `roundsRemaining` is 0 either way. No special-casing
 * needed for past vs. current seasons.
 */
export function DriverWdcBadge({ year, driverId }: { year: number; driverId: string }) {
  const { data } = useWdcCalculator(year);
  const entry = data?.drivers.find((d) => d.driverId === driverId);

  if (!entry) return null;

  return (
    <Link
      href={`/standings?year=${year}&mode=wdc`}
      title={
        entry.canWin
          ? "Could still overtake the leader in the best possible case. See the Title Decider."
          : "No longer mathematically possible, even in the best case. See the Title Decider."
      }
      className="inline-flex shrink-0 items-center rounded-full px-2.5 py-1 text-xs font-semibold transition-opacity hover:opacity-80"
      style={
        entry.canWin
          ? { backgroundColor: `${CAN_WIN_COLOR}26`, color: CAN_WIN_COLOR }
          : { backgroundColor: "var(--surface-raised)", color: "var(--muted)" }
      }
    >
      {entry.canWin ? "Can win WDC" : "Can't win WDC"}
    </Link>
  );
}
