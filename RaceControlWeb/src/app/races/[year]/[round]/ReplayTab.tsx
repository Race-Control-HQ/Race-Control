"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import clsx from "clsx";
import { motion, AnimatePresence } from "motion/react";
import { useReplay, useReplayPositions, useCircuitMap } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState, TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import { TyreLegend } from "@/components/TyreLegend";
import { compoundColor } from "@/lib/tyres";
import { REPLAY_TICK_MS } from "@/lib/replay";
import { useLocalStorageFlag } from "@/lib/useLocalStorageFlag";
import { useMediaQuery } from "@/lib/useMediaQuery";
import type { ReplayLapPositions } from "@/lib/types";
import { ReplayTrackMap } from "./ReplayTrackMap";

export function ReplayTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useReplay(year, round);
  // The track outline and the car positions are separate requests from the
  // replay itself and resolve later, so their loading state has to be passed
  // down, otherwise the map reports "no data" during its own fetch.
  const { data: circuit, isLoading: circuitLoading } = useCircuitMap(year, round);
  const { data: positions, isLoading: positionsLoading } = useReplayPositions(year, round);
  const [currentLap, setCurrentLap] = useState(1);
  const [scrubFraction, setScrubFraction] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const raceProgressRef = useRef(0);
  const sliderRef = useRef<HTMLInputElement>(null);
  const animationRef = useRef(0);
  // Only affects the stacked (mobile) layout; on `lg` and up the map lives in
  // its own column and is always shown.
  const [mapCollapsed, setMapCollapsed] = useLocalStorageFlag("replay:map-collapsed", false);
  // `mapCollapsed` only actually hides the map below `lg` (see the `hidden
  // lg:block` class below); on desktop it's always visible regardless. The
  // map runs a per-frame animation loop while playing, so this tells it
  // whether it's really on screen and worth animating, rather than always
  // running that loop even while CSS-hidden on a phone.
  const isDesktopLayout = useMediaQuery("(min-width: 1024px)");
  const mapVisible = isDesktopLayout || !mapCollapsed;

  const frames = useMemo(() => data?.frames ?? [], [data]);
  const totalLaps = data?.totalLaps ?? 0;

  const framesByLap = useMemo(() => {
    const map = new Map<number, (typeof frames)[number]>();
    for (const replayFrame of frames) map.set(replayFrame.lap, replayFrame);
    return map;
  }, [frames]);

  const frame = useMemo(
    () =>
      framesByLap.get(currentLap) ??
      [...frames].reverse().find((candidate) => candidate.lap <= currentLap) ??
      frames[0],
    [currentLap, frames, framesByLap],
  );
  const previousFrame = useMemo(
    () => [...frames].reverse().find((candidate) => candidate.lap < currentLap),
    [currentLap, frames],
  );
  const fallbackGapsByLap = useMemo(() => {
    const cumulativeByDriver = new Map<string, number>();
    const gaps = new Map<number, Map<string, number>>();
    for (const replayFrame of frames) {
      for (const entry of replayFrame.order) {
        if (entry.lapTimeMs != null && entry.lapTimeMs > 0) {
          cumulativeByDriver.set(
            entry.driver,
            (cumulativeByDriver.get(entry.driver) ?? 0) + entry.lapTimeMs,
          );
        }
      }
      const leader = replayFrame.order.find((entry) => entry.position === 1);
      const leaderElapsed = leader ? cumulativeByDriver.get(leader.driver) : undefined;
      const lapGaps = new Map<string, number>();
      if (leaderElapsed != null) {
        for (const entry of replayFrame.order) {
          const elapsed = cumulativeByDriver.get(entry.driver);
          if (elapsed != null) lapGaps.set(entry.driver, Math.max(elapsed - leaderElapsed, 0));
        }
      }
      gaps.set(replayFrame.lap, lapGaps);
    }
    return gaps;
  }, [frames]);
  const effectiveOrder = useMemo(
    () =>
      frame?.order.map((entry) => {
        if (entry.gapMs != null) return entry;
        const gapMs = fallbackGapsByLap.get(frame.lap)?.get(entry.driver);
        if (gapMs == null) return entry;
        return {
          ...entry,
          gapMs,
          gap: entry.position === 1 ? "LEADER" : `+${(gapMs / 1000).toFixed(3)}`,
        };
      }) ?? [],
    [fallbackGapsByLap, frame],
  );

  const positionsByLap = useMemo(() => {
    const map = new Map<number, ReplayLapPositions>();
    for (const lp of positions?.laps ?? []) map.set(lp.lap, lp);
    return map;
  }, [positions]);

  const driverTeamColors = useMemo(() => {
    const out: Record<string, string | null> = {};
    for (const d of data?.drivers ?? []) out[d.code] = d.teamColor;
    return out;
  }, [data]);

  const setProgress = useCallback(
    (nextProgress: number, pause = true) => {
      const clamped = Math.min(Math.max(nextProgress, 0), Math.max(totalLaps, 1));
      raceProgressRef.current = clamped;
      if (sliderRef.current) sliderRef.current.value = String(clamped);
      const lap = Math.min(Math.floor(clamped) + 1, Math.max(totalLaps, 1));
      setCurrentLap(lap);
      setScrubFraction(clamped >= totalLaps && totalLaps > 0 ? 1 : clamped - Math.floor(clamped));
      if (pause) setPlaying(false);
    },
    [totalLaps],
  );

  useEffect(() => {
    const firstLap = frames[0]?.lap ?? 1;
    const resetFrame = requestAnimationFrame(() => {
      setProgress(Math.max(firstLap - 1, 0));
    });
    return () => cancelAnimationFrame(resetFrame);
  }, [frames, setProgress, year, round]);

  useEffect(() => {
    if (!playing || totalLaps <= 0) return;
    let previousTime = performance.now();

    function tick(now: number) {
      const elapsed = Math.min(now - previousTime, 250);
      previousTime = now;
      const next = raceProgressRef.current + (elapsed * speed) / REPLAY_TICK_MS;

      if (next >= totalLaps) {
        raceProgressRef.current = totalLaps;
        if (sliderRef.current) sliderRef.current.value = String(totalLaps);
        setCurrentLap(totalLaps);
        setScrubFraction(1);
        setPlaying(false);
        return;
      }

      raceProgressRef.current = next;
      if (sliderRef.current) sliderRef.current.value = String(next);
      const nextLap = Math.floor(next) + 1;
      setCurrentLap((lap) => (lap === nextLap ? lap : nextLap));
      animationRef.current = requestAnimationFrame(tick);
    }

    animationRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(animationRef.current);
  }, [playing, speed, totalLaps]);

  const togglePlaying = useCallback(() => {
    if (playing) {
      const progress = raceProgressRef.current;
      setScrubFraction(progress >= totalLaps ? 1 : progress - Math.floor(progress));
      setPlaying(false);
      return;
    }
    if (raceProgressRef.current >= totalLaps) setProgress(0, false);
    setPlaying(true);
  }, [playing, setProgress, totalLaps]);

  const previousPositions = useMemo(
    () => new Map(previousFrame?.order.map((entry) => [entry.driver, entry.position]) ?? []),
    [previousFrame],
  );

  if (isLoading) return <LoadingState label="Loading replay…" />;
  if (error) return <ErrorState message="Replay data isn't available for this race." />;
  if (!data || frames.length === 0) return <EmptyState message="No replay data available." />;

  return (
    <div>
      <div className="mb-4 rounded-lg border border-border bg-surface p-3">
        <div className="flex flex-wrap items-center gap-2 sm:gap-3">
          <button
            type="button"
            onClick={() => setProgress(0)}
            className="min-h-9 rounded-md border border-border px-2 text-xs font-semibold text-muted transition-colors hover:text-foreground"
            aria-label="Back to first lap"
          >
            |◀
          </button>
          <button
            type="button"
            onClick={() => setProgress(raceProgressRef.current - 5)}
            className="min-h-9 rounded-md border border-border px-2 text-xs font-semibold text-muted transition-colors hover:text-foreground"
            aria-label="Back five laps"
          >
            −5
          </button>
          <button
            type="button"
            onClick={togglePlaying}
            className="min-h-9 min-w-16 rounded-md bg-racing-red px-3 text-sm font-semibold text-white"
            aria-label={playing ? "Pause replay" : "Play replay"}
          >
            {playing ? "Pause" : "Play"}
          </button>
          <button
            type="button"
            onClick={() => setProgress(raceProgressRef.current + 5)}
            className="min-h-9 rounded-md border border-border px-2 text-xs font-semibold text-muted transition-colors hover:text-foreground"
            aria-label="Forward five laps"
          >
            +5
          </button>
          <button
            type="button"
            onClick={() => setProgress(Math.max(totalLaps - 1, 0))}
            className="min-h-9 rounded-md border border-border px-2 text-xs font-semibold text-muted transition-colors hover:text-foreground"
            aria-label="Skip to final lap"
          >
            ▶|
          </button>
          <span
            className="tabular ml-auto w-24 shrink-0 text-right text-sm text-muted"
            aria-live="polite"
          >
            Lap {currentLap} / {data.totalLaps}
          </span>
        </div>

        <input
          ref={sliderRef}
          type="range"
          min={0}
          max={Math.max(totalLaps, 1)}
          step={0.01}
          defaultValue={0}
          onChange={(event) => setProgress(Number(event.target.value))}
          aria-label={`Replay position, lap ${currentLap} of ${data.totalLaps}`}
          className="mt-3 w-full accent-[color:var(--racing-red)]"
        />

        <div className="mt-2 flex items-center justify-end gap-1" aria-label="Playback speed">
          <span className="mr-1 text-xs text-muted">Speed</span>
          {[0.5, 1, 2, 4].map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setSpeed(value)}
              aria-pressed={speed === value}
              className={clsx(
                "min-h-8 min-w-11 rounded-md border px-2 text-xs font-semibold transition-colors",
                speed === value
                  ? "border-racing-red bg-racing-red/15 text-foreground"
                  : "border-border text-muted hover:text-foreground",
              )}
            >
              {value}×
            </button>
          ))}
        </div>
      </div>

      {/* Desktop puts the map and the running order side by side so neither
          has to be hidden, with the map sticky so it stays in view while the
          order is scrolled. Below `lg` there isn't room for two columns, so
          the map stacks and can be collapsed away instead. */}
      <div className="lg:grid lg:grid-cols-2 lg:items-start lg:gap-6">
        <div className="lg:sticky lg:top-6">
          <div className="mb-2 flex justify-end lg:hidden">
            <button
              type="button"
              onClick={() => setMapCollapsed(!mapCollapsed)}
              aria-expanded={!mapCollapsed}
              className="rounded-md border border-border px-2.5 py-1 text-xs font-medium text-muted transition-colors hover:text-foreground"
            >
              {mapCollapsed ? "Show map" : "Hide map"}
            </button>
          </div>
          <div className={clsx("mb-4", mapCollapsed && "hidden lg:block")}>
            <ReplayTrackMap
              circuit={circuit}
              circuitLoading={circuitLoading}
              positionsLoading={positionsLoading}
              lapPositions={positionsByLap.get(currentLap)}
              driverTeamColors={driverTeamColors}
              order={effectiveOrder}
              lapFraction={scrubFraction}
              raceProgressRef={raceProgressRef}
              totalLaps={totalLaps}
              playing={playing}
              active={mapVisible}
            />
          </div>
        </div>

        <div>
          <div className="mb-3">
            <TyreLegend />
          </div>

          <ul className="flex flex-col gap-1.5">
            <AnimatePresence initial={false}>
              {effectiveOrder.map((entry) => (
                <motion.li
                  key={entry.driver}
                  layout
                  transition={{ type: "spring", stiffness: 400, damping: 35 }}
                  className="flex items-center gap-3 rounded-lg border border-border bg-surface px-3 py-2"
                >
                  <span className="tabular w-6 shrink-0 text-center font-semibold">{entry.position}</span>
                  <span
                    className={clsx(
                      "w-3 shrink-0 text-center text-xs",
                      previousPositions.has(entry.driver) &&
                        (previousPositions.get(entry.driver) ?? entry.position) > entry.position
                        ? "text-positive"
                        : previousPositions.has(entry.driver) &&
                            (previousPositions.get(entry.driver) ?? entry.position) < entry.position
                          ? "text-negative"
                          : "text-muted",
                    )}
                    aria-label={
                      previousPositions.has(entry.driver) &&
                      (previousPositions.get(entry.driver) ?? entry.position) > entry.position
                        ? "Gained a place"
                        : previousPositions.has(entry.driver) &&
                            (previousPositions.get(entry.driver) ?? entry.position) < entry.position
                          ? "Lost a place"
                          : "Position held"
                    }
                  >
                    {previousPositions.has(entry.driver) &&
                    (previousPositions.get(entry.driver) ?? entry.position) > entry.position
                      ? "▲"
                      : previousPositions.has(entry.driver) &&
                          (previousPositions.get(entry.driver) ?? entry.position) < entry.position
                        ? "▼"
                        : "−"}
                  </span>
                  <TeamColorDot color={entry.teamColor} />
                  <TeamLogo src={entry.teamLogoUrl} name={entry.teamName} sizeClassName="h-5 w-5" />
                  {entry.driverId ? (
                    <Link
                      href={`/drivers/${year}/${entry.driverId}`}
                      className="w-14 shrink-0 font-medium hover:text-racing-red"
                    >
                      {entry.driver}
                    </Link>
                  ) : (
                    <span className="w-14 shrink-0 font-medium">{entry.driver}</span>
                  )}
                  <span className="min-w-0 flex-1 truncate text-sm text-muted">{entry.teamName}</span>
                  {entry.compound && (
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: compoundColor(entry.compound) }}
                      title={entry.compound}
                    />
                  )}
                  <span
                    className={clsx(
                      "tabular w-20 shrink-0 text-right font-mono text-sm",
                      entry.position === 1 ? "text-racing-red-text" : "text-muted",
                    )}
                  >
                    {entry.gap ?? entry.lapTime ?? "-"}
                  </span>
                </motion.li>
              ))}
            </AnimatePresence>
          </ul>
        </div>
      </div>
    </div>
  );
}
