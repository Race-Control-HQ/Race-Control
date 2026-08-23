export const COMPOUND_COLORS: Record<string, string> = {
  SOFT: "#e10600",
  MEDIUM: "#f0d000",
  HARD: "#f2f2f4",
  INTERMEDIATE: "#3ba13b",
  WET: "#2b6fd1",
  UNKNOWN: "#6b6b72",
};

export function compoundColor(compound: string | null | undefined): string {
  if (!compound) return COMPOUND_COLORS.UNKNOWN;
  return COMPOUND_COLORS[compound.toUpperCase()] ?? COMPOUND_COLORS.UNKNOWN;
}
