"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useYearParam } from "@/lib/useYearParam";
import { useDrivers } from "@/lib/api";
import { useFavorites } from "@/components/FavoritesProvider";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, EmptyState, TeamColorDot, StarButton } from "@/components/StateViews";
import { DriverAvatar } from "@/components/DriverAvatar";
import { TeamLogo } from "@/components/TeamLogo";

export function DriversClient({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const { data, error, isLoading } = useDrivers(year);
  const { isFavoriteDriver, toggleFavoriteDriver } = useFavorites();

  const sorted = useMemo(() => {
    if (!data) return [];
    return [...data].sort((a, b) => {
      const favA = isFavoriteDriver(a.driverId) ? 0 : 1;
      const favB = isFavoriteDriver(b.driverId) ? 0 : 1;
      if (favA !== favB) return favA - favB;
      return (a.position ?? 999) - (b.position ?? 999);
    });
  }, [data, isFavoriteDriver]);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Drivers</h1>
        <div className="flex items-center gap-3">
          <Link href={`/drivers/compare?year=${year}`} className="text-sm font-medium text-muted hover:text-foreground">
            Head-to-head →
          </Link>
          <SeasonPicker year={year} onChange={setYear} />
        </div>
      </div>

      {isLoading && <LoadingState label="Loading drivers…" />}
      {error && <ErrorState message="Couldn't load drivers." />}
      {data && data.length === 0 && <EmptyState message="No drivers found for this season." />}

      {sorted.length > 0 && (
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {sorted.map((d) => (
            <li key={d.driverId}>
              <Link
                href={`/drivers/${year}/${d.driverId}`}
                className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3 transition-colors hover:bg-surface-raised"
              >
                <DriverAvatar src={d.headshotUrl} name={`${d.givenName} ${d.familyName}`} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">
                    {d.givenName} {d.familyName}
                  </p>
                  <p className="flex items-center gap-1.5 truncate text-sm text-muted">
                    <TeamColorDot color={d.teamColor} />
                    <TeamLogo src={d.teamLogoUrl} name={d.teamName} sizeClassName="h-4 w-4" />
                    {d.teamName ?? "-"}
                  </p>
                </div>
                <span className="tabular shrink-0 text-sm text-muted">P{d.position ?? "-"}</span>
                <span className="tabular shrink-0 text-sm font-semibold">{d.points ?? 0} pts</span>
                <StarButton
                  active={isFavoriteDriver(d.driverId)}
                  onClick={() => toggleFavoriteDriver(d.driverId)}
                  label={`Favorite ${d.givenName} ${d.familyName}`}
                />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
