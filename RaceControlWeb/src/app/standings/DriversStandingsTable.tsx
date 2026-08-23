"use client";

import Link from "next/link";
import { useDriverStandings } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";

export function DriversStandingsTable({ year }: { year: number }) {
  const { data, error, isLoading } = useDriverStandings(year);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load driver standings." />;
  if (!data || data.length === 0) return <EmptyState message="No standings available for this season." />;

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead className="bg-surface text-left text-xs uppercase tracking-wide text-muted">
          <tr>
            <th className="px-3 py-2 font-medium">Pos</th>
            <th className="px-3 py-2 font-medium">Driver</th>
            <th className="px-3 py-2 font-medium">Team</th>
            <th className="px-3 py-2 text-right font-medium">Wins</th>
            <th className="px-3 py-2 text-right font-medium">Points</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {data.map((d) => (
            <tr key={d.driverId} className="hover:bg-surface/60">
              <td className="tabular px-3 py-2 text-muted">{d.position ?? "-"}</td>
              <td className="px-3 py-2 font-medium">
                <Link href={`/drivers/${year}/${d.driverId}`} className="hover:text-racing-red">
                  {d.givenName} {d.familyName}
                  {d.driverCode ? <span className="ml-1 text-muted">({d.driverCode})</span> : null}
                </Link>
              </td>
              <td className="px-3 py-2 text-muted">
                <span className="inline-flex items-center gap-2">
                  <TeamLogo src={d.teamLogoUrl} name={d.teamName} />
                  {d.teamName ?? "-"}
                </span>
              </td>
              <td className="tabular px-3 py-2 text-right">{d.wins ?? 0}</td>
              <td className="tabular px-3 py-2 text-right font-semibold">{d.points ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
