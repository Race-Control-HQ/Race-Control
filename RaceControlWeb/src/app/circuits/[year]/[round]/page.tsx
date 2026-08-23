import Link from "next/link";
import { callBackend, BackendError } from "@/lib/server/backend";
import type { CircuitMap } from "@/lib/types";
import { TeamColorDot } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";
import { TrackMap } from "./TrackMap";
import { ElevationProfile } from "./ElevationProfile";
import { CornerSpeedProfile } from "./CornerSpeedProfile";

export default async function CircuitDetailPage({
  params,
}: {
  params: Promise<{ year: string; round: string }>;
}) {
  const { year, round } = await params;
  let map: CircuitMap;
  try {
    map = await callBackend<CircuitMap>(`/api/circuit/${year}/${round}`);
  } catch (e) {
    const message = e instanceof BackendError ? e.message : "Couldn't load this circuit.";
    return <div className="rounded-lg border border-border bg-surface px-4 py-6 text-sm text-muted">{message}</div>;
  }

  return (
    <div>
      <Link href={`/circuits?year=${year}`} className="mb-4 inline-block text-sm text-muted hover:text-foreground">
        ← Circuits
      </Link>
      <h1 className="text-2xl font-bold tracking-tight">{map.eventName}</h1>
      <p className="mb-6 text-sm text-muted">
        {map.location}, {map.country}
        {map.lengthMeters ? ` · ${(map.lengthMeters / 1000).toFixed(3)} km` : ""}
      </p>

      <TrackMap map={map} />

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ElevationProfile map={map} />
        {map.fastestLap && (
          <div className="flex flex-col justify-center gap-2 rounded-lg border border-border bg-surface p-4">
            <p className="text-xs font-medium uppercase tracking-wide text-muted">Fastest Lap</p>
            <div className="flex items-center gap-2">
              <TeamColorDot color={map.fastestLap.teamColor} />
              <TeamLogo src={map.fastestLap.teamLogoUrl} name={map.fastestLap.team} sizeClassName="h-5 w-5" />
              <span className="font-semibold">{map.fastestLap.driverName ?? map.fastestLap.driver}</span>
              <span className="text-sm text-muted">({map.fastestLap.team})</span>
            </div>
            <p className="tabular text-2xl font-bold">{map.fastestLap.time}</p>
            <p className="text-sm text-muted">{map.fastestLap.compound}</p>
          </div>
        )}
      </div>

      <div className="mt-4">
        <CornerSpeedProfile map={map} />
      </div>
    </div>
  );
}
