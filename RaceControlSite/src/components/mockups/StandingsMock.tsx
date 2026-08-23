import { TEAM_COLORS } from "./tokens";

// Illustration of the Standings screen (FEATURES.md §3.6): the four-mode
// selector, then rank, driver, team, points, wins and a gap-to-leader bar.

const ROWS = [
  { pos: 1, name: "Verstappen", team: "redbull", pts: 287, wins: 7 },
  { pos: 2, name: "Norris", team: "mclaren", pts: 241, wins: 4 },
  { pos: 3, name: "Leclerc", team: "ferrari", pts: 213, wins: 2 },
  { pos: 4, name: "Piastri", team: "mclaren", pts: 190, wins: 2 },
  { pos: 5, name: "Russell", team: "mercedes", pts: 155, wins: 1 },
  { pos: 6, name: "Sainz", team: "ferrari", pts: 148, wins: 1 },
];

const LEADER = ROWS[0].pts;

export function StandingsMock() {
  return (
    <div className="bg-background p-3 text-foreground">
      <div className="mb-3 flex gap-1 rounded-lg bg-surface p-0.5">
        {["Drivers", "Teams", "Progress", "Reliability"].map((mode) => (
          <span
            key={mode}
            className={`flex-1 rounded-md py-1 text-center text-[9px] ${
              mode === "Drivers" ? "bg-surface-raised text-foreground" : "text-muted"
            }`}
          >
            {mode}
          </span>
        ))}
      </div>

      <div className="flex flex-col gap-1.5">
        {ROWS.map((row) => (
          <div key={row.name} className="rounded-lg bg-surface px-2.5 py-2">
            <div className="flex items-center gap-2">
              <span className="w-3 text-[10px] font-semibold tabular text-muted">{row.pos}</span>
              <span className="h-4 w-[3px] rounded-full" style={{ background: TEAM_COLORS[row.team] }} />
              <span className="flex-1 truncate text-[11px] font-medium">{row.name}</span>
              <span className="text-[9px] text-muted">{row.wins}W</span>
              <span className="w-8 text-right text-[11px] font-semibold tabular">{row.pts}</span>
            </div>
            <div className="mt-1.5 h-1 w-full rounded-full bg-surface-raised">
              <div
                className="h-1 rounded-full"
                style={{ width: `${(row.pts / LEADER) * 100}%`, background: TEAM_COLORS[row.team] }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
