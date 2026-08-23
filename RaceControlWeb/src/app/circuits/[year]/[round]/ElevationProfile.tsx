"use client";

import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { CircuitMap } from "@/lib/types";

export function ElevationProfile({ map }: { map: CircuitMap }) {
  const data = map.points.map((p) => ({ distance: p.distance, elevation: p.z }));
  if (data.length === 0) return null;

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">Elevation Profile</p>
      <ResponsiveContainer width="100%" height={140}>
        <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="elevationFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--racing-red)" stopOpacity={0.5} />
              <stop offset="100%" stopColor="var(--racing-red)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="distance" hide />
          <YAxis stroke="var(--muted)" tick={{ fontSize: 10 }} width={40} domain={["dataMin - 2", "dataMax + 2"]} />
          <Tooltip
            contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            formatter={(v) => [`${v}m`, "Elevation"]}
            labelFormatter={(v) => `${Math.round(Number(v))}m`}
          />
          <Area type="monotone" dataKey="elevation" stroke="var(--racing-red)" fill="url(#elevationFill)" strokeWidth={2} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
