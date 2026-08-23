"use client";

import { useSeasons } from "@/lib/api";

export function SeasonPicker({ year, onChange }: { year: number; onChange: (year: number) => void }) {
  const { data: seasons } = useSeasons();

  return (
    <select
      value={year}
      onChange={(e) => onChange(parseInt(e.target.value, 10))}
      className="rounded-md border border-border bg-surface px-3 py-1.5 text-sm font-medium text-foreground tabular focus:outline-none focus:ring-2 focus:ring-racing-red"
      aria-label="Season"
    >
      {(seasons ?? [year]).map((y) => (
        <option key={y} value={y}>
          {y}
        </option>
      ))}
    </select>
  );
}
