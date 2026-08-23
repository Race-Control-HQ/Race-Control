"use client";

import { useMemo, useState } from "react";
import { createProjector, densifyTrace, rotatePoints, speedColor } from "@/lib/trackGeometry";
import type { CircuitMap, CircuitCorner } from "@/lib/types";

const VB = 600;
const DRS_OPEN = new Set([10, 12, 14]);
const DRS_COLOR = "#22d3ee"; // cyan, deliberately far from the green/yellow end of the speed gradient
const MARSHAL_LIGHT_COLOR = "#f59e0b";
const MARSHAL_SECTOR_COLOR = "#a78bfa";

interface ScreenSeg {
  a: { x: number; y: number };
  b: { x: number; y: number };
  color: string;
  drsOpen: boolean;
}

interface DrsZone {
  id: number;
  path: string;
  labelPos: { x: number; y: number };
  leaderFrom: { x: number; y: number };
}

interface CornerScreen extends CircuitCorner {
  screen: { x: number; y: number };
}

interface MarkerScreen {
  number: number;
  screen: { x: number; y: number };
}

export function TrackMap({ map }: { map: CircuitMap }) {
  const [showMarshalLights, setShowMarshalLights] = useState(true);
  const [showMarshalSectors, setShowMarshalSectors] = useState(true);
  const [hovered, setHovered] = useState<CornerScreen | null>(null);

  const { segments, corners, marshalLights, marshalSectors, drsZones, start, minSpeed, maxSpeed } = useMemo(() => {
    if (map.points.length === 0) {
      return {
        segments: [] as ScreenSeg[],
        corners: [] as CornerScreen[],
        marshalLights: [] as MarkerScreen[],
        marshalSectors: [] as MarkerScreen[],
        drsZones: [] as DrsZone[],
        start: null as { x: number; y: number } | null,
        minSpeed: 0,
        maxSpeed: 0,
      };
    }

    // The backend gives ~350 evenly-spaced points per lap: accurate, but
    // still shows visible straight-line facets through tight corners at the
    // pixel scale they render at. Smooth the drawn line with a Catmull-Rom
    // spline rather than connecting the raw samples directly.
    const dense = densifyTrace(map.points, 6);

    const rotated = rotatePoints(
      dense.map((p) => ({ x: p.x, y: p.y })),
      map.rotation,
    );
    const projector = createProjector(rotated, VB, VB, 36);
    const screen = rotated.map((p) => projector.project(p));

    const speeds = map.points.map((p) => p.speed);
    const minSpeed = Math.min(...speeds);
    const maxSpeed = Math.max(...speeds);

    const segments: ScreenSeg[] = [];
    for (let i = 1; i < screen.length; i++) {
      const a = screen[i - 1];
      const b = screen[i];
      const speed = (dense[i - 1].speed + dense[i].speed) / 2;
      // drs is a discrete code (10/12/14 = open); densifyTrace linearly
      // interpolates it like any other numeric field, so round back to the
      // nearest whole code before checking membership.
      const drsOpen = DRS_OPEN.has(Math.round(dense[i - 1].drs)) && DRS_OPEN.has(Math.round(dense[i].drs));
      segments.push({ a, b, color: speedColor(speed, minSpeed, maxSpeed), drsOpen });
    }

    // Track centroid, used to figure out which side of each segment is
    // "outward" so the DRS band and its label sit beside the track rather
    // than smeared along the racing line itself.
    const centroid = {
      x: screen.reduce((sum, p) => sum + p.x, 0) / screen.length,
      y: screen.reduce((sum, p) => sum + p.y, 0) / screen.length,
    };
    const OFFSET = 9;
    const offsetOutward = (p: { x: number; y: number }, nx: number, ny: number, dist: number) => {
      const toCentroidX = centroid.x - p.x;
      const toCentroidY = centroid.y - p.y;
      // If the normal points *toward* the centroid, flip it so the offset goes outward.
      const sign = nx * toCentroidX + ny * toCentroidY > 0 ? -1 : 1;
      return { x: p.x + nx * dist * sign, y: p.y + ny * dist * sign };
    };

    // Group contiguous DRS-open segments into zones so each one gets its own
    // offset band + floating label, instead of a single thin overlay that
    // blends into the speed-gradient line beneath it.
    const drsZones: DrsZone[] = [];
    let zoneStart = -1;
    for (let i = 0; i <= segments.length; i++) {
      const open = i < segments.length && segments[i].drsOpen;
      if (open && zoneStart === -1) {
        zoneStart = i;
      } else if (!open && zoneStart !== -1) {
        const zoneSegs = segments.slice(zoneStart, i);
        const offsetPoints = zoneSegs.map((s) => {
          const dx = s.b.x - s.a.x;
          const dy = s.b.y - s.a.y;
          const len = Math.hypot(dx, dy) || 1;
          const nx = -dy / len;
          const ny = dx / len;
          return {
            a: offsetOutward(s.a, nx, ny, OFFSET),
            b: offsetOutward(s.b, nx, ny, OFFSET),
          };
        });
        const path = offsetPoints
          .map((p, idx) => `${idx === 0 ? "M" : "L"} ${p.a.x.toFixed(1)} ${p.a.y.toFixed(1)} L ${p.b.x.toFixed(1)} ${p.b.y.toFixed(1)}`)
          .join(" ");
        const mid = offsetPoints[Math.floor(offsetPoints.length / 2)];
        const midOnTrack = zoneSegs[Math.floor(zoneSegs.length / 2)].a;
        // Push the label further out than the band itself so it reads as a
        // floating annotation pointing at the zone, not part of the track art.
        const dx = mid.a.x - midOnTrack.x;
        const dy = mid.a.y - midOnTrack.y;
        const dlen = Math.hypot(dx, dy) || 1;
        const labelPos = {
          x: midOnTrack.x + (dx / dlen) * (OFFSET + 16),
          y: midOnTrack.y + (dy / dlen) * (OFFSET + 16),
        };
        drsZones.push({ id: zoneStart, path, labelPos, leaderFrom: midOnTrack });
        zoneStart = -1;
      }
    }

    const rotatedCorners = rotatePoints(
      map.corners.map((c) => ({ x: c.x, y: c.y })),
      map.rotation,
    );
    const corners: CornerScreen[] = map.corners.map((c, i) => ({ ...c, screen: projector.project(rotatedCorners[i]) }));

    const rotatedLights = rotatePoints(
      map.marshalLights.map((m) => ({ x: m.x, y: m.y })),
      map.rotation,
    );
    const marshalLights: MarkerScreen[] = map.marshalLights.map((m, i) => ({
      number: m.number,
      screen: projector.project(rotatedLights[i]),
    }));

    const rotatedSectors = rotatePoints(
      map.marshalSectors.map((m) => ({ x: m.x, y: m.y })),
      map.rotation,
    );
    const marshalSectors: MarkerScreen[] = map.marshalSectors.map((m, i) => ({
      number: m.number,
      screen: projector.project(rotatedSectors[i]),
    }));

    const start = screen[0] ?? null;

    return { segments, corners, marshalLights, marshalSectors, drsZones, start, minSpeed, maxSpeed };
  }, [map]);

  if (segments.length === 0) {
    return (
      <div className="flex h-80 items-center justify-center rounded-lg border border-dashed border-border text-sm text-muted">
        No track position data available for this race.
      </div>
    );
  }

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center gap-4 text-xs text-muted">
        {map.marshalLights.length > 0 && (
          <label className="flex cursor-pointer items-center gap-1.5">
            <input type="checkbox" checked={showMarshalLights} onChange={(e) => setShowMarshalLights(e.target.checked)} />
            <span className="inline-block h-2 w-2 rounded-full" style={{ background: MARSHAL_LIGHT_COLOR }} />
            Marshal lights
          </label>
        )}
        {map.marshalSectors.length > 0 && (
          <label className="flex cursor-pointer items-center gap-1.5">
            <input type="checkbox" checked={showMarshalSectors} onChange={(e) => setShowMarshalSectors(e.target.checked)} />
            <span className="inline-block h-2 w-2 rounded-sm" style={{ background: MARSHAL_SECTOR_COLOR }} />
            Marshal sectors
          </label>
        )}
      </div>

      <div className="relative">
        <svg viewBox={`0 0 ${VB} ${VB}`} className="w-full rounded-lg border border-border bg-surface">
          {/* base track surface */}
          <polyline
            points={segments.map((s) => `${s.a.x},${s.a.y}`).concat(`${segments[segments.length - 1].b.x},${segments[segments.length - 1].b.y}`).join(" ")}
            fill="none"
            stroke="#2a2a30"
            strokeWidth={10}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          {/* speed-gradient racing line */}
          {segments.map((s, i) => (
            <line key={i} x1={s.a.x} y1={s.a.y} x2={s.b.x} y2={s.b.y} stroke={s.color} strokeWidth={5} strokeLinecap="round" />
          ))}

          {/* DRS zones: a distinct offset band beside the track (not overlaid on
              it, so it never blends into the green/yellow end of the speed
              gradient) plus a floating "DRS" label with a leader line. */}
          {drsZones.map((z) => (
            <g key={z.id}>
              <path d={z.path} fill="none" stroke={DRS_COLOR} strokeWidth={3} strokeLinecap="round" opacity={0.95} />
              <line
                x1={z.leaderFrom.x}
                y1={z.leaderFrom.y}
                x2={z.labelPos.x}
                y2={z.labelPos.y}
                stroke={DRS_COLOR}
                strokeWidth={1}
                strokeDasharray="2,2"
                opacity={0.7}
              />
              <g transform={`translate(${z.labelPos.x}, ${z.labelPos.y})`}>
                <rect x={-13} y={-7} width={26} height={14} rx={4} fill={DRS_COLOR} />
                <text x={0} y={3.5} textAnchor="middle" fontSize={8} fontWeight={700} fill="#0a0a0c">
                  DRS
                </text>
              </g>
            </g>
          ))}

          {/* marshal sectors: boundary ticks used by stewards to divide the circuit */}
          {showMarshalSectors &&
            marshalSectors.map((m) => (
              <rect
                key={`sector-${m.number}`}
                x={m.screen.x - 3}
                y={m.screen.y - 3}
                width={6}
                height={6}
                fill={MARSHAL_SECTOR_COLOR}
                stroke="var(--surface)"
                strokeWidth={1}
              >
                <title>Marshal sector {m.number}</title>
              </rect>
            ))}

          {/* marshal lights: flag/light panels around the circuit */}
          {showMarshalLights &&
            marshalLights.map((m) => (
              <circle key={`light-${m.number}`} cx={m.screen.x} cy={m.screen.y} r={3} fill={MARSHAL_LIGHT_COLOR} stroke="var(--surface)" strokeWidth={1}>
                <title>Marshal light {m.number}</title>
              </circle>
            ))}

          {/* start/finish */}
          {start && <circle cx={start.x} cy={start.y} r={7} fill="#f2f2f4" stroke="#0a0a0c" strokeWidth={2} />}

          {/* corner markers */}
          {corners.map((c) => {
            const key = `${c.number}${c.letter}`;
            const isHovered = hovered != null && `${hovered.number}${hovered.letter}` === key;
            return (
              <g
                key={key}
                onMouseEnter={() => setHovered(c)}
                onMouseLeave={() => setHovered((h) => (h != null && `${h.number}${h.letter}` === key ? null : h))}
                onClick={() => setHovered((h) => (h != null && `${h.number}${h.letter}` === key ? null : c))}
                className="cursor-pointer"
              >
                <circle
                  cx={c.screen.x}
                  cy={c.screen.y}
                  r={9}
                  fill={isHovered ? "var(--racing-red)" : "var(--surface-raised)"}
                  stroke="var(--border)"
                  strokeWidth={1}
                />
                <text x={c.screen.x} y={c.screen.y + 3.5} textAnchor="middle" fontSize={9} fill={isHovered ? "#fff" : "var(--muted)"}>
                  {c.number}
                  {c.letter}
                </text>
              </g>
            );
          })}
        </svg>

        {hovered && (
          <div
            className="pointer-events-none absolute z-10 min-w-[9rem] -translate-x-1/2 -translate-y-full rounded-md border border-border bg-surface-raised px-2.5 py-1.5 text-xs shadow-lg"
            style={{
              left: `${(hovered.screen.x / VB) * 100}%`,
              top: `${(hovered.screen.y / VB) * 100}%`,
              marginTop: -14,
            }}
          >
            <p className="font-semibold">
              Turn {hovered.number}
              {hovered.letter}
            </p>
            <div className="mt-0.5 flex flex-col gap-0.5 text-muted">
              {hovered.speed != null && <span>~{Math.round(hovered.speed)} km/h</span>}
              {hovered.angle != null && <span>{Math.round(Math.abs(hovered.angle))}° turn</span>}
              {hovered.distanceMeters != null && <span>{Math.round(hovered.distanceMeters)}m into lap</span>}
              {hovered.speed == null && hovered.angle == null && hovered.distanceMeters == null && (
                <span>No further data available for this corner.</span>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="mt-2 flex items-center justify-between text-xs text-muted">
        <span>Slow</span>
        <div className="h-1.5 flex-1 mx-2 rounded-full" style={{ background: "linear-gradient(90deg, #5b4fe8, #3b82f6, #22c55e, #eab308)" }} />
        <span>Fast</span>
      </div>
      <p className="mt-1 text-center text-xs text-muted">
        {Math.round(minSpeed)}–{Math.round(maxSpeed)} km/h · <span style={{ color: DRS_COLOR }}>cyan</span> = DRS zone
      </p>
    </div>
  );
}
