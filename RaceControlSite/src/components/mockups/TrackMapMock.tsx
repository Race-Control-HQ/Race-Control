// Illustration of the Circuit detail track map (FEATURES.md §3.7): the outline
// with numbered corner markers, the trace coloured by speed, and DRS zones
// highlighted. The real component renders the outline from backend-supplied
// points and rotates it by a backend-supplied `rotation`.

const TRACK =
  "M 60 150 C 60 90, 90 60, 140 60 L 210 60 C 250 60, 262 84, 250 106 L 214 150 C 205 164, 212 178, 228 178 L 268 178 C 290 178, 300 194, 288 210 L 250 244 C 236 256, 214 256, 200 244 L 156 206 C 142 194, 122 196, 112 210 L 92 236 C 78 254, 56 250, 52 228 Z";

const CORNERS = [
  { n: 1, x: 140, y: 55 },
  { n: 3, x: 256, y: 100 },
  { n: 5, x: 226, y: 186 },
  { n: 7, x: 292, y: 214 },
  { n: 9, x: 224, y: 256 },
  { n: 12, x: 104, y: 214 },
  { n: 14, x: 48, y: 236 },
];

export function TrackMapMock() {
  return (
    <div className="bg-background p-3 text-foreground">
      <div className="mb-2 flex items-baseline justify-between px-1">
        <p className="text-sm font-semibold">Silverstone Circuit</p>
        <p className="text-[10px] text-muted">Round 12</p>
      </div>

      <svg viewBox="0 0 340 300" className="w-full" role="img" aria-label="Track outline with numbered corners">
        <defs>
          <linearGradient id="speedTrace" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#30d158" />
            <stop offset="45%" stopColor="#ffd12e" />
            <stop offset="100%" stopColor="#e10600" />
          </linearGradient>
        </defs>

        <path d={TRACK} fill="none" stroke="#2a2a30" strokeWidth="14" strokeLinejoin="round" />
        <path d={TRACK} fill="none" stroke="url(#speedTrace)" strokeWidth="6" strokeLinejoin="round" />

        {/* DRS zone highlight — the real map marks these from the circuit payload. */}
        <path
          d="M 140 60 L 210 60"
          stroke="#3671C6"
          strokeWidth="10"
          strokeLinecap="round"
          opacity="0.85"
        />
        <text x="175" y="46" textAnchor="middle" fontSize="9" fill="#64C4FF" fontWeight="600">
          DRS
        </text>

        {CORNERS.map((corner) => (
          <g key={corner.n}>
            <circle cx={corner.x} cy={corner.y} r="8" fill="#16161a" stroke="#2a2a30" />
            <text
              x={corner.x}
              y={corner.y + 3}
              textAnchor="middle"
              fontSize="8"
              fill="#9a9aa2"
              fontWeight="600"
            >
              {corner.n}
            </text>
          </g>
        ))}
      </svg>

      <div className="mt-1 grid grid-cols-3 gap-2 px-1">
        {[
          ["Length", "5.891 km"],
          ["Corners", "18"],
          ["Laps", "52"],
        ].map(([label, value]) => (
          <div key={label} className="rounded-lg bg-surface p-2">
            <p className="text-[9px] uppercase tracking-wider text-muted">{label}</p>
            <p className="text-[11px] font-semibold tabular">{value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
