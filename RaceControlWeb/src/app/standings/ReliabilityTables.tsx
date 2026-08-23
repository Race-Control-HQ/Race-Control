"use client";

import { Fragment, useMemo, useState } from "react";
import { useReliability } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import type { ReliabilityEntry } from "@/lib/types";

type SortKey = "starts" | "finished" | "mechanical" | "accident" | "dnf" | "finishRate";

const COLUMNS: { key: SortKey; label: string }[] = [
  { key: "starts", label: "Starts" },
  { key: "finished", label: "Finished" },
  { key: "mechanical", label: "Mech." },
  { key: "accident", label: "Accident" },
  { key: "dnf", label: "DNF" },
  { key: "finishRate", label: "Finish %" },
];

function ReliabilityTable({
  title,
  rows,
  nameKey,
  showLogo = false,
  sortKey,
  sortDir,
  onSort,
  expandedId,
  onToggleExpand,
}: {
  title: string;
  rows: ReliabilityEntry[];
  nameKey: "name" | "teamName";
  showLogo?: boolean;
  sortKey: SortKey;
  sortDir: 1 | -1;
  onSort: (key: SortKey) => void;
  expandedId: string | null;
  onToggleExpand: (id: string) => void;
}) {
  const sorted = useMemo(() => [...rows].sort((a, b) => (a[sortKey] - b[sortKey]) * sortDir), [rows, sortKey, sortDir]);

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <div className="bg-surface px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted">{title}</div>
      <table className="w-full text-sm">
        <thead className="bg-surface/60 text-left text-xs uppercase tracking-wide text-muted">
          <tr>
            <th className="px-3 py-2 font-medium">Name</th>
            {COLUMNS.map((col) => (
              <th key={col.key} className="tabular px-3 py-2 text-right font-medium">
                <button
                  type="button"
                  onClick={() => onSort(col.key)}
                  className="inline-flex items-center gap-1 hover:text-foreground"
                >
                  {col.label}
                  {sortKey === col.key && <span>{sortDir === 1 ? "↑" : "↓"}</span>}
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {sorted.map((r) => {
            const id = r.driverId ?? r.teamId ?? String(r[nameKey]);
            const expanded = expandedId === id;
            return (
              <Fragment key={id}>
                <tr
                  className="cursor-pointer hover:bg-surface/60"
                  onClick={() => onToggleExpand(id)}
                  aria-expanded={expanded}
                >
                  <td className="px-3 py-2 font-medium">
                    {showLogo ? (
                      <span className="inline-flex items-center gap-2">
                        <TeamLogo src={r.teamLogoUrl} name={r.teamName} sizeClassName="h-5 w-5" />
                        {r[nameKey]}
                      </span>
                    ) : (
                      r[nameKey]
                    )}
                  </td>
                  <td className="tabular px-3 py-2 text-right">{r.starts}</td>
                  <td className="tabular px-3 py-2 text-right">{r.finished}</td>
                  <td className="tabular px-3 py-2 text-right">{r.mechanical}</td>
                  <td className="tabular px-3 py-2 text-right">{r.accident}</td>
                  <td className="tabular px-3 py-2 text-right">{r.dnf}</td>
                  <td className="tabular px-3 py-2 text-right">{r.finishRate}%</td>
                </tr>
                {expanded && (
                  <tr className="bg-surface/40">
                    <td colSpan={COLUMNS.length + 1} className="px-3 py-2 text-xs text-muted">
                      <span className="mr-4">Disqualified: {r.disqualified}</span>
                      <span>Other: {r.other}</span>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function ReliabilityTables({ year }: { year: number }) {
  const { data, error, isLoading } = useReliability(year);
  const [sortKey, setSortKey] = useState<SortKey>("finishRate");
  const [sortDir, setSortDir] = useState<1 | -1>(-1);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const onSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((d) => (d === 1 ? -1 : 1));
    } else {
      setSortKey(key);
      setSortDir(-1);
    }
  };
  const onToggleExpand = (id: string) => setExpandedId((current) => (current === id ? null : id));

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load reliability data." />;
  if (!data || (data.drivers.length === 0 && data.teams.length === 0))
    return <EmptyState message="No reliability data available for this season." />;

  return (
    <div className="flex flex-col gap-6">
      <p className="text-xs text-muted">Click a column to sort both tables · click a row for cause detail</p>
      <ReliabilityTable
        title="Drivers"
        rows={data.drivers}
        nameKey="name"
        sortKey={sortKey}
        sortDir={sortDir}
        onSort={onSort}
        expandedId={expandedId}
        onToggleExpand={onToggleExpand}
      />
      <ReliabilityTable
        title="Teams"
        rows={data.teams}
        nameKey="teamName"
        showLogo
        sortKey={sortKey}
        sortDir={sortDir}
        onSort={onSort}
        expandedId={expandedId}
        onToggleExpand={onToggleExpand}
      />
    </div>
  );
}
