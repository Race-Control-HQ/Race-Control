"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useRaceTrace } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { formatDeltaMs, formatMs } from "@/lib/format";
import { flagColor, flagLabel } from "@/lib/flags";
import type { RaceTraceMode } from "@/lib/types";
import clsx from "clsx";

/**
 * The race trace: cumulative time delta per driver per lap.
 *
 * Unlike the Lap Times chart, which shows pace lap by lap, this shows the
 * *accumulated* consequence of that pace — so an undercut, a tyre cliff, a
 * safety-car compression or the exact lap a race was lost all read directly
 * off the shape of the lines.
 *
 * The backend does every derivation (see `analytics_service.get_race_trace`):
 * this component picks a mode, filters to the selected drivers and hands the
 * series to Recharts.
 */
export function RaceTraceTab({ year, round }: { year: number; round: number }) {
  const [mode, setMode] = useState<RaceTraceMode>("median");
  const { data, error, isLoading } = useRaceTrace(year, round, mode);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const defaultedRef = useRef(false);

  // Memoised so the fallback `[]` keeps a stable identity across renders;
  // otherwise the effect and pivot below re-run on every render.
  const drivers = useMemo(() => data?.drivers ?? [], [data]);

  // Twenty overlaid lines is an unreadable tangle, so start with the top 5
  // finishers (the podium fight) and let the chips open it up. Runs once per
  // mount, guarded by the ref, so it never fights the user's own toggling.
  useEffect(() => {
    if (!defaultedRef.current && drivers.length > 0) {
      defaultedRef.current = true;
      setHidden(new Set(drivers.slice(5).map((d) => d.code)));
    }
  }, [drivers]);

  // Recharts wants one row per x value with a key per series, so pivot the
  // per-driver arrays into per-lap rows.
  const chartData = useMemo(() => {
    if (!data || drivers.length === 0) return [];
    const rows: Record<number, Record<string, number>> = {};
    for (let lap = 1; lap <= data.totalLaps; lap++) rows[lap] = { lap };
    for (const d of drivers) {
      for (const l of d.laps) {
        if (rows[l.lap]) rows[l.lap][d.code] = l.deltaMs;
      }
    }
    return Object.values(rows);
  }, [data, drivers]);

  if (isLoading) return <LoadingState label="Loading race trace…" />;
  if (error) return <ErrorState message="Race trace data isn't available for this race." />;
  if (!data || !data.available || drivers.length === 0) {
    return <EmptyState message="No race trace data available." />;
  }

  const visible = drivers.filter((d) => !hidden.has(d.code));

  return (
    <div>
      {/* Mode switch */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <div className="flex rounded-full border border-border p-0.5">
          {(
            [
              ["median", "Gap to field median"],
              ["leader", "Gap to leader"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => setMode(value)}
              className={clsx(
                "rounded-full px-3 py-1 text-xs font-medium transition-colors",
                mode === value
                  ? "bg-foreground text-background"
                  : "text-muted hover:text-foreground",
              )}
            >
              {label}
            </button>
          ))}
        </div>
        {data.greenFlagMedianLapMs != null && (
          <span className="text-xs text-muted">
            Race pace {formatMs(data.greenFlagMedianLapMs)} (median green-flag lap)
          </span>
        )}
      </div>

      {/* Driver chips */}
      <div className="mb-3 flex flex-wrap items-center gap-1.5">
        <button
          type="button"
          onClick={() => setHidden(new Set(drivers.map((d) => d.code)))}
          className="rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
        >
          Clear
        </button>
        <button
          type="button"
          onClick={() => setHidden(new Set())}
          className="rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
        >
          All
        </button>
        {drivers.map((d) => {
          const on = !hidden.has(d.code);
          return (
            <button
              key={d.code}
              type="button"
              onClick={() =>
                setHidden((prev) => {
                  const next = new Set(prev);
                  if (next.has(d.code)) next.delete(d.code);
                  else next.add(d.code);
                  return next;
                })
              }
              // `status` is already "Retired" when the backend couldn't
              // determine a cause, so don't render "Retired — Retired".
              title={[
                d.fullName,
                d.retired
                  ? d.status && d.status !== "Retired"
                    ? `Retired — ${d.status}`
                    : "Retired"
                  : null,
              ]
                .filter(Boolean)
                .join(" · ")}
              className={clsx(
                "rounded-full border px-2.5 py-1 text-xs font-medium transition-colors",
                on ? "text-foreground" : "border-border text-muted hover:text-foreground",
              )}
              style={
                on
                  ? {
                      borderColor: d.teamColor ?? "#9a9aa2",
                      backgroundColor: `${d.teamColor ?? "#9a9aa2"}22`,
                    }
                  : undefined
              }
            >
              {d.code}
              {d.retired && <span className="ml-1 opacity-60">·DNF</span>}
            </button>
          );
        })}
      </div>

      <div className="rounded-lg border border-border bg-surface p-4">
        <ResponsiveContainer width="100%" height={460}>
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis
              dataKey="lap"
              stroke="var(--muted)"
              tick={{ fontSize: 12 }}
              label={{
                value: "Lap",
                position: "insideBottom",
                offset: -4,
                fill: "var(--muted)",
                fontSize: 12,
              }}
            />
            <YAxis
              stroke="var(--muted)"
              tick={{ fontSize: 12 }}
              // Domain comes from the backend so iOS, Android and web frame the
              // same race identically.
              domain={data.yDomainMs ?? ["dataMin", "dataMax"]}
              tickFormatter={(v) => formatDeltaMs(v)}
              width={72}
              label={{
                value: mode === "leader" ? "Gap to leader" : "Gap to field median",
                angle: -90,
                position: "insideLeft",
                fill: "var(--muted)",
                fontSize: 12,
              }}
            />
            <Tooltip
              contentStyle={{
                background: "var(--surface-raised)",
                border: "1px solid var(--border)",
                borderRadius: 8,
                fontSize: 12,
              }}
              labelFormatter={(lap) => `Lap ${lap}`}
              formatter={(value, name) => [
                formatDeltaMs(typeof value === "number" ? value : null),
                name,
              ]}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {/* Safety-car / flag bands, behind the traces. */}
            {data.periods.map((p, i) => (
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
            {/* Zero line: the leader in `leader` mode, reference pace in `median`. */}
            <ReferenceLine y={0} stroke="var(--muted)" strokeDasharray="4 4" />
            {visible.map((d) => (
              <Line
                key={d.code}
                type="monotone"
                dataKey={d.code}
                name={d.code}
                stroke={d.teamColor || "#9a9aa2"}
                dot={false}
                strokeWidth={2}
                // Deliberately NOT connectNulls: a retirement must leave the
                // line stopping where the car stopped, not bridging to the end.
                connectNulls={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      <p className="mt-2 text-xs text-muted">
        {mode === "median" ? (
          <>
            Each line is a driver&apos;s cumulative gap to the median car at that lap, so a rising
            line is gaining on the field and a falling line is losing to it. The vertical distance
            between any two lines is the real gap between those cars. Because the baseline moves
            with the field, safety-car periods cancel out instead of dragging every line down.
          </>
        ) : (
          <>
            Each line is a driver&apos;s gap to whoever completed that lap first, so the leader
            sits on zero and everyone else is below it.
          </>
        )}
      </p>

      {data.periods.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-3 text-xs text-muted">
          {data.periods.map((p, i) => (
            <span key={i} className="flex items-center gap-1.5">
              <span
                className="inline-block h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: flagColor(p.type) }}
              />
              {flagLabel(p.type)} (laps {p.startLap}–{p.endLap})
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
