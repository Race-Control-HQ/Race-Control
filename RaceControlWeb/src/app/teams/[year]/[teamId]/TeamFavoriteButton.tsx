"use client";

import { useFavorites } from "@/components/FavoritesProvider";
import { StarButton } from "@/components/StateViews";

export function TeamFavoriteButton({ teamId, name }: { teamId: string; name: string }) {
  const { isFavoriteTeam, toggleFavoriteTeam } = useFavorites();
  return <StarButton active={isFavoriteTeam(teamId)} onClick={() => toggleFavoriteTeam(teamId)} label={`Favorite ${name}`} />;
}
