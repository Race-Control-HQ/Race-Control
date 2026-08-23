import type { EventSession, ScheduleEvent } from "@/lib/types";

/**
 * Assumed length of a session, used to work out whether it has finished.
 *
 * The schedule feed only carries start times, so an end time has to be
 * inferred. These are deliberately generous: a session that overruns (a
 * delayed start, a long red flag) should still read as running rather than
 * flip to "done" while the cars are on track. A race gets F1's three-hour
 * maximum window; everything else gets ninety minutes.
 */
function sessionDurationMs(identifier: string): number {
  return identifier === "R" ? 3 * 60 * 60 * 1000 : 90 * 60 * 1000;
}

export type SessionState = "upcoming" | "live" | "done";

export function sessionState(session: EventSession, now: Date = new Date()): SessionState {
  const start = session.date ? new Date(session.date).getTime() : NaN;
  if (Number.isNaN(start)) return "upcoming";
  if (now.getTime() < start) return "upcoming";
  return now.getTime() < start + sessionDurationMs(session.identifier) ? "live" : "done";
}

/**
 * Whether the grand prix itself has been run.
 *
 * Deliberately not `event.completed`: the backend derives that flag from
 * `EventDate`, which is midnight UTC on race day. So it stays false for the
 * whole of a weekend that is already under way — practice and qualifying
 * done, race still to come — and then turns true from midnight on Sunday,
 * hours *before* the race is run. The session times say what actually
 * happened, so use those and keep `completed` only as a fallback for events
 * whose sessions carry no dates.
 */
export function raceFinished(event: ScheduleEvent, now: Date = new Date()): boolean {
  const race = event.sessions.find((s) => s.identifier === "R" && s.date);
  if (!race) return event.completed;
  return sessionState(race, now) === "done";
}

/** The start of the first session of the weekend, in ms, or null if unknown. */
function weekendStart(event: ScheduleEvent): number | null {
  const starts = event.sessions
    .map((s) => (s.date ? new Date(s.date).getTime() : NaN))
    .filter((t) => !Number.isNaN(t));
  return starts.length > 0 ? Math.min(...starts) : null;
}

export type WeekendState = "upcoming" | "live" | "finished";

/** Where a race weekend sits relative to `now`; see `raceFinished`. */
export function weekendState(event: ScheduleEvent, now: Date = new Date()): WeekendState {
  if (raceFinished(event, now)) return "finished";
  const start = weekendStart(event);
  return start != null && now.getTime() >= start ? "live" : "upcoming";
}
