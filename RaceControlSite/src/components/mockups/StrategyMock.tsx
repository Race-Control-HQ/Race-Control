import { TYRE_COLORS } from "./tokens";

// Illustration of the Tyre Strategy screen (FEATURES.md §3.8): a per-driver
// stint timeline across race distance, coloured by compound, with pit counts.

const STINTS: { code: string; stints: { c: keyof typeof TYRE_COLORS; laps: number }[] }[] = [
  { code: "VER", stints: [{ c: "M", laps: 22 }, { c: "H", laps: 30 }] },
  { code: "NOR", stints: [{ c: "M", laps: 18 }, { c: "H", laps: 34 }] },
  { code: "LEC", stints: [{ c: "S", laps: 14 }, { c: "H", laps: 24 }, { c: "M", laps: 14 }] },
  { code: "RUS", stints: [{ c: "M", laps: 20 }, { c: "M", laps: 18 }, { c: "S", laps: 14 }] },
  { code: "PIA", stints: [{ c: "S", laps: 12 }, { c: "H", laps: 40 }] },
  { code: "HAM", stints: [{ c: "H", laps: 28 }, { c: "M", laps: 24 }] },
];

const TOTAL = 52;

export function StrategyMock() {
  return (
    <div className="bg-background p-3 text-foreground">
      <div className="mb-2 flex items-baseline justify-between px-1">
        <p className="text-sm font-semibold">Tyre strategy</p>
        <p className="text-[9px] text-muted">52 laps</p>
      </div>

      <div className="flex flex-col gap-2">
        {STINTS.map((driver) => (
          <div key={driver.code} className="flex items-center gap-2">
            <span className="w-8 text-[10px] font-semibold">{driver.code}</span>
            <div className="flex h-4 flex-1 overflow-hidden rounded-sm">
              {driver.stints.map((stint, i) => (
                <div
                  key={i}
                  className="flex items-center justify-center border-r border-background text-[7px] font-bold"
                  style={{
                    width: `${(stint.laps / TOTAL) * 100}%`,
                    background: TYRE_COLORS[stint.c],
                    color: "#0a0a0c",
                  }}
                >
                  {stint.c}
                </div>
              ))}
            </div>
            <span className="w-6 text-right text-[9px] text-muted">{driver.stints.length - 1} stop</span>
          </div>
        ))}
      </div>

      <div className="mt-3 flex items-center gap-2.5 px-1">
        {(["S", "M", "H", "I", "W"] as const).map((c) => (
          <div key={c} className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-full" style={{ background: TYRE_COLORS[c] }} />
            <span className="text-[8px] text-muted">
              {{ S: "Soft", M: "Medium", H: "Hard", I: "Inter", W: "Wet" }[c]}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
