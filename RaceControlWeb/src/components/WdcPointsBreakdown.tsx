// Points on offer per finishing position: the same scale the backend's
// `_CONVENTIONAL_POINTS_TOTAL` / `_SPRINT_POINTS_TOTAL` (25+1 / 8+25+1) are
// built from, shown here so the "max points" and "points still on offer"
// figures elsewhere aren't just asserted, they're traceable.
const RACE_POINTS = [25, 18, 15, 12, 10, 8, 6, 4, 2, 1];
const SPRINT_POINTS = [8, 7, 6, 5, 4, 3, 2, 1];
const FASTEST_LAP_BONUS = 1; // only awarded to a top-10 finisher

export function WdcPointsBreakdown() {
  return (
    <details className="mt-4 rounded-lg border border-border bg-surface-raised/40 p-3 text-xs text-muted">
      <summary className="cursor-pointer select-none font-medium text-foreground">
        Points breakdown: where &quot;max points&quot; comes from
      </summary>
      <div className="mt-3 flex flex-col gap-3">
        <div>
          <p className="mb-1 font-medium text-foreground">Race (top 10 score, plus +1 for fastest lap in the top 10)</p>
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
          Max for one driver at a single event: race win {RACE_POINTS[0]} + fastest lap {FASTEST_LAP_BONUS} ={" "}
          <span className="font-semibold text-foreground">{RACE_POINTS[0] + FASTEST_LAP_BONUS} pts</span> on a normal
          weekend, or{" "}
          <span className="font-semibold text-foreground">
            {RACE_POINTS[0] + FASTEST_LAP_BONUS + SPRINT_POINTS[0]} pts
          </span>{" "}
          on a sprint weekend (+ sprint win {SPRINT_POINTS[0]}). &quot;Max remaining points&quot; is this figure
          multiplied out across every round still left on the calendar.
        </p>
      </div>
    </details>
  );
}
