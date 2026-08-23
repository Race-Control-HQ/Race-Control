import Link from "next/link";
import { callBackend, BackendError } from "@/lib/server/backend";
import type { Team } from "@/lib/types";
import { TeamColorDot } from "@/components/StateViews";
import { DriverAvatar } from "@/components/DriverAvatar";
import { TeamLogo } from "@/components/TeamLogo";
import { TeamFavoriteButton } from "./TeamFavoriteButton";

export default async function TeamDetailPage({
  params,
}: {
  params: Promise<{ year: string; teamId: string }>;
}) {
  const { year, teamId } = await params;
  let team: Team;
  try {
    team = await callBackend<Team>(`/api/teams/${year}/${teamId}`);
  } catch (e) {
    const message = e instanceof BackendError ? e.message : "Couldn't load this team.";
    return <div className="rounded-lg border border-border bg-surface px-4 py-6 text-sm text-muted">{message}</div>;
  }

  return (
    <div>
      <Link href={`/teams?year=${year}`} className="mb-4 inline-block text-sm text-muted hover:text-foreground">
        ← Teams
      </Link>

      <div
        className="mb-6 flex items-center gap-4 rounded-lg border border-border bg-surface p-4"
        style={{ borderLeft: `4px solid ${team.teamColor || "var(--border)"}` }}
      >
        <TeamColorDot color={team.teamColor} />
        <TeamLogo src={team.teamLogoUrl} name={team.teamName} sizeClassName="h-10 w-10" />
        <div className="min-w-0 flex-1">
          <h1 className="text-xl font-bold">{team.teamName}</h1>
          <p className="text-sm text-muted">{team.nationality ?? "-"}</p>
        </div>
        <div className="text-right">
          <p className="tabular text-2xl font-bold">{team.points ?? 0}</p>
          <p className="text-xs uppercase tracking-wide text-muted">pts · P{team.position ?? "-"} · {team.wins ?? 0} wins</p>
        </div>
        <TeamFavoriteButton teamId={team.teamId} name={team.teamName ?? teamId} />
      </div>

      <h2 className="mb-3 text-lg font-semibold">Drivers</h2>

      {/* Who's actually carried the team, at a glance: a driver list alone
          doesn't show whether points are split evenly or one driver is doing
          most of the scoring. */}
      <DriverPointsBreakdown drivers={team.drivers} teamColor={team.teamColor} />

      <ul className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
        {team.drivers.map((d) => (
          <li key={d.driverId}>
            <Link
              href={`/drivers/${year}/${d.driverId}`}
              className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3 transition-colors hover:bg-surface-raised"
            >
              <DriverAvatar src={d.headshotUrl} name={d.name} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{d.name}</p>
                <p className="text-sm text-muted">
                  {d.code ?? "-"} · #{d.number ?? "-"}
                </p>
              </div>
              <span className="tabular shrink-0 text-sm font-semibold">{d.points ?? 0} pts</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

function DriverPointsBreakdown({
  drivers,
  teamColor,
}: {
  drivers: Team["drivers"];
  teamColor: string | null;
}) {
  const total = drivers.reduce((sum, d) => sum + (d.points ?? 0), 0);
  if (total <= 0) return null;

  const base = teamColor || "var(--racing-red)";

  return (
    <div className="rounded-lg border border-border bg-surface p-4">
      <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted">Points breakdown</p>
      <div className="flex h-3 overflow-hidden rounded-full bg-surface-raised">
        {drivers.map((d, i) => {
          const pts = d.points ?? 0;
          if (pts <= 0) return null;
          const pct = (pts / total) * 100;
          return (
            <div
              key={d.driverId}
              className="h-full"
              style={{ width: `${pct}%`, backgroundColor: base, opacity: i === 0 ? 1 : 0.55 }}
              title={`${d.name}: ${pts} pts (${pct.toFixed(0)}%)`}
            />
          );
        })}
      </div>
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
        {drivers.map((d) => {
          const pts = d.points ?? 0;
          const pct = total > 0 ? (pts / total) * 100 : 0;
          return (
            <span key={d.driverId} className="tabular">
              {d.code ?? d.name}: <span className="font-semibold text-foreground">{pts}</span> ({pct.toFixed(0)}%)
            </span>
          );
        })}
      </div>
    </div>
  );
}
