import Link from "next/link";
import { callBackend, BackendError } from "@/lib/server/backend";
import type { ScheduleEvent, SessionResults } from "@/lib/types";
import { RaceDetailClient } from "./RaceDetailClient";

export default async function RaceDetailPage({
  params,
}: {
  params: Promise<{ year: string; round: string }>;
}) {
  const { year, round } = await params;
  const y = parseInt(year, 10);
  const r = parseInt(round, 10);

  // The schedule, not the race classification, is what the page is built on:
  // it names the event and lists the weekend's sessions, both of which exist
  // from the moment the calendar is published.
  let schedule: ScheduleEvent[];
  try {
    schedule = await callBackend<ScheduleEvent[]>(`/api/schedule/${y}`);
  } catch (e) {
    const message = e instanceof BackendError ? e.message : "Couldn't load this race.";
    return <div className="rounded-lg border border-border bg-surface px-4 py-6 text-sm text-muted">{message}</div>;
  }

  const event = schedule.find((e) => e.round === r);
  if (!event) {
    return (
      <div className="rounded-lg border border-border bg-surface px-4 py-6 text-sm text-muted">
        There&apos;s no round {r} in the {y} season.
      </div>
    );
  }

  // Mid-weekend there is no race classification yet, and there won't be one
  // for a while after the flag either. That's not a page-level failure: the
  // session picker still has practice and qualifying to show, so fall back
  // to loading the race on the client alongside every other session.
  let raceResults: SessionResults | null = null;
  try {
    raceResults = await callBackend<SessionResults>(`/api/results/${y}/${r}/R`);
  } catch {
    raceResults = null;
  }

  return (
    <div>
      <Link href={`/schedule?year=${y}`} className="mb-4 inline-block text-sm text-muted hover:text-foreground">
        ← Schedule
      </Link>
      <h1 className="mb-6 text-2xl font-bold tracking-tight">{raceResults?.eventName ?? event.name}</h1>
      <RaceDetailClient year={y} round={r} sessions={event.sessions} initialResults={raceResults} />
    </div>
  );
}
