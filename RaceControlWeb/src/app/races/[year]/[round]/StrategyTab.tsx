"use client";

import Link from "next/link";
import { useStrategy } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState, TeamColorDot } from "@/components/StateViews";
import { TyreLegend } from "@/components/TyreLegend";
import { compoundColor } from "@/lib/tyres";

export function StrategyTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useStrategy(year, round);

  if (isLoading) return <LoadingState label="Loading strategy…" />;
  if (error) return <ErrorState message="Strategy data isn't available for this race." />;
  if (!data || data.drivers.length === 0) return <EmptyState message="No strategy data available." />;

  return (
    <div className="flex flex-col gap-1.5">
      {data.drivers.map((d) => (
        <div key={d.code} className="flex items-center gap-3 rounded-lg border border-border bg-surface px-3 py-2">
          <div className="flex w-32 shrink-0 items-center gap-1.5">
            <TeamColorDot color={d.teamColor} />
            {d.driverId ? (
              <Link href={`/drivers/${year}/${d.driverId}`} className="text-sm font-medium hover:text-racing-red">
                {d.code}
              </Link>
            ) : (
              <span className="text-sm font-medium">{d.code}</span>
            )}
            {d.retired && (
              <span
                className="shrink-0 rounded-full bg-racing-red/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-racing-red"
                title={d.status ?? "Retired"}
              >
                DNF
              </span>
            )}
          </div>
          <div className="flex h-5 flex-1 overflow-hidden rounded-sm bg-surface-raised">
            {d.stints.map((s, i) => (
              <div
                key={i}
                className="h-full border-r border-background/40 last:border-r-0"
                style={{
                  width: `${(s.laps / data.totalLaps) * 100}%`,
                  backgroundColor: compoundColor(s.compound),
                }}
                title={`${s.compound ?? "Unknown"} · laps ${s.startLap}–${s.endLap}`}
              />
            ))}
          </div>
          <span className="tabular w-20 shrink-0 text-right text-xs text-muted">{d.pitStops} stop{d.pitStops === 1 ? "" : "s"}</span>
        </div>
      ))}
      <div className="mt-2">
        <TyreLegend />
      </div>
    </div>
  );
}
