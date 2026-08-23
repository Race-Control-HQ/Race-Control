import { compoundColor } from "@/lib/tyres";

const COMPOUNDS: { code: string; label: string }[] = [
  { code: "SOFT", label: "Soft" },
  { code: "MEDIUM", label: "Medium" },
  { code: "HARD", label: "Hard" },
  { code: "INTERMEDIATE", label: "Intermediate" },
  { code: "WET", label: "Wet" },
];

/**
 * Tyre-compound key. Renders as a labelled pill on larger screens and
 * collapses to just the colour dot (with a hover title as a fallback) on
 * small screens, where a full row of pills would wrap awkwardly.
 */
export function TyreLegend() {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {COMPOUNDS.map(({ code, label }) => (
        <span
          key={code}
          title={label}
          className="flex items-center gap-1.5 sm:rounded-full sm:border sm:border-border sm:bg-surface sm:px-2.5 sm:py-1"
        >
          <span
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: compoundColor(code) }}
          />
          <span className="hidden text-xs font-medium text-muted sm:inline">{label}</span>
        </span>
      ))}
    </div>
  );
}
