"use client";

import { useMemo } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useStandingsEvolution } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";

/**
 * How this driver's championship points accumulated round by round.
 *
 * Reuses the same `/api/standings-evolution` data that powers the
 * Standings page's Progress chart, just filtered down to one driver instead
 * of the top 8 (that page's cap exists to keep a multi-driver legend
 * readable, which doesn't apply here since there's only one line).
 */
export function DriverPointsChart({
  year,
  driverId,
  teamColor,
}: {
  year: number;
  driverId: string;
  teamColor: string | null;
}) {
  const { data, error, isLoading } = useStandingsEvolution(year);

  const chartData = useMemo(() => {
    if (!data) return [];
    const driver = data.drivers.find((d) => d.driverId === driverId);
    if (!driver) return [];
    return data.rounds.map((round) => {
      const point = driver.series.find((s) => s.round === round);
      return { round, points: point?.points ?? null };
    });
  }, [data, driverId]);

  if (isLoading) return <LoadingState label="Loading points progression…" />;
  if (error) return <ErrorState message="Couldn't load points progression." />;
  if (chartData.length === 0) return <EmptyState message="No points progression available for this season." />;

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="round"
            stroke="var(--muted)"
            tick={{ fontSize: 12 }}
            label={{ value: "Round", position: "insideBottom", offset: -4, fill: "var(--muted)", fontSize: 12 }}
          />
          <YAxis stroke="var(--muted)" tick={{ fontSize: 12 }} />
          <Tooltip
            contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            labelFormatter={(round) => `Round ${round}`}
          />
          <Line
            type="monotone"
            dataKey="points"
            name="Points"
            stroke={teamColor || "#9a9aa2"}
            dot={false}
            strokeWidth={2}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
