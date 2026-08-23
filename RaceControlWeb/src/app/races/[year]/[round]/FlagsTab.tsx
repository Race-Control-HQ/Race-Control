"use client";

import { useFlags } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { flagColor, flagLabel } from "@/lib/flags";
import { formatClock } from "@/lib/format";

export function FlagsTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useFlags(year, round);

  if (isLoading) return <LoadingState label="Loading flags…" />;
  if (error) return <ErrorState message="Flag data isn't available for this race." />;
  if (!data) return null;
  if (data.periods.length === 0) return <EmptyState message="No flags. Clean race." />;

  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col gap-2">
        {data.periods.map((p, i) => (
          <li key={i} className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3">
            <span
              className="h-8 w-1.5 shrink-0 rounded-full"
              style={{ backgroundColor: flagColor(p.type) }}
              aria-hidden
            />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{flagLabel(p.type)}</p>
              <p className="truncate text-sm text-muted">{p.reason ?? "-"}</p>
            </div>
            <span
              className="tabular shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold"
              style={{ backgroundColor: `${flagColor(p.type)}26`, color: flagColor(p.type) }}
            >
              {p.startLap === p.endLap ? `Lap ${p.startLap}` : `Laps ${p.startLap}–${p.endLap}`}
            </span>
          </li>
        ))}
      </ul>

      {data.events.length > 0 && (
        <details className="rounded-lg border border-border bg-surface">
          <summary className="cursor-pointer select-none px-4 py-3 text-sm font-medium text-muted hover:text-foreground">
            Raw race control log ({data.events.length})
          </summary>
          <ul className="flex flex-col gap-1 border-t border-border px-4 py-3">
            {data.events.map((e, i) => (
              <li key={i} className="flex items-start gap-3 py-1 text-xs">
                <span className="tabular w-32 shrink-0 text-muted">{formatClock(e.time)}</span>
                <span className="w-12 shrink-0 text-muted">{e.lap != null ? `Lap ${e.lap}` : "-"}</span>
                <span className="min-w-0 flex-1 text-foreground">{e.message ?? e.category ?? "-"}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
