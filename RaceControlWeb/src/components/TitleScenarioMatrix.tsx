"use client";

import { useTitleScenarios } from "@/lib/api";
import type { TitleScenarioCell } from "@/lib/types";

const legendColor: Record<string, string> = {
  D1_CLINCHED: "#30d158",
  D2_CLINCHED: "#ff453a",
  D1_LEADS: "rgba(48, 209, 88, 0.38)",
  D2_LEADS: "rgba(255, 69, 58, 0.38)",
  TIED: "#6b7280",
};

const outcomes = ["D1_CLINCHED", "D1_LEADS", "TIED", "D2_LEADS", "D2_CLINCHED"] as const;

function tileColor(cell: TitleScenarioCell, maxMargin: number) {
  if (cell.outcome === "TIED") return "#6b7280";
  const isD1 = cell.outcome.startsWith("D1");
  const clinched = cell.outcome.endsWith("CLINCHED");
  const alpha = clinched ? 0.92 : 0.2 + 0.42 * (Math.abs(cell.margin) / maxMargin);
  return isD1 ? `rgba(48, 209, 88, ${alpha})` : `rgba(255, 69, 58, ${alpha})`;
}

function marginLabel(margin: number) {
  if (margin === 0) return "TIE";
  const value = Number.isInteger(margin) ? margin.toFixed(0) : margin.toFixed(1);
  return margin > 0 ? `+${value}` : value;
}

export function TitleScenarioMatrix({
  year,
  throughRound,
}: {
  year: number;
  throughRound?: number | null;
}) {
  const { data } = useTitleScenarios(year, undefined, undefined, throughRound);
  if (!data?.available || data.drivers.length < 2) return null;
  const [d1, d2] = data.drivers;
  const label = (position: number) => (position === 0 ? "DNF" : `P${position}`);
  const maxMargin = Math.max(1, ...data.cells.map((cell) => Math.abs(cell.margin)));
  const presentOutcomes = new Set(data.cells.map((cell) => cell.outcome));
  const outcomeLabel: Record<string, string> = {
    D1_CLINCHED: `${d1.code} clinches`,
    D1_LEADS: `${d1.code} leads`,
    TIED: "Tied",
    D2_LEADS: `${d2.code} leads`,
    D2_CLINCHED: `${d2.code} clinches`,
  };

  return (
    <section className="mt-6">
      <h3 className="mb-1 font-semibold">Championship after the next race</h3>
      <p className="text-xs text-muted">
        {d1.code} {d1.points} pts · {d2.code} {d2.points} pts · {data.roundsRemaining} race{data.roundsRemaining === 1 ? "" : "s"} remaining
      </p>
      <p className="mb-3 text-xs text-muted">
        Choose a finish for {d1.code} down the rows and {d2.code} across the columns. Each tile shows the projected championship margin from {d1.code}&apos;s perspective: positive means {d1.code} is ahead, negative means {d2.code} is ahead.
      </p>
      {data.summary && <p className="mb-3 rounded-md border border-border bg-surface-raised p-3 text-sm">{data.summary} The tile numbers show how the margin changes.</p>}
      {data.clinchText && <p className="mb-3 rounded-md bg-positive/10 p-3 text-sm">{data.clinchText}</p>}
      <div className="mb-3 flex flex-wrap gap-x-4 gap-y-2 text-xs text-muted">
        {outcomes.filter((outcome) => presentOutcomes.has(outcome)).map((outcome) => (
          <span key={outcome} className="inline-flex items-center gap-1.5">
            <span className="h-3 w-3 rounded-sm" style={{ backgroundColor: legendColor[outcome] }} />
            {outcomeLabel[outcome]}
          </span>
        ))}
      </div>
      <div className="overflow-x-auto">
        <div className="grid min-w-[620px] gap-1" style={{ gridTemplateColumns: `54px repeat(${data.positions.length}, minmax(44px, 1fr))` }}>
          <span className="self-end text-center text-[9px] font-semibold text-muted">{d1.code} ↓<br />{d2.code} →</span>
          {data.positions.map((position) => <span key={position} className="text-center text-[10px] text-muted">{label(position)}</span>)}
          {data.positions.map((row) => (
            <div key={row} className="contents">
              <span className="self-center text-xs text-muted">{label(row)}</span>
              {data.positions.map((column) => {
                const cell = data.cells.find((item) => item.d1Position === row && item.d2Position === column);
                return (
                  <div
                    key={`${row}-${column}`}
                    className="flex aspect-square items-center justify-center rounded-sm border border-white/10 text-[10px] font-semibold tabular text-white shadow-sm"
                    style={{ backgroundColor: cell ? tileColor(cell, maxMargin) : legendColor.TIED }}
                    title={cell ? `${d1.code} ${label(row)}, ${d2.code} ${label(column)}: ${outcomeLabel[cell.outcome]}, projected margin ${marginLabel(cell.margin)}` : ""}
                  >
                    {cell ? marginLabel(cell.margin) : "—"}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
