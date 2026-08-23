"use client";

import { useEffect, useMemo, useRef } from "react";
import { createProjector, densifyTrace, rotatePoints } from "@/lib/trackGeometry";
import { LoadingState } from "@/components/StateViews";
import type { CircuitMap, ReplayEntry, ReplayLapPositions } from "@/lib/types";

const VB = 600;

type SnappedTrace = {
  /** Monotonic outline positions covering exactly one complete lap. */
  positions: number[];
};

function nearestTrackIndex(
  point: { x: number; y: number },
  outline: { x: number; y: number }[],
): number {
  let nearest = 0;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (let index = 0; index < outline.length; index++) {
    const dx = outline[index].x - point.x;
    const dy = outline[index].y - point.y;
    const distance = dx * dx + dy * dy;
    if (distance < nearestDistance) {
      nearest = index;
      nearestDistance = distance;
    }
  }
  return nearest;
}

function traceDirection(indices: number[], outlineLength: number): 1 | -1 {
  let signedTravel = 0;
  for (let index = 1; index < indices.length; index++) {
    let delta = (indices[index] - indices[index - 1] + outlineLength) % outlineLength;
    if (delta > outlineLength / 2) delta -= outlineLength;
    signedTravel += delta;
  }
  return signedTravel >= 0 ? 1 : -1;
}

function unwrapTrace(indices: number[], outlineLength: number): SnappedTrace {
  const direction = traceDirection(indices, outlineLength);
  const positions = [indices[0]];

  for (let index = 1; index < indices.length; index++) {
    const previousRaw = indices[index - 1];
    const currentRaw = indices[index];
    const delta =
      direction > 0
        ? (currentRaw - previousRaw + outlineLength) % outlineLength
        : -((previousRaw - currentRaw + outlineLength) % outlineLength);
    const next = positions[positions.length - 1] + delta;
    // Position telemetry can repeat a held sample. Keeping it would reserve a
    // full chunk of replay time for no movement at all.
    if (Math.abs(next - positions[positions.length - 1]) > 1) positions.push(next);
  }

  const target = positions[0] + direction * outlineLength;
  const remaining = Math.abs(target - positions[positions.length - 1]);
  if (remaining <= outlineLength * 0.04) {
    // A final sample close to the line is the finish sample: align it exactly
    // rather than adding a second near-zero start-line segment.
    positions[positions.length - 1] = target;
  } else {
    // Downsampling often omits the exact finish sample. Add it once so the
    // final interval completes the lap instead of stopping short.
    positions.push(target);
  }

  return { positions };
}

export function ReplayTrackMap({
  circuit,
  circuitLoading = false,
  positionsLoading = false,
  lapPositions,
  driverTeamColors,
  order,
  lapFraction,
  raceProgressRef,
  totalLaps,
  playing,
  active = true,
}: {
  circuit: CircuitMap | undefined;
  /** The track outline request is still in flight, distinct from "there is
   * no outline", which is only knowable once it has finished. */
  circuitLoading?: boolean;
  /** The car-position request is still in flight; the track can already be
   * drawn, but there are no cars to place on it yet. */
  positionsLoading?: boolean;
  /** Position samples for the lap currently on screen, or undefined if unavailable. */
  lapPositions: ReplayLapPositions | undefined;
  driverTeamColors: Record<string, string | null>;
  /** Timing tower for the active lap, used to preserve real gaps on track. */
  order: ReplayEntry[];
  /** Explicit fraction used while paused or scrubbing. */
  lapFraction: number;
  /** Shared replay clock owned by ReplayTab; read directly at animation rate. */
  raceProgressRef: { current: number };
  totalLaps: number;
  playing: boolean;
  /** Whether the map is actually visible right now. The per-frame animation
   * loop below is real CPU work (trig + DOM writes on every driver, every
   * frame); on a phone, running it while the map is scrolled off or
   * collapsed behind the "Show map" toggle is wasted battery/CPU that can
   * fight the running-order list's own animations for frame budget.
   * Defaults to true so callers that don't care (e.g. desktop, which never
   * hides the map) don't need to pass anything. */
  active?: boolean;
}) {
  const { screenOutline, rotatedOutline, projector } = useMemo(() => {
    if (!circuit || circuit.outline.length === 0) {
      return {
        screenOutline: [] as { x: number; y: number }[],
        rotatedOutline: [] as { x: number; y: number }[],
        projector: null,
      };
    }
    // Smooth the drawn outline the same way the circuits page does: the
    // backend's ~350 evenly-spaced points are accurate but still show
    // visible facets through tight corners without curve interpolation.
    const dense = densifyTrace(circuit.outline, 6);
    const rotated = rotatePoints(dense, circuit.rotation);
    const proj = createProjector(rotated, VB, VB, 36);
    return {
      screenOutline: rotated.map((point) => proj.project(point)),
      rotatedOutline: rotated,
      projector: proj,
    };
  }, [circuit]);

  const rotation = circuit?.rotation ?? 0;
  const drivers = lapPositions ? Object.keys(lapPositions.positions) : [];
  const timingByDriver = useMemo(
    () => new Map(order.map((entry) => [entry.driver, entry])),
    [order],
  );
  const leaderLapMs = order[0]?.lapTimeMs ?? null;
  const snappedTraces = useMemo(() => {
    const traces: Record<string, SnappedTrace> = {};
    if (!lapPositions || rotatedOutline.length < 2) return traces;

    const theta = (rotation * Math.PI) / 180;
    const cos = Math.cos(theta);
    const sin = Math.sin(theta);
    for (const [code, points] of Object.entries(lapPositions.positions)) {
      const indices = points
        .filter(([x, y]) => Number.isFinite(x) && Number.isFinite(y))
        .map(([x, y]) =>
          nearestTrackIndex(
            { x: x * cos - y * sin, y: x * sin + y * cos },
            rotatedOutline,
          ),
        );
      if (indices.length > 0) {
        traces[code] = unwrapTrace(indices, rotatedOutline.length);
      }
    }
    return traces;
  }, [lapPositions, rotatedOutline, rotation]);

  // Cars are animated imperatively (direct DOM writes via refs), not through
  // React state, so a ~60fps loop never triggers a React re-render; this
  // both keeps it smooth and avoids state-update ordering issues around
  // pause/resume and lap changes.
  const dotRefs = useRef<Record<string, SVGGElement | null>>({});
  const frameRef = useRef<number>(0);

  useEffect(() => {
    // Not on screen right now (CSS-hidden behind the mobile "Show map"
    // toggle): skip both the immediate placement and the per-frame loop
    // below entirely. There's nothing to sync visually while it's hidden;
    // `place(0)` runs again as soon as `active` flips back to true.
    if (!active) return;

    function place(baseFraction: number) {
      if (!lapPositions || screenOutline.length < 2) return;
      for (const code of Object.keys(lapPositions.positions)) {
        const el = dotRefs.current[code];
        const trace = snappedTraces[code];
        if (!el || !trace || trace.positions.length < 2) continue;
        const gapMs = timingByDriver.get(code)?.gapMs;
        const gapFraction =
          gapMs != null && leaderLapMs != null && leaderLapMs > 0
            ? gapMs / leaderLapMs
            : 0;
        const rawFraction = baseFraction - gapFraction;
        const fraction = rawFraction - Math.floor(rawFraction);
        const samplePosition = fraction * (trace.positions.length - 1);
        const sampleSegment = Math.floor(samplePosition);
        const lower = Math.min(sampleSegment, trace.positions.length - 2);
        const upper = lower + 1;
        const amount = samplePosition - sampleSegment;
        const from = trace.positions[lower];
        const to = trace.positions[upper];
        const outlineLength = screenOutline.length;
        const rawOutlinePosition = from + (to - from) * amount;
        const outlinePosition =
          ((rawOutlinePosition % outlineLength) + outlineLength) % outlineLength;
        const outlineLower = Math.floor(outlinePosition);
        const outlineUpper = (outlineLower + 1) % outlineLength;
        const outlineAmount = outlinePosition - outlineLower;
        const first = screenOutline[outlineLower];
        const second = screenOutline[outlineUpper];
        const x = first.x + (second.x - first.x) * outlineAmount;
        const y = first.y + (second.y - first.y) * outlineAmount;
        el.setAttribute("transform", `translate(${x}, ${y})`);
      }
    }

    function clockFraction() {
      const progress = raceProgressRef.current;
      if (totalLaps > 0 && progress >= totalLaps) return 1;
      return progress - Math.floor(progress);
    }

    // Place immediately from the shared clock so pause, resume and lap changes
    // never reset cars to the first position sample.
    place(playing ? clockFraction() : lapFraction);

    if (!playing) return;

    function step() {
      place(clockFraction());
      frameRef.current = requestAnimationFrame(step);
    }
    frameRef.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frameRef.current);
  }, [
    playing,
    lapPositions,
    active,
    lapFraction,
    raceProgressRef,
    timingByDriver,
    leaderLapMs,
    totalLaps,
    screenOutline,
    snappedTraces,
  ]);

  // Only report missing data once the request has actually finished; while
  // it's in flight there is legitimately nothing to draw yet, and showing the
  // failure copy in the meantime reads as a hard error that then silently
  // fixes itself.
  if (circuitLoading) {
    return (
      <div className="flex h-72 items-center justify-center rounded-lg border border-border bg-surface">
        <LoadingState label="Loading track map…" />
      </div>
    );
  }

  if (!projector || screenOutline.length === 0) {
    return (
      <div className="flex h-72 items-center justify-center rounded-lg border border-dashed border-border text-sm text-muted">
        No track position data available for this race.
      </div>
    );
  }

  const start = screenOutline[0];

  return (
    <div className="relative">
      <div className="pointer-events-none absolute left-3 top-3 z-10 flex items-center gap-1.5 rounded-full border border-racing-red/30 bg-surface/90 px-2.5 py-1 text-[10px] font-extrabold tracking-wide text-racing-red-text backdrop-blur">
        <span className={playing ? "h-1.5 w-1.5 animate-pulse rounded-full bg-racing-red-text" : "h-1.5 w-1.5 rounded-full bg-muted"} />
        LIVE REPLAY
      </div>
      {/* The viewBox is square, so an unconstrained `w-full` makes the map as
          tall as the container is wide, over 1100px on a desktop layout,
          pushing the running order entirely below the fold. Cap the height and
          let preserveAspectRatio letterbox it instead. */}
      <svg
        viewBox={`0 0 ${VB} ${VB}`}
        preserveAspectRatio="xMidYMid meet"
        className="max-h-[45vh] w-full rounded-lg border border-border bg-surface lg:max-h-[calc(100vh-10rem)]"
      >
        <polyline
          points={screenOutline.map((p) => `${p.x},${p.y}`).join(" ")}
          fill="none"
          stroke="#2a2a30"
          strokeWidth={10}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        {start && <circle cx={start.x} cy={start.y} r={6} fill="#f2f2f4" stroke="#0a0a0c" strokeWidth={2} />}
        {drivers.map((code) => (
          <g
            key={code}
            ref={(el) => {
              dotRefs.current[code] = el;
            }}
          >
            <circle r={7} fill={driverTeamColors[code] || "#6b6b72"} stroke="#0a0a0c" strokeWidth={1.5} />
            <text y={-11} textAnchor="middle" fontSize={9} fontWeight={600} fill="var(--foreground)">
              {code}
            </text>
          </g>
        ))}
      </svg>

      {/* The track outline arrives well before the per-lap car positions, so
          the map can sit there looking empty. Say why rather than leaving it
          ambiguous. */}
      {positionsLoading && drivers.length === 0 && (
        <div className="absolute inset-x-0 bottom-3 flex justify-center">
          <span className="flex items-center gap-2 rounded-full border border-border bg-surface/90 px-3 py-1.5 text-xs text-muted backdrop-blur">
            <span
              role="status"
              aria-label="Loading"
              className="h-3 w-3 animate-spin rounded-full border-2 border-border border-t-racing-red"
            />
            Loading car positions…
          </span>
        </div>
      )}
      {!positionsLoading && drivers.length === 0 && (
        <div className="absolute inset-x-0 bottom-3 flex justify-center">
          <span className="rounded-full border border-border bg-surface/90 px-3 py-1.5 text-xs text-muted backdrop-blur">
            Car positions aren&apos;t available for this lap.
          </span>
        </div>
      )}
    </div>
  );
}
