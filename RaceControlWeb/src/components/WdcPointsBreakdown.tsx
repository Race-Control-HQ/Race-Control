// Keep the maximums aligned with the backend's `_max_points_remaining`:
// the fastest-lap bonus applies only to seasons from 2019 through 2024.
const RACE_POINTS = [25, 18, 15, 12, 10, 8, 6, 4, 2, 1];
const SPRINT_POINTS = [8, 7, 6, 5, 4, 3, 2, 1];

export function WdcPointsBreakdown({ year }: { year: number }) {
  const fastestLapBonus = year >= 2019 && year <= 2024 ? 1 : 0;
  return (
    <details className="mt-4 rounded-lg border border-border bg-surface-raised/40 p-3 text-xs text-muted">
      <summary className="cursor-pointer select-none font-medium text-foreground">
        Points breakdown: where &quot;max points&quot; comes from
      </summary>
      <div className="mt-3 flex flex-col gap-3">
        <div>
          <p className="mb-1 font-medium text-foreground">
            Race (top 10 score{fastestLapBonus ? ", plus +1 for fastest lap in the top 10" : ""})
          </p>
          <div className="flex flex-wrap gap-x-3 gap-y-1 tabular">
            {RACE_POINTS.map((pts, i) => (
              <span key={i}>
                P{i + 1} <span className="text-foreground">{pts}</span>
              </span>
            ))}
          </div>
        </div>
        <div>
          <p className="mb-1 font-medium text-foreground">Sprint (top 8 score, on sprint weekends only)</p>
          <div className="flex flex-wrap gap-x-3 gap-y-1 tabular">
            {SPRINT_POINTS.map((pts, i) => (
              <span key={i}>
                P{i + 1} <span className="text-foreground">{pts}</span>
              </span>
            ))}
          </div>
        </div>
        <p className="tabular">
          Max for one driver at a single event: race win {RACE_POINTS[0]}
          {fastestLapBonus ? ` + fastest lap ${fastestLapBonus}` : ""} ={" "}
          <span className="font-semibold text-foreground">{RACE_POINTS[0] + fastestLapBonus} pts</span> on a normal
          weekend, or{" "}
          <span className="font-semibold text-foreground">
            {RACE_POINTS[0] + fastestLapBonus + SPRINT_POINTS[0]} pts
          </span>{" "}
          on a sprint weekend (+ sprint win {SPRINT_POINTS[0]}). &quot;Max remaining points&quot; is this figure
          multiplied out across every round still left on the calendar.
        </p>
        {year >= 2025 && <p>The fastest-lap bonus was removed from 2025 onward.</p>}
      </div>
    </details>
  );
}
