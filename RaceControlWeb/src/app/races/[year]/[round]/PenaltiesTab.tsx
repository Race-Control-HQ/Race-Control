"use client";

import Link from "next/link";
import { usePenalties } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState, TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import { formatClock } from "@/lib/format";
import type { Penalty, PenaltyType } from "@/lib/types";

const TYPE_COLORS: Record<PenaltyType, string> = {
  "Time Penalty": "#f0d000",
  "Stop & Go Penalty": "#ff8c1a",
  "Drive Through Penalty": "#ff8c1a",
  "Grid Penalty": "#3b82f6",
  Reprimand: "#9ca3af",
  Disqualification: "#e10600",
};

function typeColor(type: PenaltyType): string {
  return TYPE_COLORS[type] ?? "#9ca3af";
}

export function PenaltiesTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = usePenalties(year, round);

  if (isLoading) return <LoadingState label="Loading penalties…" />;
  if (error) return <ErrorState message="Penalty data isn't available for this race." />;
  if (!data) return null;
  if (data.penalties.length === 0) {
    return <EmptyState message="No penalties were issued in this session." />;
  }

  return (
    <ul className="flex flex-col gap-2">
      {data.penalties.map((p, i) => (
        <PenaltyRow key={i} year={year} penalty={p} />
      ))}
    </ul>
  );
}

function PenaltyRow({ year, penalty: p }: { year: number; penalty: Penalty }) {
  const color = typeColor(p.type);

  return (
    <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface px-4 py-3">
      <div className="flex items-center gap-3">
        <TeamColorDot color={p.teamColor} />
        <TeamLogo src={p.teamLogoUrl} name={p.teamName} sizeClassName="h-6 w-6" />
        <div className="min-w-0 flex-1">
          {p.driverId ? (
            <Link href={`/drivers/${year}/${p.driverId}`} className="block truncate font-medium hover:text-racing-red">
              {p.driverName ?? p.driverCode ?? "Unknown driver"}
            </Link>
          ) : (
            <p className="truncate font-medium">{p.driverName ?? p.driverCode ?? "Unknown driver"}</p>
          )}
          <p className="truncate text-sm text-muted">{p.teamName ?? "-"}</p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1">
          <span className="tabular text-xs text-muted">
            {formatClock(p.time)}
            {p.lap != null ? ` · Lap ${p.lap}` : ""}
          </span>
          <span
            className="shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold"
            style={{ backgroundColor: `${color}26`, color }}
          >
            {p.value ? `${p.type} (${p.value})` : p.type}
          </span>
        </div>
      </div>
      <p className="text-sm text-foreground">{p.reason ?? p.message ?? "-"}</p>
    </li>
  );
}
