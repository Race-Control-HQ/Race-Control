import { FLAG_COLORS } from "./tokens";

// Illustration of the Flags screen (FEATURES.md §3.10): collapsed flag and
// safety-car periods with icon, colour, type label, inclusive lap range and
// reason, over a collapsed raw race-control timeline disclosure.

const PERIODS = [
  { type: "SC", label: "Safety Car", laps: "Lap 18 – 22", reason: "CAR 18 STOPPED ON TRACK AT TURN 9" },
  { type: "YELLOW", label: "Yellow Flag", laps: "Lap 18", reason: "DEBRIS ON TRACK SECTOR 2" },
  { type: "VSC", label: "Virtual Safety Car", laps: "Lap 34 – 36", reason: "RECOVERY OF CAR 31" },
  { type: "DOUBLE_YELLOW", label: "Double Yellow", laps: "Lap 34", reason: "MARSHALS ON TRACK SECTOR 3" },
  { type: "YELLOW", label: "Yellow Flag", laps: "Lap 49", reason: "CAR 55 OFF AT TURN 4" },
] as const;

export function FlagsMock() {
  return (
    <div className="bg-background p-3 text-foreground">
      <p className="mb-2 px-1 text-sm font-semibold">Flags & safety car</p>

      <div className="flex flex-col gap-1.5">
        {PERIODS.map((period, i) => (
          <div key={i} className="flex items-start gap-2.5 rounded-lg bg-surface px-2.5 py-2">
            <span
              className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-sm text-[8px]"
              style={{ background: FLAG_COLORS[period.type], color: "#0a0a0c" }}
            >
              {period.type === "SC" || period.type === "VSC" ? "▲" : "⚑"}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline justify-between gap-2">
                <p className="text-[11px] font-semibold" style={{ color: FLAG_COLORS[period.type] }}>
                  {period.label}
                </p>
                <p className="shrink-0 text-[9px] tabular text-muted">{period.laps}</p>
              </div>
              <p className="mt-0.5 truncate text-[9px] leading-relaxed text-muted">{period.reason}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-2 flex items-center justify-between rounded-lg border border-border px-2.5 py-2">
        <p className="text-[9px] uppercase tracking-wider text-muted">Race control timeline (47)</p>
        <span className="text-[10px] text-muted">⌄</span>
      </div>
    </div>
  );
}
