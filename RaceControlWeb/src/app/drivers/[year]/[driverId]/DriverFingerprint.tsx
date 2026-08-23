"use client";

import { PolarAngleAxis, PolarGrid, Radar, RadarChart, ResponsiveContainer, Tooltip } from "recharts";
import { useDriverFingerprint } from "@/lib/api";

export function DriverFingerprint({ year, driverId, color }: { year: number; driverId: string; color: string | null }) {
  const { data } = useDriverFingerprint(year, driverId);
  if (!data?.available || data.axes.length !== 6) return null;
  const accent = color ? (color.startsWith("#") ? color : `#${color}`) : "#e10600";
  return (
    <section className="mb-6">
      <h2 className="mb-3 text-lg font-semibold">Season Fingerprint</h2>
      <div className="rounded-lg border border-border bg-surface p-3">
        <ResponsiveContainer width="100%" height={390}>
          <RadarChart data={data.axes} outerRadius="72%">
            <PolarGrid />
            <PolarAngleAxis dataKey="label" tick={{ fontSize: 12 }} />
            <Tooltip formatter={(value) => [`${value}th percentile`, "Score"]} />
            <Radar dataKey="percentile" stroke={accent} fill={accent} fillOpacity={0.28} />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
