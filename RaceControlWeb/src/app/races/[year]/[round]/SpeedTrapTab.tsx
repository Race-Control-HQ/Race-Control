"use client";

import { useMemo } from "react";
import { useQualifyingSectors } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";

interface Point {
  label: string;
  speed: number;
}

interface Row {
  code: string;
  teamColor: string | null;
  points: Point[];
}

const VB_W = 640;
const ROW_H = 30;
const LABEL_W = 44;

/**
 * Shared-scale dot plot of the four speed-trap detection points (I1, I2,
 * FL, ST), one row per driver. Exposes setup tradeoffs — low-drag top speed
 * vs. cornering compromise — that lap time and sector gaps alone hide.
 */
export function SpeedTrapTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useQualifyingSectors(year, round);

  const rows: Row[] = useMemo(() => {
    if (!data) return [];
    return data.drivers
      .filter((d) => d.speedI1 != null && d.speedI2 != null && d.speedFL != null && d.speedST != null)
      .map((d) => ({
        code: d.code,
        teamColor: d.teamColor,
        points: [
          { label: "I1", speed: d.speedI1 as number },
          { label: "I2", speed: d.speedI2 as number },
          { label: "FL", speed: d.speedFL as number },
          { label: "ST", speed: d.speedST as number },
        ],
      }))
      .sort((a, b) => b.points[3].speed - a.points[3].speed);
  }, [data]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Speed-trap data isn't available." />;
  if (!data?.available || rows.length === 0) {
    return <EmptyState message="No qualifying speed-trap readings are available for this session." />;
  }

  const speeds = rows.flatMap((r) => r.points.map((p) => p.speed));
  const minSpeed = Math.min(...speeds);
  const maxSpeed = Math.max(...speeds);
  const span = maxSpeed - minSpeed || 1;
  const x = (speed: number) => LABEL_W + ((speed - minSpeed) / span) * (VB_W - LABEL_W - 8);

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-1 text-xs text-muted">Speed at each detection point · km/h</p>
      <div className="mb-2 flex justify-between pl-11 text-xs text-muted">
        <span>{Math.round(minSpeed)}</span>
        <span>{Math.round(maxSpeed)} km/h</span>
      </div>
      <svg viewBox={`0 0 ${VB_W} ${rows.length * ROW_H}`} className="w-full" style={{ height: rows.length * ROW_H }}>
        {rows.map((row, i) => {
          const color = row.teamColor || "#9a9aa2";
          const y = i * ROW_H + ROW_H / 2;
          const xs = row.points.map((p) => x(p.speed));
          return (
            <g key={row.code}>
              <text x={0} y={y + 4} fontSize={11} fontWeight={700} fill="var(--foreground)">
                {row.code}
              </text>
              <line x1={xs[0]} y1={y} x2={xs[xs.length - 1]} y2={y} stroke={color} strokeOpacity={0.5} strokeWidth={2} />
              {row.points.map((p, j) => (
                <circle key={p.label} cx={xs[j]} cy={y} r={p.label === "ST" ? 6 : 3.5} fill={color} />
              ))}
            </g>
          );
        })}
      </svg>
      <div className="mt-3 flex items-center gap-4 text-xs text-muted">
        <span>I1 / I2 / FL</span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full bg-[var(--muted)]" />
          ST emphasized
        </span>
      </div>
    </div>
  );
}
