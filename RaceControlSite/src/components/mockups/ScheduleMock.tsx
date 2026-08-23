// Illustration of the Races/Schedule screen (FEATURES.md §3.1): large season
// title, the "up next" banner for the first non-completed event, then race rows
// with round, flag, name, location, date, sprint badge and completed check.

const ROUNDS = [
  { r: 9, flag: "🇨🇦", name: "Canadian Grand Prix", loc: "Montréal", date: "15 Jun", done: true, sprint: false },
  { r: 10, flag: "🇪🇸", name: "Spanish Grand Prix", loc: "Barcelona", date: "29 Jun", done: true, sprint: false },
  { r: 11, flag: "🇦🇹", name: "Austrian Grand Prix", loc: "Spielberg", date: "06 Jul", done: true, sprint: true },
  { r: 12, flag: "🇬🇧", name: "British Grand Prix", loc: "Silverstone", date: "13 Jul", done: true, sprint: false },
  { r: 13, flag: "🇭🇺", name: "Hungarian Grand Prix", loc: "Budapest", date: "03 Aug", done: false, sprint: false },
];

export function ScheduleMock() {
  return (
    <div className="bg-background px-3 pb-3 pt-4 text-foreground">
      <div className="mb-3 flex items-center justify-between px-1">
        <p className="text-lg font-semibold tracking-tight">Season 2026</p>
        <span className="rounded-md border border-border px-2 py-0.5 text-[10px] text-muted">2026 ▾</span>
      </div>

      <div className="mb-3 rounded-xl border border-racing-red/40 bg-gradient-to-r from-racing-red/25 to-transparent p-3">
        <p className="text-[9px] uppercase tracking-wider text-racing-red-text">Up next</p>
        <p className="mt-0.5 text-sm font-semibold">🇭🇺 Hungarian Grand Prix</p>
        <p className="text-[10px] text-muted">Budapest · 3 August</p>
      </div>

      <div className="flex flex-col gap-1.5">
        {ROUNDS.map((round) => (
          <div
            key={round.r}
            className="flex items-center gap-2.5 rounded-lg border border-border bg-surface px-2.5 py-2"
          >
            <span className="w-4 text-center text-[10px] tabular text-muted">{round.r}</span>
            <span className="text-sm">{round.flag}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[11px] font-medium">{round.name}</p>
              <p className="text-[9px] text-muted">
                {round.loc} · {round.date}
              </p>
            </div>
            {round.sprint && (
              <span className="rounded border border-border px-1 py-px text-[8px] font-semibold text-muted">
                SPRINT
              </span>
            )}
            {round.done && <span className="text-[10px] text-positive">✓</span>}
          </div>
        ))}
      </div>
    </div>
  );
}
