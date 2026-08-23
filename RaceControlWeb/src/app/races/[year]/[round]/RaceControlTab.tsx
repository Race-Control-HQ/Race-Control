"use client";

import { useMemo, useState } from "react";
import clsx from "clsx";
import { useRaceControl } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { flagColor, flagLabel } from "@/lib/flags";
import { formatClock } from "@/lib/format";
import type { RaceControlCategory, RaceControlMessage } from "@/lib/types";

const CATEGORY_COLORS: Record<RaceControlCategory, string> = {
  Flag: "#f0d000",
  SafetyCar: "#ff8c1a",
  Drs: "#22c55e",
  CarEvent: "#3b82f6",
  Other: "#9ca3af",
};

const CATEGORY_LABELS: Record<RaceControlCategory, string> = {
  Flag: "Flag",
  SafetyCar: "Safety Car",
  Drs: "DRS",
  CarEvent: "Car Event",
  Other: "Other",
};

function messageColor(m: RaceControlMessage): string {
  if (m.category === "Flag" && m.flag) return flagColor(m.flag);
  return CATEGORY_COLORS[m.category] ?? "#6b6b72";
}

function messageBadgeLabel(m: RaceControlMessage): string {
  if (m.category === "Flag" && m.flag) return flagLabel(m.flag);
  return CATEGORY_LABELS[m.category] ?? m.category;
}

type FilterKey = "all" | "Flag" | "SafetyCar" | "Drs" | "Incidents";

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: "all", label: "All" },
  { key: "Flag", label: "Flags" },
  { key: "SafetyCar", label: "Safety Car" },
  { key: "Drs", label: "DRS" },
  { key: "Incidents", label: "Incidents" },
];

function matchesFilter(m: RaceControlMessage, filter: FilterKey): boolean {
  if (filter === "all") return true;
  if (filter === "Incidents") return m.category === "CarEvent" || m.category === "Other";
  return m.category === filter;
}

export function RaceControlTab({ year, round }: { year: number; round: number }) {
  const { data, error, isLoading } = useRaceControl(year, round);
  const [filter, setFilter] = useState<FilterKey>("all");

  const filtered = useMemo(() => {
    if (!data) return [];
    return data.messages.filter((m) => matchesFilter(m, filter));
  }, [data, filter]);

  if (isLoading) return <LoadingState label="Loading race control log…" />;
  if (error) return <ErrorState message="Race control data isn't available for this race." />;
  if (!data) return null;
  if (data.messages.length === 0) return <EmptyState message="No race control messages for this session." />;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-1 overflow-x-auto rounded-lg bg-surface p-1">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setFilter(f.key)}
            className={clsx(
              "whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
              filter === f.key ? "bg-racing-red text-white" : "text-muted hover:text-foreground",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <EmptyState message="No messages match this filter." />
      ) : (
        <ul className="flex flex-col gap-2">
          {filtered.map((m, i) => (
            <li key={i} className="flex items-start gap-3 rounded-lg border border-border bg-surface px-4 py-3">
              <span
                className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: messageColor(m) }}
                aria-hidden
              />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className="tabular shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold"
                    style={{ backgroundColor: `${messageColor(m)}26`, color: messageColor(m) }}
                  >
                    {messageBadgeLabel(m)}
                  </span>
                  {m.driverCode && (
                    <span className="shrink-0 rounded-full bg-border px-2 py-0.5 text-xs font-semibold text-foreground">
                      {m.driverCode}
                    </span>
                  )}
                  <span className="tabular shrink-0 text-xs text-muted">{formatClock(m.time)}</span>
                  <span className="shrink-0 text-xs text-muted">{m.lap != null ? `Lap ${m.lap}` : "-"}</span>
                </div>
                <p className="mt-1 break-words text-sm text-foreground">{m.message ?? "-"}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
