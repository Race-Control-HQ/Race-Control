"use client";

import Link from "next/link";
import { useYearParam } from "@/lib/useYearParam";
import { useQueryParam } from "@/lib/useQueryParam";
import { useCompare, useDrivers } from "@/lib/api";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { HeadToHeadFormLine } from "./HeadToHeadFormLine";

function StatRow({ label, a, b }: { label: string; a: number | string; b: number | string }) {
  return (
    <tr className="hover:bg-surface/60">
      <td className="tabular px-3 py-2 text-right font-semibold">{a}</td>
      <td className="px-3 py-2 text-center text-xs uppercase tracking-wide text-muted">{label}</td>
      <td className="tabular px-3 py-2 text-left font-semibold">{b}</td>
    </tr>
  );
}

export function CompareClient({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const [d1, setD1] = useQueryParam("d1", "");
  const [d2, setD2] = useQueryParam("d2", "");
  const { data: drivers } = useDrivers(year);
  const { data, error, isLoading } = useCompare(year, d1 || null, d2 || null);

  return (
    <div>
      <Link href={`/drivers?year=${year}`} className="mb-4 inline-block text-sm text-muted hover:text-foreground">
        ← Drivers
      </Link>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Head-to-Head</h1>
        <SeasonPicker year={year} onChange={setYear} />
      </div>

      <div className="mb-6 grid grid-cols-2 gap-3">
        <select
          value={d1}
          onChange={(e) => setD1(e.target.value)}
          className="rounded-md border border-border bg-surface px-3 py-2 text-sm"
        >
          <option value="">Select driver A…</option>
          {drivers?.map((d) => (
            <option key={d.driverId} value={d.driverId}>
              {d.givenName} {d.familyName}
            </option>
          ))}
        </select>
        <select
          value={d2}
          onChange={(e) => setD2(e.target.value)}
          className="rounded-md border border-border bg-surface px-3 py-2 text-sm"
        >
          <option value="">Select driver B…</option>
          {drivers?.map((d) => (
            <option key={d.driverId} value={d.driverId}>
              {d.givenName} {d.familyName}
            </option>
          ))}
        </select>
      </div>

      {!d1 || !d2 ? (
        <EmptyState message="Pick two drivers to compare." />
      ) : isLoading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState message="Couldn't load comparison." />
      ) : data ? (
        <>
          {(() => {
            const driverA = drivers?.find((d) => d.driverId === d1);
            const driverB = drivers?.find((d) => d.driverId === d2);
            return driverA && driverB ? <HeadToHeadFormLine year={year} a={driverA} b={driverB} /> : null;
          })()}
          <div className="overflow-hidden rounded-lg border border-border">
            <div className="grid grid-cols-2 gap-3 bg-surface px-3 py-3 text-center">
              <p className="truncate font-semibold">{data.drivers[0].name}</p>
              <p className="truncate font-semibold">{data.drivers[1].name}</p>
            </div>
            <table className="w-full text-sm">
              <tbody className="divide-y divide-border">
                <StatRow label="Points" a={data.drivers[0].points} b={data.drivers[1].points} />
                <StatRow label="Wins" a={data.drivers[0].wins} b={data.drivers[1].wins} />
                <StatRow label="Podiums" a={data.drivers[0].podiums} b={data.drivers[1].podiums} />
                <StatRow label="Poles" a={data.drivers[0].poles} b={data.drivers[1].poles} />
                <StatRow label="Best Finish" a={data.drivers[0].bestFinish ?? "-"} b={data.drivers[1].bestFinish ?? "-"} />
                <StatRow label="DNFs" a={data.drivers[0].dnf} b={data.drivers[1].dnf} />
                <StatRow label="Race H2H Wins" a={data.drivers[0].raceWins_h2h} b={data.drivers[1].raceWins_h2h} />
                <StatRow label="Qualifying H2H Wins" a={data.drivers[0].qualWins_h2h} b={data.drivers[1].qualWins_h2h} />
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </div>
  );
}
