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
import { useQualifyingSectors } from "@/lib/api";
import { EmptyState, ErrorState, LoadingState } from "@/components/StateViews";
import { formatDeltaMs, formatMs } from "@/lib/format";

export function QualifyingSectorsTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useQualifyingSectors(year, round);
  if (isLoading) return <LoadingState label="Loading qualifying sectors…" />;
  if (error) return <ErrorState message="Qualifying sector analysis isn't available." />;
  if (!data?.available || !data.drivers.length) {
    return <EmptyState message="No complete qualifying sector splits are available." />;
  }
  const chartData = data.drivers.map((driver) => ({
    ...driver,
    s1: driver.sectorDeltaMs[0],
    s2: driver.sectorDeltaMs[1],
    s3: driver.sectorDeltaMs[2],
  }));

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-surface p-4">
        <p className="mb-3 text-sm text-muted">
          Sector contribution to the gap against {data.poleCode}; left of zero means faster than pole in that sector.
        </p>
        <ResponsiveContainer width="100%" height={Math.max(360, data.drivers.length * 34)}>
          <BarChart data={chartData} layout="vertical" stackOffset="sign" margin={{ left: 8, right: 20 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis type="number" domain={data.gapDomainMs ?? ["dataMin", "dataMax"]} tickFormatter={(v) => formatDeltaMs(Number(v))} />
            <YAxis type="category" dataKey="code" width={48} />
            <Tooltip
              contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)" }}
              formatter={(value, name) => [formatDeltaMs(typeof value === "number" ? value : null), String(name).toUpperCase()]}
            />
            <ReferenceLine x={0} stroke="var(--foreground)" />
            <Bar dataKey="s1" stackId="gap" fill="#7c3aed" />
            <Bar dataKey="s2" stackId="gap" fill="#06b6d4" />
            <Bar dataKey="s3" stackId="gap" fill="#f59e0b" />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface-raised text-left text-muted">
            <tr><th className="p-3">Driver</th><th>Lap</th><th>Ideal</th><th>Potential</th><th>Speed trap</th></tr>
          </thead>
          <tbody>
            {data.drivers.map((driver) => (
              <tr key={driver.code} className="border-t border-border">
                <td className="p-3 font-semibold">{driver.code}</td>
                <td>{formatMs(driver.lapMs)}</td>
                <td>{formatMs(driver.idealLapMs)}</td>
                <td>{formatDeltaMs(driver.idealGainMs)}</td>
                <td>{driver.speedST != null ? `${Number(driver.speedST).toFixed(0)} km/h` : "–"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
