"use client";

import { useMemo } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useResults } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import type { ResultRow } from "@/lib/types";

interface LadderDriver {
  code: string;
  teamColor: string | null;
  points: { column: number; value: number }[];
  finalPosition: number | null;
  eliminatedAfter: number | null;
}

function secondsFrom(text: string | null): number | null {
  if (!text) return null;
  const parts = text.split(":");
  if (parts.length === 2) {
    const minutes = Number(parts[0]);
    const seconds = Number(parts[1]);
    if (Number.isNaN(minutes) || Number.isNaN(seconds)) return null;
    return minutes * 60 + seconds;
  }
  const n = Number(text);
  return Number.isNaN(n) ? null : n;
}

function buildLadder(rows: ResultRow[]): LadderDriver[] {
  const q1 = rows.map((r) => secondsFrom(r.q1)).filter((v): v is number => v != null);
  const q2 = rows.map((r) => secondsFrom(r.q2)).filter((v): v is number => v != null);
  const q3 = rows.map((r) => secondsFrom(r.q3)).filter((v): v is number => v != null);
  const bestQ1 = q1.length ? Math.min(...q1) : null;
  const bestQ2 = q2.length ? Math.min(...q2) : null;
  const bestQ3 = q3.length ? Math.min(...q3) : null;

  return rows
    .map((row): LadderDriver | null => {
      const points: { column: number; value: number }[] = [];
      const t1 = secondsFrom(row.q1);
      if (t1 != null && bestQ1 != null) points.push({ column: 1, value: t1 - bestQ1 });
      const t2 = secondsFrom(row.q2);
      if (t2 != null && bestQ2 != null) points.push({ column: 2, value: t2 - bestQ2 });
      const t3 = secondsFrom(row.q3);
      if (t3 != null && bestQ3 != null) points.push({ column: 3, value: t3 - bestQ3 });
      if (points.length === 0 || !row.abbreviation) return null;
      const eliminatedAfter = points.length < 3 ? points[points.length - 1].column : null;
      const finalPosition = points.length === 3 ? row.position : null;
      return { code: row.abbreviation, teamColor: row.teamColor, points, finalPosition, eliminatedAfter };
    })
    .filter((d): d is LadderDriver => d !== null);
}

/**
 * Q1 -> Q2 -> Q3 as narrowing lanes: each driver's gap to the segment's best
 * time, carried forward until elimination. The shape of the lines tells the
 * session story before a number is read.
 */
export function QualifyingLadderTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useResults(year, round, "Q");
  const drivers = useMemo(() => buildLadder(data?.results ?? []), [data]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load qualifying data." />;
  if (drivers.length === 0) {
    return <EmptyState message="No qualifying session times are available for this race." />;
  }

  const chartData = [1, 2, 3].map((segment) => {
    const row: Record<string, number | string> = { segment };
    for (const d of drivers) {
      const point = d.points.find((p) => p.column === segment);
      if (point) row[d.code] = point.value;
    }
    return row;
  });

  const q3 = drivers.filter((d) => d.eliminatedAfter === null).sort((a, b) => (a.finalPosition ?? 99) - (b.finalPosition ?? 99));
  const outQ2 = drivers.filter((d) => d.eliminatedAfter === 2);
  const outQ1 = drivers.filter((d) => d.eliminatedAfter === 1);

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-3 text-xs text-muted">Gap to session best · lower is slower · line ends at elimination</p>
      <ResponsiveContainer width="100%" height={340}>
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="segment"
            type="number"
            domain={[1, 3]}
            ticks={[1, 2, 3]}
            tickFormatter={(v) => `Q${v}`}
            stroke="var(--muted)"
            tick={{ fontSize: 12 }}
          />
          <YAxis reversed stroke="var(--muted)" tick={{ fontSize: 11 }} tickFormatter={(v) => `+${Number(v).toFixed(1)}`} />
          <Tooltip
            contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            labelFormatter={(v) => `Q${v}`}
            formatter={(value, name) => [typeof value === "number" ? `+${value.toFixed(3)}s` : String(value), String(name)]}
          />
          {drivers.map((d) => (
            <Line
              key={d.code}
              type="monotone"
              dataKey={d.code}
              name={d.code}
              stroke={d.teamColor || "#9a9aa2"}
              strokeWidth={2}
              dot={{ r: 3 }}
              connectNulls={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>

      <LadderSection label="Reached Q3" drivers={q3} suffix={(d) => `P${d.finalPosition ?? "–"}`} />
      <LadderSection label="Out in Q2" drivers={outQ2} />
      <LadderSection label="Out in Q1" drivers={outQ1} />
    </div>
  );
}

function LadderSection({
  label,
  drivers,
  suffix,
}: {
  label: string;
  drivers: LadderDriver[];
  suffix?: (d: LadderDriver) => string;
}) {
  if (drivers.length === 0) return null;
  return (
    <div className="mt-3">
      <p className="mb-1 text-xs text-muted">{label}</p>
      <div className="flex flex-wrap gap-x-3 gap-y-1">
        {drivers.map((d) => (
          <span key={d.code} className="text-xs font-bold tabular" style={{ color: d.teamColor || "#9a9aa2" }}>
            {d.code}
            {suffix ? ` ${suffix(d)}` : ""}
          </span>
        ))}
      </div>
    </div>
  );
}
