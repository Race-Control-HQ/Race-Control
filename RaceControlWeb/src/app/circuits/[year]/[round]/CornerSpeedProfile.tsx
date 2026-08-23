"use client";

import { useMemo, useState } from "react";
import { createProjector, rotatePoints } from "@/lib/trackGeometry";
import type { CircuitCorner, CircuitMap } from "@/lib/types";

const VB = 260;

function tierColor(speed: number): string {
  if (speed < 120) return "#e10600";
  if (speed < 200) return "#ff9f0a";
  return "#30d158";
}

/**
 * Corner-by-corner speed as a compact bar profile, grouped slow/medium/fast,
 * paired with the circuit outline so a corner number can be matched back to
 * where it actually sits on track. Turns corner markers into a circuit
 * "rhythm" fingerprint.
 */
export function CornerSpeedProfile({ map }: { map: CircuitMap }) {
  const corners = useMemo(() => map.corners.filter((c) => c.speed != null), [map.corners]);
  const [selected, setSelected] = useState<CircuitCorner | null>(null);

  const { projected, outlineD } = useMemo(() => {
    if (map.outline.length < 2) return { projected: [] as { x: number; y: number; corner: CircuitCorner }[], outlineD: "" };
    const rotatedOutline = rotatePoints(map.outline, map.rotation);
    const projector = createProjector(rotatedOutline, VB, VB, 20);
    const outlinePoints = rotatedOutline.map((p) => projector.project(p));
    const d = outlinePoints.map((p, i) => `${i === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ") + " Z";
    const rotatedCorners = rotatePoints(corners, map.rotation);
    const projectedCorners = rotatedCorners.map((p, i) => ({ ...projector.project(p), corner: corners[i] }));
    return { projected: projectedCorners, outlineD: d };
  }, [map.outline, map.rotation, corners]);

  if (corners.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border px-4 py-10 text-center text-sm text-muted">
        Corner speed cross-references aren&apos;t available for this circuit.
      </div>
    );
  }

  const maxSpeed = Math.max(...corners.map((c) => c.speed ?? 0));

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-3 text-xs text-muted">Speed at each corner · km/h · click a bar for detail</p>

      <div className="flex items-end gap-1 overflow-x-auto pb-2" style={{ height: 180 }}>
        {corners.map((corner) => {
          const speed = corner.speed ?? 0;
          const barHeight = (speed / maxSpeed) * 150;
          return (
            <button
              key={`${corner.number}${corner.letter}`}
              type="button"
              onClick={() => setSelected(corner)}
              className="flex w-7 flex-none flex-col items-center justify-end"
            >
              <div className="w-5 rounded-t" style={{ height: barHeight, backgroundColor: tierColor(speed) }} />
              <span className="mt-1 text-[10px] font-bold text-muted">
                {corner.number}
                {corner.letter}
              </span>
            </button>
          );
        })}
      </div>

      <div className="mt-2 flex flex-wrap gap-4 text-xs text-muted">
        <Legend color="#e10600" label="Slow (<120)" />
        <Legend color="#ff9f0a" label="Medium" />
        <Legend color="#30d158" label="Fast (>200)" />
      </div>

      {selected && (
        <div className="mt-3 flex flex-wrap items-center gap-3 rounded-md bg-surface-raised px-3 py-2 text-sm">
          <span className="font-bold text-foreground">
            Turn {selected.number}
            {selected.letter}
          </span>
          {selected.speed != null && <span className="text-muted">{Math.round(selected.speed)} km/h</span>}
          {selected.angle != null && <span className="text-xs text-muted/70">{Math.round(Math.abs(selected.angle))}° turn</span>}
        </div>
      )}

      {outlineD && (
        <svg viewBox={`0 0 ${VB} ${VB}`} className="mx-auto mt-4 w-full max-w-sm">
          <path d={outlineD} fill="none" stroke="var(--border)" strokeWidth={8} strokeLinecap="round" strokeLinejoin="round" />
          {projected.map((p) => {
            const isSelected = selected && selected.number === p.corner.number && selected.letter === p.corner.letter;
            return (
              <circle
                key={`${p.corner.number}${p.corner.letter}`}
                cx={p.x}
                cy={p.y}
                r={isSelected ? 9 : 6}
                fill={tierColor(p.corner.speed ?? 0)}
                stroke={isSelected ? "#fff" : "none"}
                strokeWidth={2}
                className="cursor-pointer"
                onClick={() => setSelected(p.corner)}
              />
            );
          })}
        </svg>
      )}
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-2 w-3 rounded-sm" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
