import type { FlagPeriodType } from "@/lib/types";

export const FLAG_TYPE_COLORS: Record<FlagPeriodType, string> = {
  YELLOW: "#f0d000",
  DOUBLE_YELLOW: "#f2994a",
  RED: "#e10600",
  SC: "#ff8c1a",
  VSC: "#9b6bff",
};

export const FLAG_TYPE_LABELS: Record<FlagPeriodType, string> = {
  YELLOW: "Yellow Flag",
  DOUBLE_YELLOW: "Double Yellow",
  RED: "Red Flag",
  SC: "Safety Car",
  VSC: "Virtual Safety Car",
};

export function flagColor(type: string | null | undefined): string {
  if (!type) return "#6b6b72";
  return FLAG_TYPE_COLORS[type as FlagPeriodType] ?? "#6b6b72";
}

export function flagLabel(type: string | null | undefined): string {
  if (!type) return "Unknown";
  return FLAG_TYPE_LABELS[type as FlagPeriodType] ?? type;
}
