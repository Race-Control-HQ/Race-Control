"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { usePitStops } from "@/lib/api";
import { EmptyState, ErrorState, LoadingState } from "@/components/StateViews";
import { formatDeltaMs } from "@/lib/format";

export function PitStopsTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = usePitStops(year, round);
  if (isLoading) return <LoadingState label="Loading pit-stop ledger…" />;
  if (error) return <ErrorState message="Pit-stop analysis isn't available." />;
  if (!data?.available || !data.stops.length) {
    return <EmptyState message="No timed pit-lane transits are available." />;
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-surface p-4">
        <ResponsiveContainer width="100%" height={Math.max(320, data.stops.length * 38)}>
          <BarChart data={data.stops} layout="vertical" margin={{ left: 14, right: 24 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis
              type="number"
              domain={data.lossDomainMs ?? [0, "dataMax"]}
              tickFormatter={(value) => `${(Number(value) / 1000).toFixed(0)}s`}
            />
            <YAxis
              type="category"
              dataKey="id"
              width={82}
              tickFormatter={(_, index) => {
                const stop = data.stops[index];
                return stop ? `${stop.driverCode} L${stop.lap}` : "";
              }}
            />
            <Tooltip
              contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)" }}
              formatter={(value) => formatDeltaMs(typeof value === "number" ? value : null)}
              labelFormatter={(_, payload) => {
                const stop = payload?.[0]?.payload;
                return stop
                  ? `${stop.driverCode} stop ${stop.stop}: P${stop.entryPosition ?? "–"} → P${stop.rejoinPosition ?? "–"}`
                  : "";
              }}
            />
            <ReferenceLine
              x={data.circuitMedianLossMs ?? 0}
              stroke="var(--muted)"
              strokeDasharray="5 4"
              label="Circuit median"
            />
            <Bar dataKey="lossMs" fill="var(--accent)" radius={[0, 4, 4, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface-raised text-left text-muted">
            <tr><th className="p-3">Stop</th><th>Transit</th><th>Positions</th><th>Outcome</th><th>Rivals</th></tr>
          </thead>
          <tbody>
            {data.stops.map((stop) => (
              <tr key={stop.id} className="border-t border-border">
                <td className="p-3 font-semibold">{stop.driverCode} · L{stop.lap}</td>
                <td>{(stop.lossMs / 1000).toFixed(3)}s</td>
                <td>P{stop.entryPosition ?? "–"} → P{stop.rejoinPosition ?? "–"}</td>
                <td>{stop.outcome}</td>
                <td>{stop.rivals.join(", ") || "–"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
