"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ReferenceArea, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useLapTimes, useFlags } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { formatMs } from "@/lib/format";
import { flagColor, flagLabel } from "@/lib/flags";
import clsx from "clsx";

export function LapTimesTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useLapTimes(year, round);
  const { data: flagsData } = useFlags(year, round);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const defaultedRef = useRef(false);

  const { chartData, drivers, median } = useMemo(() => {
    if (!data || data.drivers.length === 0) return { chartData: [], drivers: [], median: 0 };
    const allTimes = data.drivers.flatMap((d) => d.laps.map((l) => l.timeMs)).sort((a, b) => a - b);
    const med = allTimes[Math.floor(allTimes.length / 2)] ?? 0;
    const cutoff = med * 1.1;
    const rows: Record<number, Record<string, number | undefined>> = {};
    for (let lap = 1; lap <= data.totalLaps; lap++) rows[lap] = { lap };
    for (const d of data.drivers) {
      for (const l of d.laps) {
        if (l.timeMs <= cutoff) rows[l.lap][d.code] = l.timeMs;
      }
    }
    return { chartData: Object.values(rows), drivers: data.drivers, median: med };
  }, [data]);

  // Selecting every driver by default makes the chart an unreadable tangle;
  // start with just the first 3 shown (typically the leaders) and let the
  // driver chips below adjust from there. Only runs once per mount (guarded
  // by the ref) so it doesn't fight the user's own toggling afterward.
  useEffect(() => {
    if (!defaultedRef.current && drivers.length > 0) {
      defaultedRef.current = true;
      setHidden(new Set(drivers.slice(3).map((d) => d.code)));
    }
  }, [drivers]);

  if (isLoading) return <LoadingState label="Loading lap times…" />;
  if (error) return <ErrorState message="Lap time data isn't available for this race." />;
  if (!data || drivers.length === 0) return <EmptyState message="No lap time data available." />;

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-1.5">
        <button
          type="button"
          onClick={() => setHidden(new Set(drivers.map((d) => d.code)))}
          className="rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
        >
          Clear
        </button>
        {drivers.map((d) => (
          <button
            key={d.code}
            onClick={() =>
              setHidden((prev) => {
                const next = new Set(prev);
                if (next.has(d.code)) next.delete(d.code);
                else next.add(d.code);
                return next;
              })
            }
            className={clsx(
              "rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
              hidden.has(d.code) ? "border-border text-muted" : "border-transparent text-white",
            )}
            style={!hidden.has(d.code) ? { backgroundColor: d.teamColor || "#6b6b72" } : undefined}
          >
            {d.code}
          </button>
        ))}
      </div>
      <div className="rounded-lg border border-border bg-surface p-4">
        <ResponsiveContainer width="100%" height={420}>
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis dataKey="lap" stroke="var(--muted)" tick={{ fontSize: 12 }} label={{ value: "Lap", position: "insideBottom", offset: -4, fill: "var(--muted)", fontSize: 12 }} />
            <YAxis
              stroke="var(--muted)"
              tick={{ fontSize: 12 }}
              domain={["dataMin - 1000", "dataMax + 1000"]}
              tickFormatter={(v) => formatMs(v)}
              width={70}
            />
            <Tooltip
              contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
              formatter={(value) => formatMs(typeof value === "number" ? value : null)}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {flagsData?.periods.map((p, i) => (
              <ReferenceArea
                key={i}
                x1={p.startLap}
                x2={p.endLap}
                fill={flagColor(p.type)}
                fillOpacity={0.12}
                stroke={flagColor(p.type)}
                strokeOpacity={0.3}
                ifOverflow="visible"
              />
            ))}
            {drivers
              .filter((d) => !hidden.has(d.code))
              .map((d) => (
                <Line key={d.code} type="monotone" dataKey={d.code} name={d.code} stroke={d.teamColor || "#9a9aa2"} dot={false} strokeWidth={2} connectNulls />
              ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 text-xs text-muted">Outlier laps (safety cars, pit stops) above {formatMs(median * 1.1)} are hidden.</p>
      {flagsData && flagsData.periods.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-3 text-xs text-muted">
          {flagsData.periods.map((p, i) => (
            <span key={i} className="flex items-center gap-1.5">
              <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: flagColor(p.type) }} />
              {flagLabel(p.type)} (laps {p.startLap}–{p.endLap})
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
