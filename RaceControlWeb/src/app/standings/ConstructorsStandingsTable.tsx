"use client";

import Link from "next/link";
import { useConstructorStandings } from "@/lib/api";
import { LoadingState, ErrorState, EmptyState } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";

export function ConstructorsStandingsTable({ year }: { year: number }) {
  const { data, error, isLoading } = useConstructorStandings(year);

  if (isLoading) return <LoadingState />;
  if (error) return <ErrorState message="Couldn't load constructor standings." />;
  if (!data || data.length === 0) return <EmptyState message="No standings available for this season." />;

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead className="bg-surface text-left text-xs uppercase tracking-wide text-muted">
          <tr>
            <th className="px-3 py-2 font-medium">Pos</th>
            <th className="px-3 py-2 font-medium">Team</th>
            <th className="px-3 py-2 text-right font-medium">Wins</th>
            <th className="px-3 py-2 text-right font-medium">Points</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {data.map((t) => (
            <tr key={t.teamId} className="hover:bg-surface/60">
              <td className="tabular px-3 py-2 text-muted">{t.position ?? "-"}</td>
              <td className="px-3 py-2 font-medium">
                <Link href={`/teams/${year}/${t.teamId}`} className="inline-flex items-center gap-2 hover:text-racing-red">
                  <TeamLogo src={t.teamLogoUrl} name={t.teamName} />
                  {t.teamName}
                </Link>
              </td>
              <td className="tabular px-3 py-2 text-right">{t.wins ?? 0}</td>
              <td className="tabular px-3 py-2 text-right font-semibold">{t.points ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
