"use client";

import { useYearParam } from "@/lib/useYearParam";
import { useSchedule } from "@/lib/api";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { RaceListRow, UnderwayBadge, UpcomingBadge } from "@/components/RaceListRow";
import { formatDate } from "@/lib/format";
import { raceFinished, weekendState } from "@/lib/weekend";

export function CircuitsClient({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const { data: events, error, isLoading } = useSchedule(year);
  const races = events?.filter((e) => e.round > 0) ?? [];

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Circuits</h1>
        <SeasonPicker year={year} onChange={setYear} />
      </div>

      {isLoading && <LoadingState label="Loading circuits…" />}
      {error && <ErrorState message="Couldn't load circuits." />}
      {events && races.length === 0 && <EmptyState message="No circuits found for this season." />}

      {races.length > 0 && (
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {races.map((race) => {
            // Unlike the schedule, a weekend under way stays inert here: the
            // circuit map is traced from the race session's telemetry, so
            // there's nothing to draw until the grand prix has been run.
            const done = raceFinished(race);
            return (
              <li key={race.round}>
                <RaceListRow href={`/circuits/${year}/${race.round}`} upcoming={!done}>
                  <span className="tabular w-8 shrink-0 text-sm text-muted">R{race.round}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{race.name}</p>
                    <p className="truncate text-sm text-muted">
                      {race.location}, {race.country}
                    </p>
                  </div>
                  {!done && (
                    <>
                      <span className="tabular shrink-0 text-sm text-muted">{formatDate(race.date)}</span>
                      {weekendState(race) === "live" ? <UnderwayBadge /> : <UpcomingBadge />}
                    </>
                  )}
                </RaceListRow>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
