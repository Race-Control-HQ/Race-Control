"use client";

import { useMemo, useState } from "react";
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useRaceDrivers, useTelemetry, useTelemetryCompare, useFlags, useLapTimes, useCircuitMap } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import type { CircuitCorner, FlagPeriod, FlagsResponse, TelemetryTrace } from "@/lib/types";
import { flagColor, flagLabel } from "@/lib/flags";
import { formatMs } from "@/lib/format";
import { TelemetryMiniMap } from "./TelemetryMiniMap";

function lapFlag(lap: number, flagsData: FlagsResponse | undefined): FlagPeriod | null {
  if (!flagsData) return null;
  return flagsData.periods.find((p) => lap >= p.startLap && lap <= p.endLap) ?? null;
}

function useSyncedTraces(year: number, round: number, d1: string, d2: string, compare: boolean, lap: string) {
  const single = useTelemetry(year, round, !compare ? d1 || null : null, lap);
  const both = useTelemetryCompare(year, round, compare ? d1 || null : null, compare ? d2 || null : null);
  if (compare) {
    return { traces: both.data?.traces ?? [], isLoading: both.isLoading, error: both.error, available: both.data?.available };
  }
  return {
    traces: single.data?.trace ? [single.data.trace] : [],
    isLoading: single.isLoading,
    error: single.error,
    available: single.data?.available,
  };
}

function metricSeries(trace: TelemetryTrace, key: "speed" | "throttle" | "gear" | "rpm") {
  return trace.distance.map((d, i) => ({ distance: d, value: trace[key][i] }));
}

function MetricChart({
  title,
  unit,
  traces,
  metricKey,
  onScrub,
  corners,
  showCornerLabels = false,
  scrubDistance,
}: {
  title: string;
  unit: string;
  traces: TelemetryTrace[];
  metricKey: "speed" | "throttle" | "gear" | "rpm";
  onScrub: (distance: number | null) => void;
  corners: CircuitCorner[];
  showCornerLabels?: boolean;
  scrubDistance: number | null;
}) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">
        {title} {unit}
      </p>
      <ResponsiveContainer width="100%" height={120}>
        <LineChart
          margin={{ top: showCornerLabels ? 12 : 4, right: 8, bottom: 0, left: 0 }}
          onMouseMove={(s) => onScrub(typeof s.activeLabel === "number" ? s.activeLabel : null)}
          onMouseLeave={() => onScrub(null)}
        >
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis dataKey="distance" type="number" domain={["dataMin", "dataMax"]} hide />
          <YAxis stroke="var(--muted)" tick={{ fontSize: 10 }} width={32} />
          <Tooltip
            contentStyle={{ background: "var(--surface-raised)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }}
            labelFormatter={(v) => `${Math.round(Number(v))}m`}
          />
          {/* Corner markers, so a dip in the trace can be read against the
              part of the circuit it belongs to. Only the top chart carries
              labels; repeating them on all four is just noise. */}
          {corners.map((c) => (
            <ReferenceLine
              key={`${c.number}${c.letter}`}
              x={c.distanceMeters as number}
              stroke="var(--border)"
              strokeDasharray="2 3"
              label={
                showCornerLabels
                  ? { value: `${c.number}${c.letter}`, position: "top", fontSize: 9, fill: "var(--muted)" }
                  : undefined
              }
            />
          ))}
          {/* Where the pointer is: either scrubbed on a chart, or hovered on
              the mini-map, which reports the same lap distance back. */}
          {scrubDistance != null && (
            <ReferenceLine x={scrubDistance} stroke="var(--racing-red)" strokeWidth={1.5} />
          )}
          {traces.map((t) => (
            <Line
              key={t.code}
              data={metricSeries(t, metricKey)}
              dataKey="value"
              name={t.code}
              type="monotone"
              stroke={t.teamColor || "#9a9aa2"}
              dot={false}
              strokeWidth={1.5}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function TelemetryTab({ year, round }: { year: number; round: number }) {
  const { data: raceDrivers } = useRaceDrivers(year, round);
  const [d1, setD1] = useState("");
  const [d2, setD2] = useState("");
  const [compare, setCompare] = useState(false);
  const [selectedLap, setSelectedLap] = useState("fastest");
  const [scrubDistance, setScrubDistance] = useState<number | null>(null);

  const effectiveD1 = d1 || raceDrivers?.[0]?.code || "";
  const effectiveD2 = d2 || raceDrivers?.[1]?.code || "";
  const { traces, isLoading, error, available } = useSyncedTraces(year, round, effectiveD1, effectiveD2, compare, selectedLap);
  const { data: flagsData } = useFlags(year, round);
  const { data: lapTimesData } = useLapTimes(year, round);
  const { data: circuit } = useCircuitMap(year, round);

  // Only corners FastF1 gave a lap distance for can be placed on the x-axis.
  const corners = useMemo(
    () => (circuit?.corners ?? []).filter((c) => c.distanceMeters != null),
    [circuit],
  );

  const d1Laps = useMemo(
    () => lapTimesData?.drivers.find((d) => d.code === effectiveD1)?.laps ?? [],
    [lapTimesData, effectiveD1],
  );

  const bestLapTimes = useMemo(
    () => traces.map((t) => `${t.code} ${t.lapTime ?? "-"}`).join(" · "),
    [traces],
  );

  const activeFlag = useMemo(() => {
    const lap = traces[0]?.lapNumber;
    if (lap == null || !flagsData) return null;
    return flagsData.periods.find((p) => lap >= p.startLap && lap <= p.endLap) ?? null;
  }, [traces, flagsData]);

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <select
          value={effectiveD1}
          onChange={(e) => {
            setD1(e.target.value);
            setSelectedLap("fastest");
          }}
          className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm"
        >
          {raceDrivers?.map((d) => (
            <option key={d.code} value={d.code}>
              {d.code}
            </option>
          ))}
        </select>
        {!compare && (
          <select
            value={selectedLap}
            onChange={(e) => setSelectedLap(e.target.value)}
            className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm"
          >
            <option value="fastest">Fastest lap</option>
            {d1Laps.map((l) => {
              const flag = lapFlag(l.lap, flagsData);
              return (
                <option key={l.lap} value={String(l.lap)} style={flag ? { color: flagColor(flag.type) } : undefined}>
                  {flag ? "● " : ""}Lap {l.lap}: {formatMs(l.timeMs)}
                </option>
              );
            })}
          </select>
        )}
        <label className="flex items-center gap-1.5 text-sm text-muted">
          <input
            type="checkbox"
            checked={compare}
            onChange={(e) => {
              setCompare(e.target.checked);
              setSelectedLap("fastest");
            }}
            className="accent-[color:var(--racing-red)]"
          />
          Compare
        </label>
        {compare && <span className="text-xs text-muted">(fastest lap only)</span>}
        {compare && (
          <select value={effectiveD2} onChange={(e) => setD2(e.target.value)} className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm">
            {raceDrivers?.map((d) => (
              <option key={d.code} value={d.code}>
                {d.code}
              </option>
            ))}
          </select>
        )}
        <span className="tabular ml-auto text-sm text-muted">{bestLapTimes}</span>
      </div>

      {activeFlag && (
        <div
          className="mb-4 inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold"
          style={{ backgroundColor: `${flagColor(activeFlag.type)}26`, color: flagColor(activeFlag.type) }}
        >
          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: flagColor(activeFlag.type) }} aria-hidden />
          {flagLabel(activeFlag.type)} (lap {traces[0]?.lapNumber})
        </div>
      )}

      {isLoading && <LoadingState label="Loading telemetry…" />}
      {error && <ErrorState message="Telemetry data isn't available for this lap." />}
      {!isLoading && !error && (available === false || traces.length === 0) && (
        <EmptyState message="No telemetry available for this selection." />
      )}

      {traces.length > 0 && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_260px]">
          <div className="flex flex-col gap-3">
            <MetricChart
              title="Speed"
              unit="(km/h)"
              traces={traces}
              metricKey="speed"
              onScrub={setScrubDistance}
              corners={corners}
              showCornerLabels
              scrubDistance={scrubDistance}
            />
            <MetricChart title="Throttle" unit="(%)" traces={traces} metricKey="throttle" onScrub={setScrubDistance} corners={corners} scrubDistance={scrubDistance} />
            <MetricChart title="Gear" unit="" traces={traces} metricKey="gear" onScrub={setScrubDistance} corners={corners} scrubDistance={scrubDistance} />
            <MetricChart title="RPM" unit="" traces={traces} metricKey="rpm" onScrub={setScrubDistance} corners={corners} scrubDistance={scrubDistance} />
          </div>
          <div className="flex flex-col gap-2">
            <div className="aspect-square rounded-lg border border-border bg-surface p-3">
              <TelemetryMiniMap
                year={year}
                round={round}
                traces={traces}
                scrubDistance={scrubDistance}
                onHoverDistance={setScrubDistance}
              />
            </div>
            <p className="text-center text-xs text-muted">
              Hover the track or a chart to line them up
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
