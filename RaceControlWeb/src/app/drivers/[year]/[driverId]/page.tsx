import Link from "next/link";
import { callBackend, BackendError } from "@/lib/server/backend";
import type { DriverDetail } from "@/lib/types";
import { TeamColorDot } from "@/components/StateViews";
import { DriverAvatar } from "@/components/DriverAvatar";
import { TeamLogo } from "@/components/TeamLogo";
import { DriverFavoriteButton } from "./DriverFavoriteButton";
import { DriverWdcBadge } from "./DriverWdcBadge";
import { DriverPointsChart } from "./DriverPointsChart";
import { DriverFingerprint } from "./DriverFingerprint";

export default async function DriverDetailPage({
  params,
}: {
  params: Promise<{ year: string; driverId: string }>;
}) {
  const { year, driverId } = await params;
  let driver: DriverDetail;
  try {
    driver = await callBackend<DriverDetail>(`/api/drivers/${year}/${driverId}`);
  } catch (e) {
    const message = e instanceof BackendError ? e.message : "Couldn't load this driver.";
    return <div className="rounded-lg border border-border bg-surface px-4 py-6 text-sm text-muted">{message}</div>;
  }

  return (
    <div>
      <Link href={`/drivers?year=${year}`} className="mb-4 inline-block text-sm text-muted hover:text-foreground">
        ← Drivers
      </Link>

      <div className="mb-6 flex items-center gap-4 rounded-lg border border-border bg-surface p-4">
        <DriverAvatar
          src={driver.headshotUrl}
          name={`${driver.givenName} ${driver.familyName}`}
          sizeClassName="h-16 w-16"
          textClassName="text-lg"
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-bold">
              {driver.givenName} {driver.familyName}
            </h1>
            <DriverWdcBadge year={Number(year)} driverId={driver.driverId} />
          </div>
          <p className="flex items-center gap-1.5 text-sm text-muted">
            <TeamColorDot color={driver.teamColor} />
            <TeamLogo src={driver.teamLogoUrl} name={driver.teamName} sizeClassName="h-4 w-4" />
            {driver.teamName ?? "-"} · #{driver.number ?? "-"} · {driver.nationality ?? "-"}
          </p>
        </div>
        <div className="text-right">
          <p className="tabular text-2xl font-bold">{driver.points ?? 0}</p>
          <p className="text-xs uppercase tracking-wide text-muted">pts · P{driver.position ?? "-"}</p>
        </div>
        <DriverFavoriteButton driverId={driver.driverId} name={`${driver.givenName} ${driver.familyName}`} />
      </div>

      <h2 className="mb-3 text-lg font-semibold">Points Progression</h2>
      <div className="mb-6">
        <DriverPointsChart year={Number(year)} driverId={driver.driverId} teamColor={driver.teamColor} />
      </div>
      <DriverFingerprint year={Number(year)} driverId={driver.driverId} color={driver.teamColor} />

      <h2 className="mb-3 text-lg font-semibold">Season Results</h2>
      <div className="overflow-hidden rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-3 py-2 font-medium">Rnd</th>
              <th className="px-3 py-2 font-medium">Race</th>
              <th className="tabular px-3 py-2 text-right font-medium">Grid</th>
              <th className="tabular px-3 py-2 text-right font-medium">Finish</th>
              <th className="px-3 py-2 font-medium">Status</th>
              <th className="tabular px-3 py-2 text-right font-medium">Points</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {driver.seasonResults.map((r) => (
              <tr key={r.round} className="hover:bg-surface/60">
                <td className="tabular px-3 py-2 text-muted">{r.round}</td>
                <td className="px-3 py-2">
                  <Link href={`/races/${year}/${r.round}`} className="hover:text-racing-red">
                    {r.raceName}
                  </Link>
                </td>
                <td className="tabular px-3 py-2 text-right">{r.grid ?? "-"}</td>
                <td className="tabular px-3 py-2 text-right font-semibold">{r.position ?? "-"}</td>
                <td className="px-3 py-2 text-muted">{r.status ?? "-"}</td>
                <td className="tabular px-3 py-2 text-right">{r.points ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
