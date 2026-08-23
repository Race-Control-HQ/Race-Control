"use client";

import Link from "next/link";
import clsx from "clsx";
import type { ReactNode } from "react";

/**
 * A row in a season race list (Schedule, Circuits).
 *
 * Races that haven't happened yet have no timing, telemetry or results data
 * behind them, so linking to them only ever lands the user on an empty
 * "No data available" page. Those rows are rendered as inert, dimmed markup
 * with an "Upcoming" badge instead of a link: the information (which race,
 * where, when) is still visible, there's just nowhere useful to go yet.
 *
 * `upcoming` is about data, not the calendar: a weekend that is already
 * under way may or may not be inert depending on what the destination page
 * needs (qualifying results exist mid-weekend, a circuit map drawn from race
 * telemetry doesn't), so each list decides for itself and pairs the row with
 * `UnderwayBadge` where that's the clearer label.
 */
export function RaceListRow({
  href,
  upcoming,
  children,
}: {
  href: string;
  upcoming: boolean;
  children: ReactNode;
}) {
  const shared = "flex items-center gap-4 rounded-lg border border-border px-4 py-3";

  if (upcoming) {
    return (
      <div className={clsx(shared, "bg-surface/60 opacity-60")} aria-disabled="true">
        {children}
      </div>
    );
  }

  return (
    <Link href={href} className={clsx(shared, "bg-surface transition-colors hover:bg-surface-raised")}>
      {children}
    </Link>
  );
}

export function UpcomingBadge() {
  return (
    <span className="shrink-0 rounded-full bg-racing-red/15 px-2 py-0.5 text-xs font-semibold text-racing-red">
      Upcoming
    </span>
  );
}

export function UnderwayBadge() {
  return (
    <span className="shrink-0 rounded-full bg-racing-red px-2 py-0.5 text-xs font-semibold text-white">
      Under way
    </span>
  );
}
