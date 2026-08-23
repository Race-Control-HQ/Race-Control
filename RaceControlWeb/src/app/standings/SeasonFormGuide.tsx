"use client";

import { useMemo, useState } from "react";
import { useSeasonFormGuide, SeasonFormDriverRow } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import type { ResultRow, ScheduleEvent } from "@/lib/types";

function isFinishStatus(status: string): boolean {
  const s = status.toLowerCase();
  return s === "" || s === "finished" || s.startsWith("+");
}

function shortStatusCode(status: string): string {
  const s = status.toLowerCase();
  if (s.includes("disqualified")) return "DSQ";
  if (s.includes("did not start")) return "DNS";
  if (s.includes("did not qualify")) return "DNQ";
  return "DNF";
}

function positionLabel(entry: ResultRow): string {
  if (entry.classifiedPosition && Number.isNaN(Number(entry.classifiedPosition))) {
    return entry.classifiedPosition;
  }
  if (entry.position != null) return String(Math.round(entry.position));
  return "–";
}

type Tier = "podium" | "points" | "outsidePoints" | "dnf" | "unknown";

function tierOf(entry: ResultRow | undefined): Tier {
  if (!entry) return "unknown";
  if (entry.status && !isFinishStatus(entry.status)) return "dnf";
  const position = entry.position ?? (entry.classifiedPosition ? Number(entry.classifiedPosition) : null);
  if (position == null || Number.isNaN(position)) return "unknown";
  if (position <= 3) return "podium";
  if (position <= 10) return "points";
  return "outsidePoints";
}

const TIER_CLASSES: Record<Tier, string> = {
  podium: "bg-[#ffd700] text-black",
  points: "bg-[color-mix(in_srgb,var(--positive)_55%,transparent)] text-white",
  outsidePoints: "bg-surface-raised text-foreground",
  dnf: "bg-[color-mix(in_srgb,var(--negative)_65%,transparent)] text-white",
  unknown: "bg-surface text-muted",
};

function cellLabel(entry: ResultRow | undefined): string {
  if (!entry) return "–";
  if (entry.status && !isFinishStatus(entry.status)) return shortStatusCode(entry.status);
  return positionLabel(entry);
}

/**
 * Short column labels for each round, de-duplicated across the whole season:
 * a plain 3-letter prefix collides often (Montréal/Monte Carlo, United
 * States/United Kingdom, Australia/Austria all share a prefix), so widen the
 * colliding label until it's unique, falling back to the round number if it
 * still can't be disambiguated.
 */
function roundShortLabels(events: ScheduleEvent[]): Map<number, string> {
  const used = new Set<string>();
  const labels = new Map<number, string>();
  for (const event of events) {
    const source = (event.location || event.country || event.name || `R${event.round}`).replace(
      /[^a-zA-Z]/g,
      "",
    );
    let len = 3;
    let label = source.slice(0, len).toUpperCase() || `R${event.round}`;
    while (used.has(label) && len < source.length) {
      len += 1;
      label = source.slice(0, len).toUpperCase();
    }
    if (used.has(label)) label = `R${event.round}`;
    used.add(label);
    labels.set(event.round, label);
  }
  return labels;
}

export function SeasonFormGuide({ year }: { year: number }) {
  const { data, error, isLoading } = useSeasonFormGuide(year);
  const [selected, setSelected] = useState<{ driver: string; round: string; result: string } | null>(null);
  const roundLabels = useMemo(() => roundShortLabels(data?.rounds ?? []), [data?.rounds]);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load the season form guide." />;
  if (!data || data.rounds.length === 0 || data.drivers.length === 0) {
    return <EmptyState message={`No completed races for ${year} yet.`} />;
  }

  const select = (driver: SeasonFormDriverRow, round: ScheduleEvent, entry: ResultRow | undefined) => {
    const resultLabel = !entry
      ? "No result"
      : entry.status && !isFinishStatus(entry.status)
        ? entry.status
        : `P${positionLabel(entry)}`;
    setSelected({ driver: driver.name, round: round.name || `Round ${round.round}`, result: resultLabel });
  };

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-3 text-xs text-muted">Finishing position by round · click a cell for detail</p>

      <div className="flex overflow-x-auto">
        <div className="flex flex-none flex-col pr-2">
          <div className="h-8" />
          {data.drivers.map((driver) => (
            <div key={driver.id} className="flex h-8 items-center text-xs font-bold text-foreground">
              {driver.code}
            </div>
          ))}
        </div>

        <div className="flex flex-col">
          <div className="flex">
            {data.rounds.map((round) => (
              <div
                key={round.round}
                className="flex h-8 w-9 flex-none items-center justify-center text-[10px] font-bold text-muted"
              >
                {roundLabels.get(round.round)}
              </div>
            ))}
          </div>
          {data.drivers.map((driver) => (
            <div key={driver.id} className="flex">
              {data.rounds.map((round) => {
                const entry = driver.cells[round.round];
                const tier = tierOf(entry);
                return (
                  <button
                    key={round.round}
                    type="button"
                    onClick={() => select(driver, round, entry)}
                    aria-label={`${driver.name}, ${round.name}, ${entry ? cellLabel(entry) : "no result"}`}
                    className={`m-[1px] flex h-[30px] w-8 flex-none items-center justify-center rounded text-[11px] font-bold tabular ${TIER_CLASSES[tier]}`}
                  >
                    {cellLabel(entry)}
                  </button>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      {selected && (
        <div className="mt-3 rounded-md bg-surface-raised px-3 py-2 text-sm font-semibold text-foreground">
          {selected.driver} · {selected.round} · {selected.result}
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-4 text-xs text-muted">
        <Legend swatch="bg-[#ffd700]" label="P1–3" />
        <Legend swatch="bg-[color-mix(in_srgb,var(--positive)_55%,transparent)]" label="P4–10" />
        <Legend swatch="bg-surface-raised" label="P11+" />
        <Legend swatch="bg-[color-mix(in_srgb,var(--negative)_65%,transparent)]" label="DNF/DSQ" />
      </div>
    </div>
  );
}

function Legend({ swatch, label }: { swatch: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={`h-2.5 w-2.5 rounded-sm ${swatch}`} />
      {label}
    </span>
  );
}
