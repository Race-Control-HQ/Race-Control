"use client";

import { useMemo, useState } from "react";
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useTyrePerformance } from "@/lib/api";
import { EmptyState, ErrorState, LoadingState } from "@/components/StateViews";
import { compoundColor } from "@/lib/tyres";
import { formatDeltaMs } from "@/lib/format";
import clsx from "clsx";

export function TyrePerformanceTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useTyrePerformance(year, round);
  const [compound, setCompound] = useState<string | null>(null);
  const compounds = useMemo(
    () => [...new Set(data?.stints.map((stint) => stint.compound ?? "UNKNOWN") ?? [])],
    [data],
  );

  if (isLoading) return <LoadingState label="Loading tyre degradation…" />;
  if (error) return <ErrorState message="Tyre performance isn't available for this race." />;
  if (!data?.available || !data.stints.length) {
    return <EmptyState message="No clean tyre-performance stints are available." />;
  }

  const stints = data.stints.filter(
    (stint) => compound == null || (stint.compound ?? "UNKNOWN") === compound,
  );

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => setCompound(null)}
          className={clsx("rounded-full border px-3 py-1 text-xs", compound == null && "bg-foreground text-background")}
        >
          All
        </button>
        {compounds.map((name) => (
          <button
            key={name}
            type="button"
            onClick={() => setCompound(name)}
            className={clsx("rounded-full border px-3 py-1 text-xs", compound === name && "text-background")}
            style={compound === name ? { backgroundColor: compoundColor(name) } : { borderColor: compoundColor(name) }}
          >
            {name}
          </button>
        ))}
      </div>
      <div className="rounded-lg border border-border bg-surface p-4">
        <ResponsiveContainer width="100%" height={460}>
          <ComposedChart margin={{ left: 8, right: 12 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
            <XAxis
              dataKey="tyreLife"
              type="number"
              domain={data.xDomain ?? ["dataMin", "dataMax"]}
              label={{ value: "Tyre life (laps)", position: "insideBottom", offset: -4 }}
            />
            <YAxis
              dataKey="deltaMs"
              type="number"
              domain={data.yDomainMs ?? [0, "dataMax"]}
              tickFormatter={(value) => formatDeltaMs(value)}
              width={66}
            />
            <Tooltip
              contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)" }}
              formatter={(value) => formatDeltaMs(typeof value === "number" ? value : null)}
            />
            {stints.map((stint) => (
              <Scatter
                key={`points-${stint.id}`}
                name={`${stint.driverCode} S${stint.stint} (${stint.slopeSecPerLap.toFixed(3)} s/lap)`}
                data={stint.points}
                fill={compoundColor(stint.compound)}
                opacity={0.8}
              />
            ))}
            {stints.map((stint) => (
              <Line
                key={`fit-${stint.id}`}
                name={`${stint.driverCode} fit`}
                data={stint.fit}
                dataKey="deltaMs"
                stroke={compoundColor(stint.compound)}
                strokeWidth={1.5}
                strokeDasharray="5 3"
                dot={false}
                legendType="none"
              />
            ))}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <div className="mt-3 flex flex-wrap gap-2 text-xs text-muted">
        {data.compoundBaselines.map((baseline) => (
          <span key={baseline.compound}>
            {baseline.compound}: {baseline.slopeSecPerLap.toFixed(3)} s/lap field median
          </span>
        ))}
      </div>
    </div>
  );
}
