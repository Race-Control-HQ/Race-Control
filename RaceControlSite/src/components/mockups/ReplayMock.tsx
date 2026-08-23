import { TEAM_COLORS, TYRE_COLORS } from "./tokens";

// Illustration of the Race Replay screen (FEATURES.md §3.3): lap counter,
// running order with movement triangles, tyre badges and lap times, then the
// transport row and the speed control.

const ORDER = [
  { pos: 1, code: "VER", team: "redbull", move: 0, tyre: "M", time: "1:32.104" },
  { pos: 2, code: "NOR", team: "mclaren", move: 1, tyre: "M", time: "1:32.418" },
  { pos: 3, code: "LEC", team: "ferrari", move: -1, tyre: "H", time: "1:32.655" },
  { pos: 4, code: "RUS", team: "mercedes", move: 0, tyre: "M", time: "1:32.881" },
  { pos: 5, code: "PIA", team: "mclaren", move: 2, tyre: "S", time: "1:33.002" },
  { pos: 6, code: "SAI", team: "ferrari", move: -1, tyre: "H", time: "1:33.240" },
  { pos: 7, code: "HAM", team: "mercedes", move: -1, tyre: "H", time: "1:33.512" },
];

function Move({ move }: { move: number }) {
  if (move > 0) return <span className="text-[9px] leading-none text-positive">▲</span>;
  if (move < 0) return <span className="text-[9px] leading-none text-negative">▼</span>;
  return <span className="text-[9px] leading-none text-muted">–</span>;
}

export function ReplayMock() {
  return (
    <div className="bg-background px-3 pb-3 pt-4 text-foreground">
      <div className="mb-3 flex items-baseline justify-between px-1">
        <div>
          <p className="text-[10px] uppercase tracking-wider text-muted">Lap</p>
          <p className="text-xl font-semibold leading-tight tabular">32 / 57</p>
        </div>
        <p className="text-[10px] text-muted">Silverstone</p>
      </div>

      <div className="flex flex-col gap-1">
        {ORDER.map((row) => (
          <div key={row.code} className="flex items-center gap-2 rounded-lg bg-surface px-2 py-1.5">
            <span className="w-4 text-center text-[11px] font-semibold tabular">{row.pos}</span>
            <Move move={row.move} />
            <span className="h-5 w-[3px] rounded-full" style={{ background: TEAM_COLORS[row.team] }} />
            <span className="text-[11px] font-semibold">{row.code}</span>
            <span
              className="ml-auto flex h-4 w-4 items-center justify-center rounded-full text-[8px] font-bold text-background"
              style={{ background: TYRE_COLORS[row.tyre] }}
            >
              {row.tyre}
            </span>
            <span className="w-14 text-right text-[10px] tabular text-muted">{row.time}</span>
          </div>
        ))}
      </div>

      <div className="mt-3 px-1">
        <div className="h-1 w-full rounded-full bg-surface-raised">
          <div className="h-1 w-[56%] rounded-full bg-racing-red" />
        </div>
        <div className="mt-2.5 flex items-center justify-center gap-4 text-muted">
          <span className="text-[11px]">⏮</span>
          <span className="text-[11px]">−5</span>
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-racing-red text-[10px] text-white">
            ❚❚
          </span>
          <span className="text-[11px]">+5</span>
          <span className="text-[11px]">⏭</span>
        </div>
        <div className="mt-2.5 flex gap-1">
          {["0.5×", "1×", "2×", "4×"].map((s) => (
            <span
              key={s}
              className={`flex-1 rounded-md py-1 text-center text-[10px] ${
                s === "2×" ? "bg-surface-raised text-foreground" : "text-muted"
              }`}
            >
              {s}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
