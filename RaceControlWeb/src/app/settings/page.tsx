"use client";

import { useFavorites } from "@/components/FavoritesProvider";
import { StarButton } from "@/components/StateViews";

function idToLabel(id: string): string {
  return id
    .split(/[_-]/)
    .map((p) => p[0]?.toUpperCase() + p.slice(1))
    .join(" ");
}

export default function SettingsPage() {
  const { favoriteDrivers, favoriteTeams, toggleFavoriteDriver, toggleFavoriteTeam } = useFavorites();

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">Settings</h1>

      <section className="mb-8">
        <h2 className="mb-3 text-lg font-semibold">Favorite Drivers</h2>
        {favoriteDrivers.size === 0 ? (
          <p className="text-sm text-muted">No favorite drivers yet. Star a driver from the Drivers tab.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {[...favoriteDrivers].map((id) => (
              <li key={id} className="flex items-center justify-between rounded-lg border border-border bg-surface px-4 py-2.5">
                <span className="font-medium">{idToLabel(id)}</span>
                <StarButton active onClick={() => toggleFavoriteDriver(id)} label={`Remove ${idToLabel(id)} from favorites`} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="mb-8">
        <h2 className="mb-3 text-lg font-semibold">Favorite Teams</h2>
        {favoriteTeams.size === 0 ? (
          <p className="text-sm text-muted">No favorite teams yet. Star a team from the Teams tab.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {[...favoriteTeams].map((id) => (
              <li key={id} className="flex items-center justify-between rounded-lg border border-border bg-surface px-4 py-2.5">
                <span className="font-medium">{idToLabel(id)}</span>
                <StarButton active onClick={() => toggleFavoriteTeam(id)} label={`Remove ${idToLabel(id)} from favorites`} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold">About</h2>
        <div className="rounded-lg border border-border bg-surface px-4 py-3 text-sm text-muted">
          <p className="mb-1 text-foreground">RaceControl Web</p>
          <p>Historical Formula 1 schedules, results, standings, and telemetry (2018–present).</p>
          <p className="mt-2">Data powered by the FastF1 library and the Jolpica/Ergast API.</p>
        </div>
      </section>
    </div>
  );
}
