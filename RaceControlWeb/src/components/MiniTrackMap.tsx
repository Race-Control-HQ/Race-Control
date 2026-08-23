"use client";

import { useMemo } from "react";
import { createProjector, densifyTrace, rotatePoints } from "@/lib/trackGeometry";
import type { CircuitMap } from "@/lib/types";

const VB = 200;

/**
 * A small, plain-line circuit outline for dashboard cards: no speed
 * gradient, no corner numbers, just the shape (the full `TrackMap` on the
 * circuit detail page already covers the detailed version).
 *
 * The outline only exists once a session has actually recorded car
 * positions, so there's nothing to draw for a race weekend that hasn't
 * happened yet; `isLoading` covers the fetch itself, and an empty/missing
 * `circuit` (fetch succeeded, but no outline in it) renders nothing rather
 * than an empty box, since the caller decides what to show instead.
 */
export function MiniTrackMap({
  circuit,
  isLoading,
  className = "h-24 w-24",
}: {
  circuit: CircuitMap | undefined;
  isLoading?: boolean;
  className?: string;
}) {
  const screenOutline = useMemo(() => {
    if (!circuit || circuit.outline.length === 0) return [];
    const dense = densifyTrace(circuit.outline, 4);
    const rotated = rotatePoints(dense, circuit.rotation);
    const projector = createProjector(rotated, VB, VB, 14);
    return rotated.map((p) => projector.project(p));
  }, [circuit]);

  if (isLoading) {
    return (
      <div className={`flex flex-col items-center justify-center gap-1.5 rounded-md bg-surface-raised ${className}`}>
        <span
          role="status"
          aria-label="Loading"
          className="h-4 w-4 animate-spin rounded-full border-2 border-border border-t-racing-red"
        />
      </div>
    );
  }

  if (screenOutline.length === 0) return null;

  return (
    <svg viewBox={`0 0 ${VB} ${VB}`} className={className}>
      <polyline
        points={screenOutline.map((p) => `${p.x},${p.y}`).join(" ")}
        fill="none"
        stroke="currentColor"
        strokeWidth={6}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}
