"use client";

import { useMemo, useState } from "react";
import { useCircuitMap, useRaceDrivers, useTelemetry } from "@/lib/api";
import { createProjector, densifyTrace, nearestIndexByDistance, rotatePoints } from "@/lib/trackGeometry";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";

const VB = 600;

type Zone = "brake" | "coast" | "partial" | "full";

const ZONE_COLOR: Record<Zone, string> = {
  brake: "#e10600",
  coast: "#64d2ff",
  partial: "#ff9f0a",
  full: "#30d158",
};

function classify(brake: number, throttle: number): Zone {
  if (brake > 0) return "brake";
  if (throttle < 10) return "coast";
  if (throttle < 90) return "partial";
  return "full";
}

/**
 * Paints the racing line by brake / coast / partial-throttle / full-throttle
 * intensity for a driver's fastest lap. The outline is drawn from the
 * circuit endpoint rather than the telemetry trace's own x/y channel — same
 * reasoning as TelemetryMiniMap: a single lap's position channel updates
 * slower than the rest of telemetry and often has too few points to draw a
 * smooth line, while the circuit endpoint already picks a lap with rich
 * position data. Each outline point is colored by the nearest telemetry
 * sample at that distance along the lap.
 */
export function BrakeThrottleTab({ year, round }: { year: number; round: number }) {
  const { data: drivers } = useRaceDrivers(year, round);
  const [code, setCode] = useState<string | null>(null);
  const effectiveCode = code ?? drivers?.[0]?.code ?? null;

  const { data: circuit, isLoading: circuitLoading, error: circuitError } = useCircuitMap(year, round);
  const { data: telemetry, isLoading: telemetryLoading, error: telemetryError } = useTelemetry(
    year,
    round,
    effectiveCode,
  );
  const trace = telemetry?.trace ?? null;

  const segments = useMemo(() => {
    if (!circuit || circuit.points.length === 0 || !trace) return [];
    const dense = densifyTrace(circuit.points, 4);
    const rotated = rotatePoints(
      dense.map((p) => ({ x: p.x, y: p.y })),
      circuit.rotation,
    );
    const projector = createProjector(rotated, VB, VB, 30);
    const screen = rotated.map((p) => projector.project(p));

    const out: { a: { x: number; y: number }; b: { x: number; y: number }; color: string }[] = [];
    for (let i = 1; i < screen.length; i++) {
      const idx = nearestIndexByDistance(trace.distance, dense[i].distance);
      const zone = classify(trace.brake[idx] ?? 0, trace.throttle[idx] ?? 0);
      out.push({ a: screen[i - 1], b: screen[i], color: ZONE_COLOR[zone] });
    }
    return out;
  }, [circuit, trace]);

  if (circuitLoading || telemetryLoading) return <LoadingState />;
  if (circuitError) return <ErrorState message="Couldn't load the circuit map." />;
  if (telemetryError) return <ErrorState message="Couldn't load telemetry for this driver." />;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <select
          value={effectiveCode ?? ""}
          onChange={(e) => setCode(e.target.value)}
          className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm"
        >
          {drivers?.map((d) => (
            <option key={d.code} value={d.code}>
              {d.code}
            </option>
          ))}
        </select>
        {trace?.lapTime && (
          <span className="text-xs text-muted">
            {trace.code} · fastest lap · {trace.lapTime}
          </span>
        )}
      </div>

      {segments.length === 0 ? (
        <EmptyState message="No telemetry available for this driver in this session." />
      ) : (
        <div className="rounded-lg border border-border bg-surface p-4">
          <svg viewBox={`0 0 ${VB} ${VB}`} className="mx-auto w-full max-w-xl">
            <polyline
              points={segments
                .map((s) => `${s.a.x},${s.a.y}`)
                .concat(`${segments[segments.length - 1].b.x},${segments[segments.length - 1].b.y}`)
                .join(" ")}
              fill="none"
              stroke="#000"
              strokeOpacity={0.55}
              strokeWidth={10}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            {segments.map((s, i) => (
              <line
                key={i}
                x1={s.a.x}
                y1={s.a.y}
                x2={s.b.x}
                y2={s.b.y}
                stroke={s.color}
                strokeWidth={5}
                strokeLinecap="round"
              />
            ))}
            <circle cx={segments[0].a.x} cy={segments[0].a.y} r={5} fill="#fff" stroke="#000" strokeWidth={2} />
          </svg>
          <div className="mt-3 flex flex-wrap gap-4 text-xs text-muted">
            <Legend color={ZONE_COLOR.brake} label="Brake" />
            <Legend color={ZONE_COLOR.coast} label="Coast" />
            <Legend color={ZONE_COLOR.partial} label="Partial" />
            <Legend color={ZONE_COLOR.full} label="Full throttle" />
          </div>
        </div>
      )}
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-1 w-3.5 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}

