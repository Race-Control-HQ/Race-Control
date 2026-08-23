"use client";

import { createContext, useContext, useMemo } from "react";
import { useLocalStorageSet } from "@/lib/localStorageSet";

const DRIVERS_KEY = "racecontrol.favorite_drivers";
const TEAMS_KEY = "racecontrol.favorite_teams";

interface FavoritesContextValue {
  favoriteDrivers: Set<string>;
  favoriteTeams: Set<string>;
  isFavoriteDriver: (id: string) => boolean;
  isFavoriteTeam: (id: string) => boolean;
  toggleFavoriteDriver: (id: string) => void;
  toggleFavoriteTeam: (id: string) => void;
}

const FavoritesContext = createContext<FavoritesContextValue | null>(null);

function toggle(prev: Set<string>, id: string): Set<string> {
  const next = new Set(prev);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

export function FavoritesProvider({ children }: { children: React.ReactNode }) {
  const [favoriteDrivers, setFavoriteDrivers] = useLocalStorageSet(DRIVERS_KEY);
  const [favoriteTeams, setFavoriteTeams] = useLocalStorageSet(TEAMS_KEY);

  const value = useMemo<FavoritesContextValue>(
    () => ({
      favoriteDrivers,
      favoriteTeams,
      isFavoriteDriver: (id) => favoriteDrivers.has(id),
      isFavoriteTeam: (id) => favoriteTeams.has(id),
      toggleFavoriteDriver: (id) => setFavoriteDrivers((prev) => toggle(prev, id)),
      toggleFavoriteTeam: (id) => setFavoriteTeams((prev) => toggle(prev, id)),
    }),
    [favoriteDrivers, favoriteTeams, setFavoriteDrivers, setFavoriteTeams],
  );

  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites(): FavoritesContextValue {
  const ctx = useContext(FavoritesContext);
  if (!ctx) throw new Error("useFavorites must be used within FavoritesProvider");
  return ctx;
}
