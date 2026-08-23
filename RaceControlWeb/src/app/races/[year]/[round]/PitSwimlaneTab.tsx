"use client";

import { useMemo, useState } from "react";
import { usePitStops } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState, TeamColorDot } from "@/components/StateViews";
import type { PitStopLedgerItem } from "@/lib/types";

/**
 * Pit stops aligned on a shared lap axis, with position before/after the
 * stop and whether it was an undercut, overcut, or held position — turning
 * strategy into visible cause and effect instead of a ledger of times.
 */
export function PitSwimlaneTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = usePitStops(year, round);
  const [selected, setSelected] = useState<PitStopLedgerItem | null>(null);

  const { stops, minLap, maxLap } = useMemo(() => {
    const list = [...(data?.stops ?? [])].sort((a, b) => a.lap - b.lap);
    const laps = list.map((s) => s.lap);
    const min = Math.max((laps.length ? Math.min(...laps) : 1) - 1, 0);
    const max = (laps.length ? Math.max(...laps) : 1) + 1;
    return { stops: list, minLap: min, maxLap: max };
  }, [data]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Pit-stop analysis isn't available." />;
  if (!data?.available || stops.length === 0) {
    return <EmptyState message="No timed pit-lane transits are available for this race." />;
  }

  const span = Math.max(maxLap - minLap, 1);

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-3 text-xs text-muted">
        Lap {minLap}–{maxLap} · entry → rejoin position · click a stop for detail
      </p>

      <div className="flex flex-col gap-2">
        {stops.map((stop) => {
          const pitPct = ((stop.lap - minLap) / span) * 100;
          const gained = stop.positionsGained ?? 0;
          const badgeColor = gained > 0 ? "text-[var(--positive)]" : gained < 0 ? "text-[var(--negative)]" : "text-muted";
          return (
            <button
              key={stop.id}
              type="button"
              onClick={() => setSelected(stop)}
              className="flex items-center gap-3 rounded-md py-1 text-left hover:bg-surface-raised"
            >
              <span className="w-10 shrink-0 text-xs font-bold tabular">{stop.driverCode}</span>
              <span className="relative h-5 flex-1">
                <span className="absolute top-1/2 h-1 w-full -translate-y-1/2 rounded-full bg-surface-raised" />
                <span
                  className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full"
                  style={{ left: 0, width: `${pitPct}%`, backgroundColor: `${stop.teamColor || "#6b6b72"}99` }}
                />
                <span
                  className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full"
                  style={{ left: `${pitPct}%`, width: `${100 - pitPct}%`, backgroundColor: "#64d2ff99" }}
                />
                <span
                  className="absolute top-0 w-0.5 bg-racing-red"
                  style={{ left: `${pitPct}%`, height: "100%" }}
                />
              </span>
              <span className={`w-20 shrink-0 text-right text-xs font-bold tabular ${badgeColor}`}>
                P{stop.entryPosition ?? "–"} → P{stop.rejoinPosition ?? "–"}
              </span>
            </button>
          );
        })}
      </div>

      <div className="mt-2 flex justify-between pl-[3.25rem] text-xs text-muted">
        <span>L{minLap}</span>
        <span>L{Math.round((minLap + maxLap) / 2)}</span>
        <span>L{maxLap}</span>
      </div>

      {selected && (
        <div className="mt-4 rounded-md bg-surface-raised px-3 py-2 text-sm">
          <div className="flex items-center gap-2 font-semibold text-foreground">
            <TeamColorDot color={selected.teamColor} />
            {selected.driverCode} · Stop {selected.stop} · Lap {selected.lap}
          </div>
          <div className="mt-1 text-xs text-muted">
            Pit-lane loss {(selected.lossMs / 1000).toFixed(1)}s ·{" "}
            {selected.outcome.charAt(0) + selected.outcome.slice(1).toLowerCase()} · P{selected.entryPosition ?? "–"} →
            P{selected.rejoinPosition ?? "–"}
          </div>
          {selected.rivals.length > 0 && (
            <div className="mt-1 text-xs text-muted/70">Rival window: {selected.rivals.join(", ")}</div>
          )}
        </div>
      )}
    </div>
  );
}
