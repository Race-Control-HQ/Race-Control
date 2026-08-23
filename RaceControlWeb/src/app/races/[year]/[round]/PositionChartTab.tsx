"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useFlags, useReplay, useStrategy } from "@/lib/api";
import { EmptyState, ErrorState, LoadingState } from "@/components/StateViews";
import { flagColor } from "@/lib/flags";
import clsx from "clsx";

export function PositionChartTab({ year, round }: { year: number; round: number }) {
  const replay = useReplay(year, round);
  const strategy = useStrategy(year, round);
  const flags = useFlags(year, round);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const defaulted = useRef(false);

  const drivers = useMemo(() => replay.data?.drivers ?? [], [replay.data]);
  useEffect(() => {
    if (!defaulted.current && drivers.length) {
      defaulted.current = true;
      setHidden(new Set(drivers.slice(5).map((driver) => driver.code)));
    }
  }, [drivers]);

  const rows = useMemo(() => {
    if (!replay.data) return [];
    return replay.data.frames.map((frame) => {
      const row: Record<string, number> = { lap: frame.lap };
      for (const entry of frame.order) row[entry.driver] = entry.position;
      return row;
    });
  }, [replay.data]);

  if (replay.isLoading) return <LoadingState label="Loading position chart…" />;
  if (replay.error) return <ErrorState message="Position data isn't available for this race." />;
  if (!replay.data || !rows.length) return <EmptyState message="No position data available." />;

  const pits = strategy.data?.drivers.flatMap((driver) =>
    driver.stints.slice(1).map((stint) => {
      const row = rows.find((item) => item.lap === stint.startLap);
      return row?.[driver.code] == null
        ? null
        : { code: driver.code, lap: stint.startLap, position: row[driver.code] };
    }).filter((item): item is { code: string; lap: number; position: number } => item != null),
  ) ?? [];
  const retired = strategy.data?.drivers.filter((driver) => driver.retired).flatMap((driver) => {
    const last = [...rows].reverse().find((row) => row[driver.code] != null);
    return last ? [{ code: driver.code, lap: last.lap, position: last[driver.code] }] : [];
  }) ?? [];

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-1.5">
        {drivers.map((driver) => {
          const visible = !hidden.has(driver.code);
          return (
            <button
              key={driver.code}
              type="button"
              onClick={() => setHidden((previous) => {
                const next = new Set(previous);
                if (visible) next.add(driver.code); else next.delete(driver.code);
                return next;
              })}
              className={clsx(
                "rounded-full border px-2.5 py-1 text-xs font-medium",
                visible ? "text-foreground" : "border-border text-muted",
              )}
              style={visible ? { borderColor: driver.teamColor ?? "#9a9aa2" } : undefined}
            >
              {driver.code}
            </button>
          );
        })}
      </div>
      <div className="rounded-lg border border-border bg-surface p-4">
        <ResponsiveContainer width="100%" height={460}>
          <LineChart data={rows}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis dataKey="lap" stroke="var(--muted)" />
            <YAxis reversed domain={[1, Math.max(20, drivers.length)]} allowDecimals={false} width={36} />
            <Tooltip
              contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)" }}
              labelFormatter={(lap) => `Lap ${lap}`}
              formatter={(value, name) => [`P${value}`, name]}
            />
            <Legend />
            {flags.data?.periods.map((period, index) => (
              <ReferenceArea
                key={index}
                x1={period.startLap}
                x2={period.endLap}
                fill={flagColor(period.type)}
                fillOpacity={0.12}
              />
            ))}
            {drivers.filter((driver) => !hidden.has(driver.code)).map((driver) => (
              <Line
                key={driver.code}
                dataKey={driver.code}
                name={driver.code}
                stroke={driver.teamColor ?? "#9a9aa2"}
                strokeWidth={2}
                dot={false}
                connectNulls={false}
                type="monotone"
              />
            ))}
            {pits.filter((pit) => !hidden.has(pit.code)).map((pit) => (
              <ReferenceDot
                key={`pit-${pit.code}-${pit.lap}`}
                x={pit.lap}
                y={pit.position}
                r={4}
                fill="#ffffff"
                stroke="#111111"
              />
            ))}
            {retired.filter((item) => !hidden.has(item.code)).map((item) => (
              <ReferenceDot
                key={`dnf-${item.code}`}
                x={item.lap}
                y={item.position}
                r={5}
                fill="#e10600"
                stroke="#e10600"
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 text-xs text-muted">
        Position 1 is at the top. White markers are pit stops; red terminators mark retirements.
      </p>
    </div>
  );
}
