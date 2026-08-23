"use client";

import clsx from "clsx";
import { useYearParam } from "@/lib/useYearParam";
import { useSchedule } from "@/lib/api";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { RaceListRow, UnderwayBadge, UpcomingBadge } from "@/components/RaceListRow";
import { formatDate } from "@/lib/format";
import { raceFinished, weekendState } from "@/lib/weekend";

export function ScheduleClient({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const { data: events, error, isLoading } = useSchedule(year);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Race Schedule</h1>
        <SeasonPicker year={year} onChange={setYear} />
      </div>

      {isLoading && <LoadingState label="Loading schedule…" />}
      {error && <ErrorState message="Couldn't load the schedule." />}
      {events && events.length === 0 && <EmptyState message="No races scheduled for this season." />}

      {events && events.length > 0 && (
        <ol className="flex flex-col gap-2">
          {events
            .filter((e) => e.round > 0)
            .map((event) => {
              // A weekend already under way stays linkable: its qualifying (and
              // on a sprint weekend, its sprint) has run, so the race page has
              // something to show even though the grand prix itself hasn't.
              const state = weekendState(event);
              return (
                <li key={event.round}>
                  <RaceListRow href={`/races/${year}/${event.round}`} upcoming={state === "upcoming"}>
                    <span className="tabular w-8 shrink-0 text-sm text-muted">R{event.round}</span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium">{event.name}</p>
                      <p className="truncate text-sm text-muted">
                        {event.location}, {event.country}
                      </p>
                    </div>
                    <span
                      className={clsx(
                        "tabular shrink-0 text-sm",
                        raceFinished(event) ? "text-muted" : "font-medium text-foreground",
                      )}
                    >
                      {formatDate(event.date)}
                    </span>
                    {state === "live" && <UnderwayBadge />}
                    {state === "upcoming" && <UpcomingBadge />}
                  </RaceListRow>
                </li>
              );
            })}
        </ol>
      )}
    </div>
  );
}
