"use client";

import Link from "next/link";
import { useRetirements } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState, TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";

export function RetirementsTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useRetirements(year, round);

  if (isLoading) return <LoadingState label="Loading retirements…" />;
  if (error) return <ErrorState message="Retirement data isn't available for this race." />;
  if (!data || data.retirements.length === 0) return <EmptyState message="No retirements. Everyone finished." />;

  return (
    <ul className="flex flex-col gap-2">
      {data.retirements.map((r, i) => (
        <li key={i} className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3">
          <TeamColorDot color={r.teamColor} />
          <TeamLogo src={r.teamLogoUrl} name={r.teamName} sizeClassName="h-6 w-6" />
          <div className="min-w-0 flex-1">
            {r.driverId ? (
              <Link href={`/drivers/${year}/${r.driverId}`} className="block truncate font-medium hover:text-racing-red">
                {r.fullName ?? r.driver}
              </Link>
            ) : (
              <p className="truncate font-medium">{r.fullName ?? r.driver}</p>
            )}
            <p className="truncate text-sm text-muted">{r.teamName ?? "-"}</p>
          </div>
          {r.lapsCompleted != null && (
            <span className="tabular shrink-0 text-sm text-muted">Lap {r.lapsCompleted}</span>
          )}
          <span className="shrink-0 rounded-full bg-racing-red/15 px-2.5 py-1 text-xs font-semibold text-racing-red">{r.status}</span>
        </li>
      ))}
    </ul>
  );
}
