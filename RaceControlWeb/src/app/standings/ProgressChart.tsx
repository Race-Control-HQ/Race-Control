"use client";

import { useMemo } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useStandingsEvolution } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";

export function ProgressChart({ year }: { year: number }) {
  const { data, error, isLoading } = useStandingsEvolution(year);

  const { chartData, drivers } = useMemo(() => {
    if (!data) return { chartData: [], drivers: [] };
    // Only chart the top 8 by final points: a full 20-driver legend is unreadable.
    const top = [...data.drivers].sort((a, b) => b.points - a.points).slice(0, 8);
    const rows = data.rounds.map((round) => {
      const row: Record<string, number | string> = { round };
      for (const d of top) {
        const point = d.series.find((s) => s.round === round);
        if (point) row[d.code ?? d.driverId] = point.points;
      }
      return row;
    });
    return { chartData: rows, drivers: top };
  }, [data]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load standings evolution." />;
  if (!data || drivers.length === 0) return <EmptyState message="No progress data available for this season." />;

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <ResponsiveContainer width="100%" height={420}>
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis dataKey="round" stroke="var(--muted)" tick={{ fontSize: 12 }} label={{ value: "Round", position: "insideBottom", offset: -4, fill: "var(--muted)", fontSize: 12 }} />
          <YAxis stroke="var(--muted)" tick={{ fontSize: 12 }} />
          <Tooltip contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {drivers.map((d) => (
            <Line
              key={d.driverId}
              type="monotone"
              dataKey={d.code ?? d.driverId}
              name={d.code ?? d.name}
              stroke={d.teamColor || "#9a9aa2"}
              dot={false}
              strokeWidth={2}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
