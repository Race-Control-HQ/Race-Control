export function formatMs(ms: number | null | undefined): string {
  if (ms == null) return "-";
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  const millis = Math.round(ms % 1000);
  return `${minutes}:${String(seconds).padStart(2, "0")}.${String(millis).padStart(3, "0")}`;
}

/**
 * A signed time delta in seconds, e.g. "+11.330" / "-6.663".
 *
 * Distinct from `formatMs`: a race-trace value is a *difference*, so the sign
 * carries the meaning and minutes are folded into seconds (the same convention
 * F1 timing uses for gaps over a minute).
 */
export function formatDeltaMs(ms: number | null | undefined): string {
  if (ms == null) return "-";
  const sign = ms < 0 ? "-" : "+";
  const abs = Math.abs(ms);
  const seconds = Math.floor(abs / 1000);
  const millis = Math.round(abs % 1000);
  return `${sign}${seconds}.${String(millis).padStart(3, "0")}`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "TBC";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "TBC";
  return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "TBC";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "TBC";
  return d.toLocaleString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * Formats a UTC timestamp as a clock time in the viewer's local timezone,
 * with the offset appended (e.g. "1:20:00 PM GMT+1") so it's never ambiguous
 * which zone is being shown: race-control timestamps come from the timing
 * feed in UTC, but a race can be happening in any timezone, and different
 * viewers are in different timezones themselves.
 */
export function formatClock(iso: string | null | undefined): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  return d.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZoneName: "shortOffset",
  });
}

/**
 * Compact date+time for a session in a weekend schedule list (e.g. "Fri 1:30 PM GMT+1"),
 * shown in the viewer's local timezone with the offset made explicit; see `formatClock`.
 */
export function formatSessionTime(iso: string | null | undefined): string {
  if (!iso) return "TBC";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "TBC";
  return d.toLocaleString(undefined, {
    weekday: "short",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "shortOffset",
  });
}

export function ordinal(n: number | null | undefined): string {
  if (n == null) return "-";
  const s = ["th", "st", "nd", "rd"];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
}

export function driverInitials(fullName: string | null | undefined): string {
  if (!fullName) return "??";
  return fullName
    .split(" ")
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}
