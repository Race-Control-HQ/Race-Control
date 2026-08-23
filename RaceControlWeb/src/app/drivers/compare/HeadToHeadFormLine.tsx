"use client";

import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useHeadToHeadFormLine } from "@/lib/api";
import type { Driver } from "@/lib/types";

/**
 * Two stepped lines comparing finishing position round by round. Crossover
 * points and missing finishes show when the matchup changed, not just who
 * won the season aggregate.
 */
export function HeadToHeadFormLine({ year, a, b }: { year: number; a: Driver; b: Driver }) {
  const { data: series, isLoading } = useHeadToHeadFormLine(year, a, b);
  if (isLoading || !series) return null;
  const [sa, sb] = series;
  if (sa.points.length === 0 && sb.points.length === 0) return null;

  const rounds = Array.from(new Set([...sa.points.map((p) => p.round), ...sb.points.map((p) => p.round)])).sort(
    (x, y) => x - y,
  );
  const chartData = rounds.map((round) => ({
    round,
    [sa.code]: sa.points.find((p) => p.round === round)?.position,
    [sb.code]: sb.points.find((p) => p.round === round)?.position,
  }));

  return (
    <div className="mb-4 rounded-lg border border-border bg-surface p-4">
      <p className="mb-2 text-xs text-muted">Finishing position by round</p>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis dataKey="round" stroke="var(--muted)" tick={{ fontSize: 11 }} />
          <YAxis reversed stroke="var(--muted)" tick={{ fontSize: 11 }} tickFormatter={(v) => `P${v}`} />
          <Tooltip
            contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            formatter={(value, name) => [typeof value === "number" ? `P${value}` : String(value), String(name)]}
          />
          <Line
            type="stepAfter"
            dataKey={sa.code}
            name={sa.code}
            stroke={sa.teamColor || "#e10600"}
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls
          />
          <Line
            type="stepAfter"
            dataKey={sb.code}
            name={sb.code}
            stroke={sb.teamColor || "#64d2ff"}
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
