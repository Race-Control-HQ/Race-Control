import { TEAM_COLORS } from "./tokens";

// Illustration of the Telemetry screen (FEATURES.md §3.8): speed and throttle
// traces over lap distance with two drivers overlaid, plus the playhead the
// real screen sweeps along with the mini track map.

// Two plausible speed traces over one lap: straights near the top, heavy
// braking zones dropping into corners.
const SPEED_A =
  "M0,52 L18,14 L34,12 L46,58 L58,66 L74,30 L88,24 L100,70 L112,74 L128,34 L142,20 L156,16 L168,62 L182,70 L196,38 L214,18 L232,14 L248,60 L262,68 L276,32 L292,18 L300,16";
const SPEED_B =
  "M0,56 L18,18 L34,15 L46,62 L58,72 L74,36 L88,28 L100,74 L112,80 L128,40 L142,25 L156,20 L168,66 L182,76 L196,44 L214,24 L232,18 L248,64 L262,74 L276,38 L292,22 L300,20";

const THROTTLE_A =
  "M0,20 L18,6 L34,6 L46,34 L58,36 L74,10 L88,8 L100,36 L112,38 L128,12 L142,6 L156,6 L168,34 L182,36 L196,14 L214,6 L232,6 L248,34 L262,36 L276,10 L292,6 L300,6";
const THROTTLE_B =
  "M0,22 L18,8 L34,8 L46,36 L58,38 L74,14 L88,10 L100,38 L112,38 L128,16 L142,8 L156,8 L168,36 L182,38 L196,18 L214,8 L232,8 L248,36 L262,38 L276,14 L292,8 L300,8";

export function TelemetryMock() {
  return (
    <div className="bg-background p-3 text-foreground">
      <div className="mb-2 flex items-center gap-3 px-1">
        <div className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full" style={{ background: TEAM_COLORS.redbull }} />
          <span className="text-[10px] font-medium">VER</span>
          <span className="text-[10px] tabular text-muted">1:27.097</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="h-2 w-2 rounded-full" style={{ background: TEAM_COLORS.mclaren }} />
          <span className="text-[10px] font-medium">NOR</span>
          <span className="text-[10px] tabular text-muted">+0.214</span>
        </div>
        <span className="ml-auto rounded border border-border px-1.5 py-px text-[9px] text-muted">Lap 41</span>
      </div>

      <p className="mb-1 px-1 text-[9px] uppercase tracking-wider text-muted">Speed (km/h)</p>
      <svg viewBox="0 0 300 88" className="w-full" role="img" aria-label="Speed traces for two drivers over one lap">
        {[0, 22, 44, 66, 88].map((y) => (
          <line key={y} x1="0" y1={y} x2="300" y2={y} stroke="#2a2a30" strokeWidth="0.5" />
        ))}
        <path d={SPEED_A} fill="none" stroke={TEAM_COLORS.redbull} strokeWidth="1.6" />
        <path d={SPEED_B} fill="none" stroke={TEAM_COLORS.mclaren} strokeWidth="1.6" />
        <line x1="196" y1="0" x2="196" y2="88" stroke="#f2f2f4" strokeWidth="0.75" opacity="0.6" />
      </svg>

      <p className="mb-1 mt-2 px-1 text-[9px] uppercase tracking-wider text-muted">Throttle (%)</p>
      <svg viewBox="0 0 300 44" className="w-full" role="img" aria-label="Throttle traces for two drivers over one lap">
        {[0, 22, 44].map((y) => (
          <line key={y} x1="0" y1={y} x2="300" y2={y} stroke="#2a2a30" strokeWidth="0.5" />
        ))}
        <path d={THROTTLE_A} fill="none" stroke={TEAM_COLORS.redbull} strokeWidth="1.6" />
        <path d={THROTTLE_B} fill="none" stroke={TEAM_COLORS.mclaren} strokeWidth="1.6" />
        <line x1="196" y1="0" x2="196" y2="44" stroke="#f2f2f4" strokeWidth="0.75" opacity="0.6" />
      </svg>

      <div className="mt-2 flex gap-1.5 px-1">
        {["VER", "NOR", "LEC", "RUS"].map((code) => (
          <span
            key={code}
            className={`rounded-full px-2 py-0.5 text-[9px] ${
              code === "VER" || code === "NOR"
                ? "bg-surface-raised text-foreground"
                : "border border-border text-muted"
            }`}
          >
            {code}
          </span>
        ))}
      </div>
    </div>
  );
}
