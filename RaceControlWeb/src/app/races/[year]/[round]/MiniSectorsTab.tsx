"use client";

import { useMiniSectors } from "@/lib/api";
import { EmptyState, ErrorState, LoadingState } from "@/components/StateViews";

function color(hex: string | null) {
  return hex ? (hex.startsWith("#") ? hex : `#${hex}`) : "#e10600";
}

export function MiniSectorsTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useMiniSectors(year, round);
  if (isLoading) return <LoadingState label="Calculating mini-sectors… first load may take a minute." />;
  if (error) return <ErrorState message="Mini-sector dominance isn't available." />;
  if (!data?.available || !data.segments.length) {
    return <EmptyState message="No qualifying telemetry is available for mini-sector analysis." />;
  }
  const points = data.segments.flatMap((segment) => segment.points);
  const minX = Math.min(...points.map((point) => point[0]));
  const maxX = Math.max(...points.map((point) => point[0]));
  const minY = Math.min(...points.map((point) => point[1]));
  const maxY = Math.max(...points.map((point) => point[1]));
  const pad = Math.max(maxX - minX, maxY - minY) * 0.04;

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border bg-black p-4">
        <svg
          viewBox={`${minX - pad} ${minY - pad} ${maxX - minX + pad * 2} ${maxY - minY + pad * 2}`}
          className="h-[520px] w-full"
          role="img"
          aria-label="Track coloured by fastest driver in each mini-sector"
        >
          <g transform={`translate(0 ${minY + maxY}) scale(1 -1)`}>
            {data.segments.map((segment) => (
              <polyline
                key={segment.index}
                points={segment.points.map((point) => point.join(",")).join(" ")}
                fill="none"
                stroke={color(segment.teamColor)}
                strokeWidth={Math.max(maxX - minX, maxY - minY) / 95}
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <title>{`${segment.winnerCode}: ${segment.startDistance}–${segment.endDistance}m, ${segment.gapMs}ms advantage`}</title>
              </polyline>
            ))}
          </g>
        </svg>
      </div>
      <div className="flex flex-wrap gap-3">
        {data.legend.map((item) => (
          <span key={item.code} className="rounded-full border px-3 py-1 text-sm" style={{ borderColor: color(item.teamColor) }}>
            <span style={{ color: color(item.teamColor) }}>●</span> {item.code} · {item.segmentsWon}
          </span>
        ))}
      </div>
    </div>
  );
}
