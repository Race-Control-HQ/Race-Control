"use client";

import { useMemo, useRef } from "react";
import { useCircuitMap } from "@/lib/api";
import { createProjector, densifyTrace, interpolateXYAtDistance, rotatePoints, type RawPoint } from "@/lib/trackGeometry";
import type { TelemetryTrace } from "@/lib/types";

const VB = 260;

/** A projected outline point that still knows how far along the lap it sits. */
interface TracePoint extends RawPoint {
  distance: number;
}

/**
 * Track outline for the telemetry tab, with a dot per driver at the scrubbed
 * point on the lap. Hovering the map reports the corresponding lap distance
 * back up, so the charts can highlight the same spot.
 *
 * The outline deliberately comes from the circuit endpoint rather than from
 * the telemetry trace being charted. A single lap's *position* channel is
 * often degraded: it updates ~10x slower than the rest of the telemetry and
 * can carry as few as ~20 genuinely distinct samples, which draws as a
 * hard-edged polygon. Neither resampling nor curve smoothing can recover
 * shape that was never in the source. The circuit endpoint already solves
 * this (`_pick_outline_lap` picks a lap with a rich position trace, falling
 * back across laps until it finds one), which is why every other map in the
 * app looks smooth; they all draw from it. The telemetry trace is still
 * used for the dots, since those must reflect the selected lap.
 */
export function TelemetryMiniMap({
  year,
  round,
  traces,
  scrubDistance,
  onHoverDistance,
}: {
  year: number;
  round: number;
  traces: TelemetryTrace[];
  scrubDistance: number | null;
  onHoverDistance?: (distance: number | null) => void;
}) {
  const { data: circuit } = useCircuitMap(year, round);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const longest = useMemo(
    () => (traces.length ? traces.reduce((a, b) => (a.x.length > b.x.length ? a : b)) : null),
    [traces],
  );

  // Both sources are in the same underlying coordinate space, so the dots can
  // be rotated and projected with exactly the transform derived from whichever
  // outline is in use.
  const { outline, projector, rotation } = useMemo(() => {
    const fromCircuit = circuit && circuit.points.length > 0;
    const raw: TracePoint[] = fromCircuit
      ? circuit.points.map((p) => ({ x: p.x, y: p.y, distance: p.distance }))
      : longest
        ? longest.x.map((x, i) => ({ x, y: longest.y[i], distance: longest.distance[i] }))
        : [];
    if (raw.length === 0) return { outline: [] as (TracePoint & { screen: RawPoint })[], projector: null, rotation: 0 };

    const rot = fromCircuit ? circuit.rotation : 0;
    // densifyTrace interpolates any extra numeric fields, so `distance` is
    // carried through the smoothing pass alongside the geometry.
    const dense = densifyTrace(raw, 6);
    const rotated = rotatePoints(dense, rot);
    const proj = createProjector(rotated, VB, VB);
    return {
      outline: dense.map((p, i) => ({ ...p, screen: proj.project(rotated[i]) })),
      projector: proj,
      rotation: rot,
    };
  }, [circuit, longest]);

  const scrubDots = useMemo(() => {
    if (scrubDistance == null || !projector) return [];
    return traces.map((t) => {
      // Interpolated rather than snapped to the nearest sample, so the marker
      // glides as the pointer moves instead of hopping between position
      // updates (which are far coarser than the rest of the telemetry).
      const exact = interpolateXYAtDistance(t.distance, t.x, t.y, scrubDistance);
      const [rotated] = rotatePoints([exact], rotation);
      return { color: t.teamColor || "#e10600", code: t.code, point: projector.project(rotated) };
    });
  }, [traces, scrubDistance, projector, rotation]);

  if (outline.length === 0) return null;

  const handleMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!onHoverDistance) return;
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return;
    // Client pixels -> viewBox units (the SVG is square, so one scale factor).
    const px = ((e.clientX - rect.left) / rect.width) * VB;
    const py = ((e.clientY - rect.top) / rect.height) * VB;

    let best = outline[0];
    let bestDist = Infinity;
    for (const p of outline) {
      const dx = p.screen.x - px;
      const dy = p.screen.y - py;
      const d2 = dx * dx + dy * dy;
      if (d2 < bestDist) {
        bestDist = d2;
        best = p;
      }
    }
    onHoverDistance(best.distance);
  };

  return (
    <svg
      ref={svgRef}
      viewBox={`0 0 ${VB} ${VB}`}
      className={`h-full w-full ${onHoverDistance ? "cursor-crosshair" : ""}`}
      onMouseMove={handleMove}
      onMouseLeave={() => onHoverDistance?.(null)}
    >
      <polyline
        points={outline.map((p) => `${p.screen.x},${p.screen.y}`).join(" ")}
        fill="none"
        stroke="#4b4b52"
        strokeWidth={4}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {scrubDots.map((d, i) => (
        <circle key={i} cx={d.point.x} cy={d.point.y} r={6} fill={d.color} stroke="#0a0a0c" strokeWidth={2} />
      ))}
    </svg>
  );
}
