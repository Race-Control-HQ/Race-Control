"use client";

import { useMemo } from "react";
import Link from "next/link";
import { useYearParam } from "@/lib/useYearParam";
import { useTeams } from "@/lib/api";
import { useFavorites } from "@/components/FavoritesProvider";
import { SeasonPicker } from "@/components/SeasonPicker";
import { LoadingState, ErrorState, EmptyState, TeamColorDot, StarButton } from "@/components/StateViews";
import { TeamLogo } from "@/components/TeamLogo";

export function TeamsClient({ defaultYear }: { defaultYear: number }) {
  const [year, setYear] = useYearParam(defaultYear);
  const { data, error, isLoading } = useTeams(year);
  const { isFavoriteTeam, toggleFavoriteTeam } = useFavorites();

  const sorted = useMemo(() => {
    if (!data) return [];
    return [...data].sort((a, b) => {
      const favA = isFavoriteTeam(a.teamId) ? 0 : 1;
      const favB = isFavoriteTeam(b.teamId) ? 0 : 1;
      if (favA !== favB) return favA - favB;
      return (a.position ?? 999) - (b.position ?? 999);
    });
  }, [data, isFavoriteTeam]);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Teams</h1>
        <SeasonPicker year={year} onChange={setYear} />
      </div>

      {isLoading && <LoadingState label="Loading teams…" />}
      {error && <ErrorState message="Couldn't load teams." />}
      {data && data.length === 0 && <EmptyState message="No teams found for this season." />}

      {sorted.length > 0 && (
        <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {sorted.map((t) => (
            <li key={t.teamId}>
              <Link
                href={`/teams/${year}/${t.teamId}`}
                className="flex items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3 transition-colors hover:bg-surface-raised"
                style={{ borderLeft: `3px solid ${t.teamColor || "var(--border)"}` }}
              >
                <TeamColorDot color={t.teamColor} />
                <TeamLogo src={t.teamLogoUrl} name={t.teamName} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{t.teamName}</p>
                  <p className="truncate text-sm text-muted">{t.drivers.map((d) => d.code ?? d.name).join(" · ")}</p>
                </div>
                <span className="tabular shrink-0 text-sm text-muted">P{t.position ?? "-"}</span>
                <span className="tabular shrink-0 text-sm font-semibold">{t.points ?? 0} pts</span>
                <StarButton
                  active={isFavoriteTeam(t.teamId)}
                  onClick={() => toggleFavoriteTeam(t.teamId)}
                  label={`Favorite ${t.teamName}`}
                />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
